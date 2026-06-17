package com.baic.bridge.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.baic.bridge.transport.IBridgeNode;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 连接网格管理：按静态清单逐节点 bindService，处理握手、断开、退避重连。
 * - bindService 返回 false（开机竞速）必须重试（CabinLink 教训#6）。
 * - 进程死亡后 BIND_AUTO_CREATE 会在对端恢复时自动回调 onServiceConnected，无需手动重绑。
 */
final class ConnectionManager {
    private static final String TAG = "Bridge.Conn";
    private static final long BACKOFF_START = 1000L;
    private static final long BACKOFF_MAX   = 30000L;

    private final Context ctx;
    private final BridgeCore core;
    private final ScheduledExecutorService scheduler;

    final ConcurrentHashMap<String, PeerConnection> peers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> backoff = new ConcurrentHashMap<>();

    ConnectionManager(Context ctx, BridgeCore core, ScheduledExecutorService scheduler) {
        this.ctx = ctx; this.core = core; this.scheduler = scheduler;
    }

    void connectAll(List<NodeDescriptor> nodes, String selfId) {
        for (NodeDescriptor n : nodes) {
            if (n.id == null || n.id.equals(selfId)) continue;
            connect(n);
        }
    }

    private void connect(final NodeDescriptor n) {
        final Intent intent = new Intent(n.action);
        if (n.component != null && n.component.contains("/")) {
            String[] parts = n.component.split("/", 2);
            String pkg = parts[0];
            String cls = parts[1].startsWith(".") ? pkg + parts[1] : parts[1];
            intent.setComponent(new ComponentName(pkg, cls));
        } else {
            intent.setPackage(n.id);  // 全量自带 Service：按包名 + action 隐式解析
        }

        final ServiceConnection conn = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                backoff.remove(n.id);
                IBridgeNode remote = IBridgeNode.Stub.asInterface(binder);
                PeerConnection pc = new PeerConnection(n.id, remote);
                peers.put(n.id, pc);
                core.linkDeath(pc);
                core.attachTo(remote);     // 双向：把本端回调通道交给对端
                core.sendHelloTo(pc);      // 声明本端 provide/subscribe
                Log.i(TAG, "已连接 " + n.id);
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                Log.w(TAG, "连接断开 " + n.id + "（等待 BIND_AUTO_CREATE 自动恢复）");
                peers.remove(n.id);
            }
        };

        boolean ok;
        try {
            ok = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            ok = false;
            Log.w(TAG, "bindService 异常 " + n.id + " " + e);
        }
        if (!ok) {
            Log.w(TAG, "bindService 返回 false（可能开机竞速），退避重连 " + n.id);
            try { ctx.unbindService(conn); } catch (Exception ignore) {}
            scheduleReconnect(n);
        }
    }

    private void scheduleReconnect(final NodeDescriptor n) {
        Long cur = backoff.get(n.id);
        long delay = (cur == null) ? BACKOFF_START : cur;
        long next = Math.min(delay * 2, BACKOFF_MAX);
        backoff.put(n.id, next);
        scheduler.schedule(() -> connect(n), delay, TimeUnit.MILLISECONDS);
    }
}
