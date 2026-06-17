package com.baic.bridge.core;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.baic.bridge.transport.BridgeEnvelope;
import com.baic.bridge.transport.IBridgeNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Bridge 中枢：注册表 + 去中心化路由 + 收发 + RPC + 订阅分发 + 去重。
 * 线程模型：入站 deliver 在 Binder 线程到达，统一 post 到单线程 worker 串行处理；
 * 超时/重连在单线程 scheduler。两者均为 daemon 线程。
 */
final class BridgeCore {
    private static final String TAG = "Bridge.Core";
    private static final int DEDUP_CAP = 1024;

    private final Context ctx;
    private final String selfId;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService worker;
    private final RpcEngine rpc;
    private final ConnectionManager connections;
    private final AclGuard acl;

    private final CopyOnWriteArraySet<String> modules = new CopyOnWriteArraySet<>();
    private final ConcurrentHashMap<String, RequestHandler> handlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<EventListener>> listeners = new ConcurrentHashMap<>();

    // msgId 去重（有界 LRU），重连补发场景下幂等
    private final LinkedHashMap<String, Boolean> seen =
            new LinkedHashMap<String, Boolean>(DEDUP_CAP, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) { return size() > DEDUP_CAP; }
            };

    // 本端 Binder：既作为全量 Service 的 onBind 返回值，也作为 lite 通过 attach 交给对端的回调
    private final IBridgeNode.Stub localNode = new IBridgeNode.Stub() {
        @Override public void deliver(BridgeEnvelope env) {
            final int uid = Binder.getCallingUid();           // 内核身份，绝不信 env.source（CabinLink 铁律）
            worker.execute(() -> onInbound(env, uid));
        }
        @Override public void attach(IBridgeNode peer, String peerNodeId) {
            onAttach(peer, peerNodeId);
        }
    };

    BridgeCore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.selfId = this.ctx.getPackageName();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemon("bridge-sched"));
        this.worker = Executors.newSingleThreadExecutor(daemon("bridge-worker"));
        this.rpc = new RpcEngine(scheduler);
        this.connections = new ConnectionManager(this.ctx, this, scheduler);
        this.acl = new AclGuard(this.ctx);
    }

    private static ThreadFactory daemon(final String name) {
        return r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; };
    }

    void start() {
        List<NodeDescriptor> nodes = NodeRegistry.load(ctx);
        connections.connectAll(nodes, selfId);
        Log.i(TAG, "Bridge 启动 self=" + selfId + " 清单节点数=" + nodes.size());
    }

    // ───────────────────────── 供 ConnectionManager 调用 ─────────────────────────

    String selfId() { return selfId; }
    IBinder localNode() { return localNode; }

    void attachTo(IBridgeNode remote) {
        try { remote.attach(localNode, selfId); }
        catch (RemoteException e) { Log.w(TAG, "attach 对端失败 " + e); }
    }

    void linkDeath(final PeerConnection pc) {
        try {
            final IBinder b = pc.remote.asBinder();
            b.linkToDeath(new IBinder.DeathRecipient() {
                @Override public void binderDied() {
                    Log.w(TAG, "对端死亡 " + pc.peerId + "，清理路由 + 失败该对端在途请求");
                    connections.peers.remove(pc.peerId);
                    rpc.failPeer(pc.peerId, BridgeErrors.E_NOT_CONNECTED, "对端已断开");
                }
            }, 0);
        } catch (Exception e) {
            Log.w(TAG, "linkToDeath 失败 " + pc.peerId + " " + e);
        }
    }

    void sendHelloTo(PeerConnection pc) { pc.send(buildHello()); }

    // ───────────────────────── 入站处理（worker 线程串行）─────────────────────────

    private void onInbound(BridgeEnvelope env, int callingUid) {
        if (env == null) return;
        if (env.msgId != null && !firstSeen(env.msgId)) return;   // 去重
        if (!acl.verifySource(callingUid, env.source)) return;    // 身份校验：防 source 伪造，失败即丢弃（请求方超时感知）
        switch (env.type) {
            case BridgeEnvelope.TYPE_HELLO:    handleHello(env); break;
            case BridgeEnvelope.TYPE_REQUEST:  handleRequest(env); break;
            case BridgeEnvelope.TYPE_RESPONSE: handleResponse(env); break;
            case BridgeEnvelope.TYPE_EVENT:    handleEvent(env); break;
            default: break;
        }
    }

    private void handleHello(BridgeEnvelope env) {
        PeerConnection pc = connections.peers.get(env.source);
        if (pc == null) return;   // attach 尚未建立，等对端 attach 后会补握手
        try {
            JSONObject o = new JSONObject(env.payload == null ? "{}" : env.payload);
            applyTopics(pc.providedTopics, o.optJSONArray("provide"));
            applyTopics(pc.subscribedTopics, o.optJSONArray("subscribe"));
            Log.i(TAG, "握手 from " + env.source + " provide=" + pc.providedTopics + " subscribe=" + pc.subscribedTopics);
        } catch (Exception e) {
            Log.w(TAG, "解析 HELLO 失败 " + e);
        }
    }

    private static void applyTopics(java.util.Set<String> set, JSONArray arr) {
        set.clear();
        if (arr != null) for (int i = 0; i < arr.length(); i++) set.add(arr.optString(i));
    }

    private void handleRequest(final BridgeEnvelope env) {
        final PeerConnection src = connections.peers.get(env.source);
        RequestHandler h = handlers.get(env.topic);
        if (h == null) {
            if (src != null) src.send(newEnv(BridgeEnvelope.TYPE_RESPONSE, env.topic, "{}", env.correlationId, BridgeErrors.E_NO_PROVIDER));
            return;
        }
        BridgeResponder resp = new BridgeResponder((code, payload) -> {
            if (src != null) src.send(newEnv(BridgeEnvelope.TYPE_RESPONSE, env.topic, payload, env.correlationId, code));
        });
        try {
            h.handle(new BridgeRequest(env.payload), resp);
        } catch (Exception e) {
            Log.w(TAG, "handler 异常 topic=" + env.topic + " " + e);
            resp.fail(BridgeErrors.E_INTERNAL, "提供方处理异常");
        }
    }

    private void handleResponse(BridgeEnvelope env) {
        if (env.code == BridgeErrors.OK) {
            rpc.complete(env.correlationId, BridgeErrors.OK, env.payload);
        } else {
            rpc.complete(env.correlationId, env.code, extractMsg(env.payload));
        }
    }

    private void handleEvent(BridgeEnvelope env) {
        CopyOnWriteArrayList<EventListener> ls = listeners.get(env.topic);
        if (ls != null) for (EventListener l : ls) {
            try { l.onEvent(env.payload); } catch (Exception e) { Log.w(TAG, "listener 异常 " + e); }
        }
    }

    private void onAttach(IBridgeNode peer, String peerId) {
        if (peerId == null) return;
        PeerConnection pc = connections.peers.get(peerId);
        if (pc == null) {
            pc = new PeerConnection(peerId, peer);
            connections.peers.put(peerId, pc);
            linkDeath(pc);
        } else {
            pc.remote = peer;
        }
        sendHelloTo(pc);   // 回握，让对端知道本端 provide/subscribe
    }

    // ───────────────────────── 对外 API（经 Bridge / BridgeNodeHost）─────────────────────────

    void register(String module) { if (module != null) modules.add(module); }

    void onRequest(String topic, RequestHandler handler) {
        handlers.put(topic, handler);
        broadcastHello();   // 能力变化，重新通告
    }

    void subscribe(String topic, EventListener listener) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(listener);
        broadcastHello();   // 订阅变化，重新通告，发布方据此推送
    }

    void publish(String topic, String payload) {
        BridgeEnvelope env = newEnv(BridgeEnvelope.TYPE_EVENT, topic, payload, null, 0);
        for (PeerConnection pc : connections.peers.values()) {
            if (pc.subscribes(topic)) pc.send(env);
        }
    }

    void request(String topic, String payload, BridgeReply reply, long timeoutMs) {
        PeerConnection provider = findProvider(topic);
        if (provider == null) { reply.onError(BridgeErrors.E_NO_PROVIDER, "无节点提供 " + topic); return; }
        String corr = UUID.randomUUID().toString();
        rpc.register(corr, provider.peerId, reply, timeoutMs);
        boolean sent = provider.send(newEnv(BridgeEnvelope.TYPE_REQUEST, topic, payload, corr, 0));
        if (!sent) rpc.complete(corr, BridgeErrors.E_NOT_CONNECTED, "投递失败，对端通道已断");
    }

    BridgeResult requestSync(String topic, String payload, long timeoutMs) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("requestSync 禁止在主线程调用，会阻塞导致 ANR；请用异步 request");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final BridgeResult[] box = new BridgeResult[1];
        request(topic, payload, new BridgeReply() {
            @Override public void onSuccess(String p) { box[0] = new BridgeResult(BridgeErrors.OK, p, null); latch.countDown(); }
            @Override public void onError(int c, String m) { box[0] = new BridgeResult(c, null, m); latch.countDown(); }
        }, timeoutMs);
        try { latch.await(timeoutMs + 500, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return box[0] != null ? box[0] : new BridgeResult(BridgeErrors.E_TIMEOUT, null, "请求超时");
    }

    // ───────────────────────── 内部工具 ─────────────────────────

    private PeerConnection findProvider(String topic) {
        for (PeerConnection pc : connections.peers.values()) if (pc.provides(topic)) return pc;
        return null;
    }

    private void broadcastHello() {
        for (PeerConnection pc : connections.peers.values()) sendHelloTo(pc);
    }

    private BridgeEnvelope buildHello() {
        JSONObject o = new JSONObject();
        try {
            o.put("provide", new JSONArray(handlers.keySet()));
            o.put("subscribe", new JSONArray(listeners.keySet()));
        } catch (Exception ignore) {}
        return newEnv(BridgeEnvelope.TYPE_HELLO, null, o.toString(), null, 0);
    }

    private BridgeEnvelope newEnv(int type, String topic, String payload, String corr, int code) {
        BridgeEnvelope e = new BridgeEnvelope();
        e.msgId = UUID.randomUUID().toString();
        e.type = type;
        e.topic = topic;
        e.schemaVersion = 1;
        e.source = selfId;
        e.correlationId = corr;
        e.timestamp = System.currentTimeMillis();
        e.needAck = false;
        e.code = code;
        e.payload = payload == null ? "{}" : payload;
        return e;
    }

    private boolean firstSeen(String msgId) {
        synchronized (seen) {
            if (seen.containsKey(msgId)) return false;
            seen.put(msgId, Boolean.TRUE);
            return true;
        }
    }

    private static String extractMsg(String payload) {
        if (payload == null) return "";
        try { return new JSONObject(payload).optString("msg", payload); }
        catch (Exception e) { return payload; }
    }
}
