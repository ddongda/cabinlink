package com.baic.bridge.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.baic.bridge.transport.IBridgeNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 连接网格管理：按注入的 {@link ServiceNode} 逐节点 bindService，处理握手、断开、退避重连。
 *
 * <p>重连策略（极致稳定性，覆盖各类失联）：
 * <ul>
 *   <li><b>普通进程死亡</b>（崩溃 / 被 LMK 回收）：回调 onServiceDisconnected，binding 仍在，
 *       {@code BIND_AUTO_CREATE} 会在对端恢复时自动回连。兜底加 grace 看门狗——若宽限内仍未恢复
 *       （典型如被 <b>force-stop</b>，系统不会自动回连），主动 unbind + 退避重 bind。</li>
 *   <li><b>binding 彻底失效</b>：回调 onBindingDied / onNullBinding（force-stop、包替换/OTA、
 *       onBind 返回 null）——{@code BIND_AUTO_CREATE} 永不自动恢复，立即 unbind + 退避重 bind。</li>
 *   <li><b>bindService 返回 false</b>（开机竞速，CabinLink 教训#6）：退避重试。</li>
 * </ul>
 * 多个丢失回调（disconnected 看门狗 + bindingDied）经 {@link #active} “当前连接”校验做幂等，
 * 旧连接的迟到回调一律忽略，杜绝重复 bind / 连接抖动。
 */
final class ConnectionManager {
    private static final String TAG = "Bridge.Conn";
    private static final long BACKOFF_START = 1000L;
    private static final long BACKOFF_MAX = 30000L;
    private static final long RECOVER_GRACE = 2000L;   // 断开后等待 BIND_AUTO_CREATE 自动恢复的宽限，超时则主动重连

    private final Context ctx;
    private final BridgeCore core;
    private final ScheduledExecutorService scheduler;

    // 当前活跃对端表（key=对端包名）：BridgeCore 收发/路由/就绪评估都遍历它；包级可见，供 BridgeCore 直接读。
    final ConcurrentHashMap<String, PeerConnection> peers = new ConcurrentHashMap<>();
    // 每个 package 的下次退避时长记忆（指数：1s→×2→上限 30s），连上即清零。
    private final ConcurrentHashMap<String, Long> backoff = new ConcurrentHashMap<>();
    // connect() 首次去重锁：同一 package 只主动发起一次 bind（重连走 doConnect，不经此项）。
    private final java.util.Set<String> known = ConcurrentHashMap.newKeySet();
    // 每个 package「当前」活跃的 ServiceConnection：用于精准 unbind + 丢失回调幂等（remove(key,value) CAS 只回收当前连接，忽略旧连接迟到回调）。
    private final ConcurrentHashMap<String, ServiceConnection> active = new ConcurrentHashMap<>();

    ConnectionManager(Context ctx, BridgeCore core, ScheduledExecutorService scheduler) {
        this.ctx = ctx;
        this.core = core;
        this.scheduler = scheduler;
    }

    /** 日志前缀 "[包名] "：多进程 logcat 混排时区分来源（TAG 仍为 Bridge.Conn，不影响过滤）。 */
    private String p() {
        return "[" + core.selfId() + "] ";
    }

    /** 注入式连接：self 跳过 + 去重（同一 package 只 bind 一次），幂等。 */
    void connect(ServiceNode n) {
        if (n == null || n.pkg == null || n.pkg.equals(core.selfId())) return;   // 不连自己
        if (peers.containsKey(n.pkg) || !known.add(n.pkg)) return;               // 已连接/已发起则跳过
        doConnect(n);
    }

    private void doConnect(final ServiceNode n) {
        final Intent intent = new Intent(n.action);
        if (n.component != null && n.component.contains("/")) {
            String[] parts = n.component.split("/", 2);
            String pkg = parts[0];
            String cls = parts[1].startsWith(".") ? pkg + parts[1] : parts[1];
            intent.setComponent(new ComponentName(pkg, cls));
        } else {
            intent.setPackage(n.pkg);  // 全量自带 Service：按包名 + action 隐式解析
        }

        final ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                backoff.remove(n.pkg);
                IBridgeNode remote = IBridgeNode.Stub.asInterface(binder);
                PeerConnection pc = new PeerConnection(n.pkg, remote);
                peers.put(n.pkg, pc);
                core.linkDeath(pc);
                core.attachTo(remote);     // 双向：把本端回调通道交给对端
                core.sendHelloTo(pc);      // 声明本端 provide/subscribe
                core.onPeerConnected(n.pkg); // bind 成功 → 触发该节点模块的 onConnected（排查日志）
                Log.i(TAG, p() + "已连接 " + n.pkg);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // 普通进程死亡：binding 仍在，先给 BIND_AUTO_CREATE 自动恢复的机会；
                // 宽限内未回填 peers（如被 force-stop，系统不会自动回连）则主动重连兜底。
                Log.w(TAG, p() + "连接断开 " + n.pkg + "（先等 BIND_AUTO_CREATE 自动恢复，"
                        + RECOVER_GRACE + "ms 内未恢复则主动重连）");
                core.onPeerLost(n.pkg);
                final ServiceConnection self = this;
                scheduler.schedule(() -> {
                    if (active.get(n.pkg) == self && !peers.containsKey(n.pkg)) {
                        recover(n, self, "断开 " + RECOVER_GRACE + "ms 仍未自动恢复（疑似 force-stop），主动 unbind+重连 " + n.pkg);
                    }
                }, RECOVER_GRACE, TimeUnit.MILLISECONDS);
            }

            @Override
            public void onBindingDied(ComponentName name) {
                // binding 彻底失效（force-stop / 包替换 / OTA）：BIND_AUTO_CREATE 永不恢复，立即重连。
                recover(n, this, "binding 失效（force-stop/包替换），unbind+重连 " + n.pkg);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                // 对端 onBind 返回 null（异常实现）：同样退避重连。
                recover(n, this, "对端 onBind 返回 null，unbind+重连 " + n.pkg);
            }
        };

        active.put(n.pkg, conn);   // 记录当前活跃连接（供 unbind 与丢失回调幂等用）

        boolean ok;
        try {
            Log.i(TAG, p() + "发起连接 node=" + n.pkg + " action=" + n.action
                    + (n.component != null ? " component=" + n.component : "")
                    + " modules=" + n.modules);
            ok = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            ok = false;
            Log.w(TAG, p() + "bindService 异常 " + n.pkg + " " + e);
        }
        if (!ok) {
            Log.w(TAG, p() + "bindService 返回 false（可能开机竞速），退避重连 " + n.pkg);
            active.remove(n.pkg, conn);
            try {
                ctx.unbindService(conn);
            } catch (Exception ignore) {
            }
            scheduleReconnect(n);
        }
    }

    /**
     * 连接失效兜底回收：对「当前活跃连接」做一次性 unbind + 标记离线 + 退避重 bind。
     * 经 {@link #active} 的「当前连接」CAS（{@code remove(key, value)}）保证幂等：
     * disconnected 看门狗与 bindingDied 即便同时触发，也只回收一次；旧连接的迟到回调被忽略。
     */
    private void recover(ServiceNode n, ServiceConnection self, String reason) {
        if (!active.remove(n.pkg, self)) return;   // 不是当前活跃连接（已被取代）或已回收 → 幂等忽略
        Log.w(TAG, p() + reason);
        try {
            ctx.unbindService(self);
        } catch (Exception ignore) {
        }
        known.remove(n.pkg);     // 解除 connect 去重锁（重连经 doConnect 不查 known，此处仅保持状态一致）
        core.onPeerLost(n.pkg);  // 移除 peer + 模块置未就绪
        scheduleReconnect(n);    // 退避后以全新 ServiceConnection 重 bind（显式 bind 可把 force-stop 的对端拉起）
    }

    private void scheduleReconnect(final ServiceNode n) {
        Long cur = backoff.get(n.pkg);
        long delay = (cur == null) ? BACKOFF_START : cur;
        long next = Math.min(delay * 2, BACKOFF_MAX);
        backoff.put(n.pkg, next);
        scheduler.schedule(() -> doConnect(n), delay, TimeUnit.MILLISECONDS);
    }
}
