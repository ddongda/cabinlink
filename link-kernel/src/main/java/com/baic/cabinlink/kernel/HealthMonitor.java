package com.baic.cabinlink.kernel;

import android.os.RemoteException;
import android.util.Log;

import com.baic.cabinlink.pipe.ICapabilityPipe;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 能力健康巡检：独立线程周期 ping 每个已注册能力，僵尸 Binder（ping 不通/回值错/进程已死）
 * 立即剔除并触发下线通知。
 *
 * 为什么独立线程：ping 是同步 Binder 调用，可能阻塞（对端卡死）；放主线程会拖垮控制面，
 * 放业务线程会污染串行语义。本类自带 daemon 线程 while+sleep，崩溃自愈靠 catch-all。
 *
 * 不接触业务数据：只用 ICapabilityPipe.ping(nonce)（传输 ABI 自带的健康原语）。
 */
final class HealthMonitor {

    private static final String TAG = "LinkKernel.Health";
    private static final long DEFAULT_INTERVAL_MS = 10_000L;
    private static final long PING_BUDGET_MS = 2_000L;   // 单次 ping 预算（仅用于日志/统计）

    /** 与 KernelImpl 解耦：巡检线程通过本接口读注册表、报告死亡。 */
    interface Host {
        /** 返回当前注册表的快照视图（id → pipe）；实现方需保证并发安全可遍历。 */
        Map<String, ICapabilityPipe> snapshot();
        /** 巡检发现某能力不可达，请求内核按死亡路径剔除并通知 onUnavailable。 */
        void onCapabilityDead(String id, ICapabilityPipe deadPipe);
    }

    private final Host mHost;
    private final long mIntervalMs;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private volatile Thread mThread;

    HealthMonitor(Host host) { this(host, DEFAULT_INTERVAL_MS); }
    HealthMonitor(Host host, long intervalMs) { mHost = host; mIntervalMs = intervalMs; }

    void start() {
        if (!mRunning.compareAndSet(false, true)) return;   // 幂等启动
        Thread t = new Thread(this::loop, "link-health");
        t.setDaemon(true);
        mThread = t;
        t.start();
        Log.i(TAG, "health monitor started, interval=" + mIntervalMs + "ms");
    }

    void stop() {
        mRunning.set(false);
        Thread t = mThread;
        if (t != null) t.interrupt();
    }

    private void loop() {
        while (mRunning.get()) {
            try {
                Thread.sleep(mIntervalMs);
            } catch (InterruptedException e) {
                if (!mRunning.get()) break;
                continue;
            }
            try {
                sweep();
            } catch (Throwable t) {
                // 巡检自身绝不可因单次异常退出线程（否则健康面失效且无人察觉）
                Log.e(TAG, "sweep crashed, will retry next cycle", t);
            }
        }
        Log.i(TAG, "health monitor stopped");
    }

    private void sweep() {
        Map<String, ICapabilityPipe> snap = mHost.snapshot();
        for (Map.Entry<String, ICapabilityPipe> e : snap.entrySet()) {
            if (!mRunning.get()) return;
            String id = e.getKey();
            ICapabilityPipe pipe = e.getValue();
            if (pipe == null) continue;

            // 先看 Binder 存活位（廉价、不发 IPC）
            if (!pipe.asBinder().isBinderAlive()) {
                Log.w(TAG, "dead(binder) " + id);
                mHost.onCapabilityDead(id, pipe);
                continue;
            }

            // 再做一次真实 ping：对端进程在但 Looper 卡死时 isBinderAlive 仍为 true，
            // 只有 ping 能验出僵尸。
            int nonce = ThreadLocalRandom.current().nextInt();
            try {
                int echo = pipe.ping(nonce);
                if (echo != nonce) {
                    Log.w(TAG, "dead(ping mismatch) " + id + " sent=" + nonce + " got=" + echo);
                    mHost.onCapabilityDead(id, pipe);
                }
            } catch (RemoteException re) {
                Log.w(TAG, "dead(ping remote) " + id, re);
                mHost.onCapabilityDead(id, pipe);
            } catch (Throwable t) {
                Log.w(TAG, "dead(ping error) " + id, t);
                mHost.onCapabilityDead(id, pipe);
            }
        }
    }
}
