package com.baic.cabinlink.runtime;

import android.os.Bundle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 消费端属性镜像：读 0 IPC；提供方崩溃时标记 stale（读旧值 + isStale 可查）。
 * 变更监听回调由 PipeProxy 切到主线程后再进入本类分发。
 */
public final class PropertyMirror {

    public interface Watcher { void onChanged(int propId, Object value); }

    private final ConcurrentHashMap<Integer, Object> values = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Watcher> watchers = new CopyOnWriteArrayList<>();
    private volatile boolean stale = true;   // 首次快照到达前视为 stale

    public boolean isStale() { return stale; }

    public boolean getBool(int id, boolean dflt) { Object v = values.get(id); return v instanceof Boolean ? (Boolean) v : dflt; }
    public int     getInt(int id, int dflt)      { Object v = values.get(id); return v instanceof Integer ? (Integer) v : dflt; }
    public float   getFloat(int id, float dflt)  { Object v = values.get(id); return v instanceof Float   ? (Float)   v : dflt; }
    public String  getString(int id, String dflt){ Object v = values.get(id); return v instanceof String  ? (String)  v : dflt; }

    public void watch(Watcher w) { watchers.add(w); }

    /**
     * P1 修复：快照批量应用与单条增量推送可能并发（增量走主线程，markStale 走 Binder
     * deathRecipient 线程；重连快照与残留增量也可能交错），旧实现 put 与 equals 判断之间
     * 非原子，会读到不一致或旧增量覆盖新快照。
     * 用本锁串行化所有写入路径，保证「一次快照整体可见」且 stale 翻转与值写入原子。
     */
    private final Object mLock = new Object();

    // ── 由 PipeProxy 调用（已在主线程） ──
    void update(int propId, Object value) {
        synchronized (mLock) {
            stale = false;
            Object old = values.put(propId, value);
            if (value.equals(old)) return;
        }
        // 回调在锁外执行，避免 watcher 重入回到本类造成死锁/持锁过久
        for (Watcher w : watchers) w.onChanged(propId, value);
    }
    void updateFromSnapshot(Bundle snap) {
        synchronized (mLock) {              // 整批快照原子可见，期间不被增量穿插
            stale = false;
            for (String k : snap.keySet()) {
                Object v = snap.get(k);
                if (v == null) continue;
                int id = Integer.parseInt(k);
                Object old = values.put(id, v);
                if (!v.equals(old)) {
                    for (Watcher w : watchers) w.onChanged(id, v);
                }
            }
        }
    }
    void markStale() { synchronized (mLock) { stale = true; } }
}
