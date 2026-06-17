package com.baic.cabinlink.kernel;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.baic.cabinlink.pipe.ICapabilityPipe;
import com.baic.cabinlink.pipe.ILinkKernel;
import com.baic.cabinlink.pipe.ILinkWatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 控制面实现：注册表 + 常驻 watcher（waitFor 即订阅，崩溃恢复自动再通知）。
 * 进程级单例（教训#9）。仅做注册/发现/健康/ACL——不碰业务数据（铁律）。
 *
 * 稳定性三件套：
 *  · {@link AclGuard}     注册/发现入口做签名级鉴权，pid/uid 取 Binder 内核值（教训#4/#5）；
 *  · {@link HealthMonitor} 独立线程周期 ping，僵尸 Binder 自动剔除并通知 onUnavailable；
 *  · {@link Watchdog}     主线程 ANR 自检（独立线程检查，教训#1）。
 */
final class KernelImpl extends ILinkKernel.Stub implements HealthMonitor.Host {

    private static final String TAG = "LinkKernel";

    private static final class Record {
        final ICapabilityPipe pipe; final int version; final IBinder.DeathRecipient guard;
        Record(ICapabilityPipe p, int v, IBinder.DeathRecipient g) { pipe = p; version = v; guard = g; }
    }

    private final ConcurrentHashMap<String, Record> mRegistry = new ConcurrentHashMap<>();
    /** waitFor 的常驻观察者：能力每次上线都通知（消费端 reattach 依据）——教训#3 并发集合 */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<ILinkWatcher>> mWatchers =
            new ConcurrentHashMap<>();

    private final AclGuard mAcl;
    private final HealthMonitor mHealth = new HealthMonitor(this);

    KernelImpl(Context ctx) {
        mAcl = new AclGuard(ctx);
    }

    /** 由 Service.onCreate 调用，启动健康巡检线程。 */
    void onKernelStart() { mHealth.start(); }

    @Override public int register(String id, int version, ICapabilityPipe pipe) {
        // 教训#4/#5：先鉴权，pid/uid 全部取自 Binder 内核值，绝不信任调用方参数
        if (!mAcl.checkRegister()) return -1;   // -1 = 无权限
        if (TextUtils.isEmpty(id) || !id.startsWith("baic.")) return -2;
        if (pipe == null || !pipe.asBinder().isBinderAlive()) return -3;

        IBinder.DeathRecipient guard = () -> onDied(id);
        try { pipe.asBinder().linkToDeath(guard, 0); }
        catch (RemoteException e) { return -3; }

        Record old = mRegistry.put(id, new Record(pipe, version, guard));
        if (old != null) old.pipe.asBinder().unlinkToDeath(old.guard, 0);

        Log.i(TAG, "register " + id + " v=" + version + (old != null ? " (replaced)" : ""));
        notifyAvailable(id, pipe, version);
        return 0;
    }

    @Override public void unregister(String id) {
        if (!mAcl.checkRegister()) return;   // 非受信调用方不得注销他人能力
        Record r = mRegistry.remove(id);
        if (r == null) return;
        r.pipe.asBinder().unlinkToDeath(r.guard, 0);
        notifyUnavailable(id);
    }

    @Override public ICapabilityPipe query(String id) {
        if (!mAcl.checkDiscover()) return null;
        Record r = mRegistry.get(id);
        return r != null ? r.pipe : null;
    }

    @Override public void waitFor(String id, ILinkWatcher watcher) {
        if (watcher == null) return;
        if (!mAcl.checkDiscover()) return;   // 无权限静默拒绝（不暴露注册表存在性）
        mWatchers.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).addIfAbsent(watcher);
        Record r = mRegistry.get(id);   // 已在线 → 立即回调（消费端无需区分先后）
        if (r != null) {
            try { watcher.onAvailable(id, r.pipe, r.version); } catch (RemoteException ignored) {}
        }
    }

    @Override public void unwatch(String id, ILinkWatcher watcher) {
        CopyOnWriteArrayList<ILinkWatcher> l = mWatchers.get(id);
        if (l != null) l.remove(watcher);
    }

    private void onDied(String id) {
        Log.w(TAG, "capability died: " + id);
        Record r = mRegistry.remove(id);
        if (r != null) r.pipe.asBinder().unlinkToDeath(r.guard, 0);
        notifyUnavailable(id);
    }

    private void notifyAvailable(String id, ICapabilityPipe pipe, int version) {
        CopyOnWriteArrayList<ILinkWatcher> l = mWatchers.get(id);
        if (l == null) return;
        for (ILinkWatcher w : l) {
            try { w.onAvailable(id, pipe, version); }
            catch (RemoteException e) { l.remove(w); }   // 死亡 watcher 自动清理
        }
    }

    private void notifyUnavailable(String id) {
        CopyOnWriteArrayList<ILinkWatcher> l = mWatchers.get(id);
        if (l == null) return;
        for (ILinkWatcher w : l) {
            try { w.onUnavailable(id); }
            catch (RemoteException e) { l.remove(w); }
        }
    }

    // ════ HealthMonitor.Host 实现 ════════════════════════════
    @Override public Map<String, ICapabilityPipe> snapshot() {
        // 浅拷贝出 id→pipe 视图供巡检线程遍历，不暴露 Record（含 deathRecipient）
        Map<String, ICapabilityPipe> m = new HashMap<>(mRegistry.size());
        for (Map.Entry<String, Record> e : mRegistry.entrySet()) {
            m.put(e.getKey(), e.getValue().pipe);
        }
        return m;
    }

    @Override public void onCapabilityDead(String id, ICapabilityPipe deadPipe) {
        // 巡检判死：仅当注册表里仍是这条 pipe 才剔除（避免误删期间已重注册的新实例）
        Record r = mRegistry.get(id);
        if (r == null || r.pipe.asBinder() != deadPipe.asBinder()) return;
        if (mRegistry.remove(id, r)) {
            try { r.pipe.asBinder().unlinkToDeath(r.guard, 0); } catch (Exception ignored) {}
            Log.w(TAG, "health evict zombie: " + id);
            notifyUnavailable(id);
        }
    }
}
