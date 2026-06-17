package com.baic.cabinlink.runtime;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.baic.cabinlink.pipe.ICapabilityPipe;
import com.baic.cabinlink.pipe.IPipeCallback;
import com.baic.cabinlink.pipe.IPipeReply;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 提供方骨架基类：业务团队不直接用本类，用各契约生成/手写的 XxxSkeleton。
 *
 * 本类托管全部 IPC 样板（一期手写 60 行/域的部分）：
 *  · 业务方法默认在能力级串行 executor 执行（不变量#2，状态机免锁）
 *  · Property 存储：set() 即自动推送给订阅者；订阅含属性主题时立即补快照（不变量#3）
 *  · Event 推送：RemoteCallbackList 托管，死亡自动剔除
 *  · ping/describe/snapshot 统一实现
 */
public abstract class CapabilitySkeleton {

    private static final String TAG = "CabinLink.Skeleton";

    public abstract String capabilityId();
    public abstract int version();

    /** 契约层（手写/生成 Skeleton）实现：按 op 解包并调业务方法 */
    protected abstract void onInvoke(int opCode, Bundle args, ReplySink reply);

    // ── 回执（带一次性守卫，教训#3） ─────────────────────────
    public static final class ReplySink {
        private final IPipeReply target;
        private final java.util.concurrent.atomic.AtomicBoolean done =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        ReplySink(IPipeReply t) { target = t; }
        public void ok()                    { send(Pipe.OK, null); }
        public void ok(Bundle data)         { send(Pipe.OK, data); }
        public void fail(int code, String m){
            Bundle b = new Bundle(); b.putString(Pipe.K_MESSAGE, m); send(code, b);
        }
        private void send(int code, Bundle data) {
            if (!done.compareAndSet(false, true)) return;
            if (target == null) return;
            try { target.onResult(code, data); } catch (RemoteException ignored) {}
        }
    }

    /**
     * P1 修复：set()→pushProperty（业务任意线程）与 subscribe() 注册+补快照存在竞态：
     * 订阅者注册完成、补快照读取之间若发生 set，可能丢失该次推送或快照与增量乱序。
     * 用本锁串行化「订阅快照补达」与「属性写入+推送」，保证订阅者拿到的快照与后续增量首尾衔接。
     */
    private final Object mPropLock = new Object();

    // ── Property 存储（set 即推送） ──────────────────────────
    public final class Properties {
        private final ConcurrentHashMap<Integer, Object> values = new ConcurrentHashMap<>();
        public void set(int propId, Object value) {
            synchronized (mPropLock) {
                Object old = values.put(propId, value);
                if (!value.equals(old)) pushProperty(propId, value);
            }
        }
        public Object get(int propId) { return values.get(propId); }
        Bundle snapshotOf(int[] ids) {
            Bundle b = new Bundle();
            for (int id : ids) put(b, String.valueOf(id), values.get(id));
            return b;
        }
        Map<Integer, Object> all() { return values; }
    }

    private final Properties mProps = new Properties();
    protected final Properties properties() { return mProps; }

    /** Event 推送（业务在任意线程可调） */
    protected final void emit(int topic, Bundle data) { push(topic, data); }

    // ── 串行业务 executor（不变量#2） ────────────────────────
    private final ExecutorService mSerial = Executors.newSingleThreadExecutor(
            r -> { Thread t = new Thread(r, "cap-" + capabilityId()); t.setDaemon(true); return t; });

    // ── 订阅者管理 ───────────────────────────────────────────
    private final RemoteCallbackList<IPipeCallback> mSubs = new RemoteCallbackList<>();
    /** callback.asBinder → 订阅的 topics（用于按需过滤推送） */
    private final ConcurrentHashMap<IBinder, int[]> mTopicsOf = new ConcurrentHashMap<>();

    private void pushProperty(int propId, Object value) {
        Bundle b = new Bundle(); put(b, Pipe.K_VALUE, value);
        push(Pipe.propTopic(propId), b);
    }

    private void push(int topic, Bundle data) {
        int n = mSubs.beginBroadcast();
        for (int i = 0; i < n; i++) {
            IPipeCallback cb = mSubs.getBroadcastItem(i);
            int[] topics = mTopicsOf.get(cb.asBinder());
            if (!contains(topics, topic)) continue;
            try { cb.onTopic(topic, data); }            // 逐个 try-catch（教训：单死 Binder 不阻断）
            catch (RemoteException e) { Log.w(TAG, "push dead subscriber", e); }
        }
        mSubs.finishBroadcast();
    }

    // ── 对外 Binder（统一传输接口实现） ───────────────────────
    private final ICapabilityPipe.Stub mPipe = new ICapabilityPipe.Stub() {

        @Override public void invoke(int opCode, Bundle args, IPipeReply reply) {
            ReplySink sink = new ReplySink(reply);
            mSerial.execute(() -> {
                try { onInvoke(opCode, args != null ? args : new Bundle(), sink); }
                catch (Throwable t) {
                    Log.e(TAG, capabilityId() + " op=" + opCode + " crashed", t);
                    sink.fail(Pipe.E_BAD_ARGS, t.getClass().getSimpleName());
                }
            });
        }

        @Override public void subscribe(int[] topics, IPipeCallback callback) {
            if (callback == null || topics == null) return;
            // P1 修复：注册与补快照整体持 mPropLock，与 set()/pushProperty 互斥，
            // 保证「先注册→再读当前快照」期间不被 set 穿插，订阅者快照与后续增量首尾衔接。
            synchronized (mPropLock) {
                mTopicsOf.put(callback.asBinder(), topics.clone());
                mSubs.register(callback);
                // 不变量#3：含属性主题 → 立即向该订阅者补快照
                for (int t : topics) {
                    if (!Pipe.isPropTopic(t)) continue;
                    Object v = mProps.get(Pipe.propIdOf(t));
                    if (v == null) continue;
                    Bundle b = new Bundle(); put(b, Pipe.K_VALUE, v);
                    try { callback.onTopic(t, b); } catch (RemoteException ignored) {}
                }
            }
        }

        @Override public void unsubscribe(IPipeCallback callback) {
            if (callback == null) return;
            mTopicsOf.remove(callback.asBinder());
            mSubs.unregister(callback);
        }

        @Override public Bundle snapshot(int[] propertyIds) {
            return mProps.snapshotOf(propertyIds != null ? propertyIds : new int[0]);
        }

        @Override public int ping(int nonce) { return nonce; }

        @Override public Bundle describe() {
            Bundle b = new Bundle();
            b.putInt(Pipe.K_VERSION, version());
            return b;
        }
    };

    /** 供 CabinLink.publish 取出注册 */
    public final ICapabilityPipe pipe() { return mPipe; }

    // ── 工具 ─────────────────────────────────────────────────
    private static boolean contains(int[] a, int v) {
        if (a == null) return false;
        for (int x : a) if (x == v) return true;
        return false;
    }
    /** Bundle 单值装载（@Pod 编组器落地前的最小集） */
    static void put(Bundle b, String k, Object v) {
        if (v == null) return;
        if (v instanceof Boolean) b.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) b.putInt(k, (Integer) v);
        else if (v instanceof Float)   b.putFloat(k, (Float) v);
        else if (v instanceof Long)    b.putLong(k, (Long) v);
        else if (v instanceof String)  b.putString(k, (String) v);
        else if (v instanceof android.os.Parcelable) b.putParcelable(k, (android.os.Parcelable) v);
        else throw new IllegalArgumentException("unsupported pod type: " + v.getClass());
    }
}
