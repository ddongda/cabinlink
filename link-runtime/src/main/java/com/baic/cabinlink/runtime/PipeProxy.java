package com.baic.cabinlink.runtime;

import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.baic.cabinlink.pipe.ICapabilityPipe;
import com.baic.cabinlink.pipe.IPipeCallback;
import com.baic.cabinlink.pipe.IPipeReply;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消费方代理基类：契约层 XxxProxy 继承本类。
 * 托管：Call 超时裁决（消费端裁决，不依赖提供方自觉）、回调切主线程、
 *      订阅聚合、属性镜像、崩溃后 reattach 自动重订阅 + 快照刷新（V2 增强重放）。
 */
public abstract class PipeProxy {

    private static final String TAG = "CabinLink.Proxy";
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;

    private static final Handler sMain = new Handler(Looper.getMainLooper());
    private static final ScheduledExecutorService sTimeouts =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "link-timeout"); t.setDaemon(true); return t;
            });

    private volatile ICapabilityPipe mPipe;          // null = DETACHED
    private final PropertyMirror mMirror = new PropertyMirror();
    /** 累计订阅过的主题（reattach 重放依据） */
    private final ConcurrentHashMap<Integer, Boolean> mTopics = new ConcurrentHashMap<>();
    /**
     * P1 修复：记录「已成功提交订阅的 pipe Binder」与「提交时的订阅集指纹」。
     * 重连/重复 flush 时，若仍是同一 pipe 且订阅集未变，则跳过重复 subscribe IPC。
     * pipe 换新（reattach）或订阅集新增主题时指纹/引用变化，才真正提交。
     */
    private volatile IBinder mSubmittedPipe;
    private volatile java.util.Set<Integer> mSubmittedTopics;

    protected final PropertyMirror mirror() { return mMirror; }

    // ── Call ────────────────────────────────────────────────
    protected final void call(int op, Bundle args, Reply<Bundle> reply) {
        call(op, args, DEFAULT_TIMEOUT_MS, reply);
    }

    protected final void call(int op, Bundle args, long timeoutMs, Reply<Bundle> reply) {
        ICapabilityPipe pipe = mPipe;
        if (pipe == null) { post(reply, LinkResult.fail(Pipe.E_DETACHED, "provider offline")); return; }

        AtomicBoolean done = new AtomicBoolean(false);   // 超时/回执 一次性裁决（教训#3）
        IPipeReply.Stub sink = new IPipeReply.Stub() {
            @Override public void onResult(int code, Bundle data) {
                if (!done.compareAndSet(false, true)) return;
                post(reply, code == Pipe.OK ? LinkResult.ok(data)
                        : LinkResult.fail(code, data != null ? data.getString(Pipe.K_MESSAGE) : null));
            }
        };
        sTimeouts.schedule(() -> {
            if (done.compareAndSet(false, true))
                post(reply, LinkResult.fail(Pipe.E_TIMEOUT, "op=" + op + " >" + timeoutMs + "ms"));
        }, timeoutMs, TimeUnit.MILLISECONDS);

        try { pipe.invoke(op, args, sink); }
        catch (RemoteException e) {
            if (done.compareAndSet(false, true))
                post(reply, LinkResult.fail(Pipe.E_DETACHED, e.getMessage()));
        }
    }

    // ── 订阅（Event 与 Property 共用；幂等聚合） ─────────────
    protected final void subscribe(int... topics) {
        for (int t : topics) mTopics.put(t, Boolean.TRUE);
        flushSubscriptions();
    }

    private final IPipeCallback.Stub mCallback = new IPipeCallback.Stub() {
        @Override public void onTopic(int topic, Bundle data) {
            sMain.post(() -> {
                if (Pipe.isPropTopic(topic)) {
                    Object v = data != null ? data.get(Pipe.K_VALUE) : null;
                    if (v != null) mMirror.update(Pipe.propIdOf(topic), v);
                } else {
                    onEvent(topic, data != null ? data : new Bundle());
                }
            });
        }
    };

    /** 契约层 Proxy 覆写：分发业务事件（已在主线程） */
    protected void onEvent(int topic, Bundle data) {}

    private final Object mFlushLock = new Object();

    private void flushSubscriptions() {
        synchronized (mFlushLock) {
            ICapabilityPipe pipe = mPipe;
            if (pipe == null || mTopics.isEmpty()) return;
            IBinder binder = pipe.asBinder();
            java.util.Set<Integer> current = new java.util.HashSet<>(mTopics.keySet());
            // P1 修复：同一 pipe 且订阅集未变 → 重复提交是冗余 IPC，跳过。
            if (binder == mSubmittedPipe && current.equals(mSubmittedTopics)) return;

            int[] arr = new int[current.size()];
            int i = 0; for (Integer t : current) arr[i++] = t;
            try {
                pipe.subscribe(arr, mCallback);
                mSubmittedPipe = binder;          // 记录已提交状态，供下次校验去重
                mSubmittedTopics = current;
            } catch (RemoteException e) {
                mSubmittedPipe = null;            // 失败不记录，允许下次重试
                Log.w(TAG, "subscribe failed", e);
            }
        }
    }

    // ── 生命周期（由 CabinLink 驱动） ─────────────────────────
    final void attach(ICapabilityPipe pipe) {
        mPipe = pipe;
        try {   // 提供方崩溃 → 镜像置 stale；CabinLink 的 watcher 负责 reattach
            pipe.asBinder().linkToDeath(() -> { mPipe = null; mMirror.markStale(); }, 0);
        } catch (RemoteException e) { mPipe = null; return; }
        flushSubscriptions();    // ★ V2 增强重放：重订阅（快照随订阅自动补达，镜像随之刷新）
    }

    public final boolean isAttached() { return mPipe != null; }

    private static <T> void post(Reply<T> r, LinkResult<T> v) {
        if (r != null) sMain.post(() -> r.onResult(v));
    }
}
