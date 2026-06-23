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
    // 统一订阅表：精确 topic 与整模块通配（"module.*"）混存，入站按 matches 分发
    private final CopyOnWriteArrayList<Sub> subs = new CopyOnWriteArrayList<>();
    // 每个已注册模块的就绪状态 + 回调
    private final ConcurrentHashMap<String, ModuleState> moduleStates = new ConcurrentHashMap<>();
    // 节点id → 它提供的模块（来自静态清单），仅用于 onConnected 归属
    private final ConcurrentHashMap<String, java.util.Set<String>> nodeModules = new ConcurrentHashMap<>();

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
        for (NodeDescriptor n : nodes) {
            if (n.id != null && !n.modules.isEmpty()) nodeModules.put(n.id, n.modules);
        }
        connections.connectAll(nodes, selfId);
        Log.i(TAG, "==== Bridge SDK 启动 ==== self=" + selfId);
        Log.i(TAG, "SDK版本=" + BuildConfig.SDK_VERSION + " gitSha=" + BuildConfig.GIT_SHA
                + " 构建=" + BuildConfig.BUILD_TIME + " 传输ABI=" + BridgeEnvelope.ABI_VERSION
                + " 清单节点数=" + nodes.size());
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
                    Log.w(TAG, "提供方死亡 " + pc.peerId + "，清理路由 + 失败该对端在途请求");
                    worker.execute(() -> {
                        connections.peers.remove(pc.peerId);
                        rpc.failPeer(pc.peerId, BridgeErrors.E_NOT_CONNECTED, "对端已断开");
                        reevaluateAll();
                    });
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
            reevaluateAll();   // 提供方能力到位，重算各模块就绪
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
        for (Sub s : subs) {
            if (s.matches(env.topic)) {
                try { s.listener.onEvent(env.topic, env.payload); }
                catch (Exception e) { Log.w(TAG, "listener 异常 topic=" + env.topic + " " + e); }
            }
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

    // ───────────────────────── 对外 API（经统一门面 Bridge）─────────────────────────

    void register(String module) { register(module, 0, null); }

    void register(String module, int contractVersion, ModuleCallback cb) {
        if (module == null) return;
        modules.add(module);
        ModuleState st = moduleStates.computeIfAbsent(module, m -> new ModuleState(m, contractVersion));
        if (cb != null) st.callbacks.add(cb);
        Log.i(TAG, "注册模块 " + module + " 契约门面版本=" + contractVersion + " callback=" + (cb != null));
        worker.execute(() -> reevaluate(module));   // 提供方可能已就绪，补一次评估
    }

    boolean isReady(String module) {
        ModuleState st = moduleStates.get(module);
        return st != null && st.isReady();
    }

    /** 该模块是否存在握手完成、provide 了其前缀 topic 的提供方。 */
    private boolean evaluateReady(String module) {
        String prefix = module + ".";
        for (PeerConnection pc : connections.peers.values()) {
            for (String t : pc.providedTopics) if (t != null && t.startsWith(prefix)) return true;
        }
        return false;
    }

    /** worker 线程：推进单模块状态并触发回调/日志。 */
    private void reevaluate(String module) {
        ModuleState st = moduleStates.get(module);
        if (st == null) return;
        switch (st.applyReadiness(evaluateReady(module))) {
            case READY:
                Log.i(TAG, "模块就绪 " + module);
                for (ModuleCallback cb : st.callbacks) safe(cb::onReady);
                break;
            case REBOOTED:
                Log.i(TAG, "模块提供方重启恢复 " + module);
                for (ModuleCallback cb : st.callbacks) safe(cb::onRebooted);
                break;
            case LOST:
                Log.w(TAG, "模块提供方已离线 " + module);
                break;
            default: break;
        }
    }

    private void reevaluateAll() {
        for (String m : moduleStates.keySet()) reevaluate(m);
    }

    /** bind 成功（onServiceConnected）—— 对该节点提供的模块触发 onConnected（排查日志用）。 */
    void onPeerConnected(String nodeId) {
        worker.execute(() -> {
            java.util.Set<String> mods = nodeModules.get(nodeId);
            if (mods == null) return;
            for (String m : mods) {
                ModuleState st = moduleStates.get(m);
                if (st != null) {
                    Log.i(TAG, "模块节点已连接 module=" + m + " node=" + nodeId);
                    for (ModuleCallback cb : st.callbacks) safe(cb::onConnected);
                }
            }
        });
    }

    /** 连接断开（onServiceDisconnected）—— 移除并重算模块。 */
    void onPeerLost(String nodeId) {
        worker.execute(() -> {
            connections.peers.remove(nodeId);
            reevaluateAll();
        });
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Exception e) { Log.w(TAG, "模块回调异常 " + e); }
    }

    void onRequest(String topic, RequestHandler handler) {
        handlers.put(topic, handler);
        broadcastHello();   // 能力变化，重新通告
    }

    /** 单 topic 订阅（旧 API，回调无需 topic）。 */
    void subscribe(String topic, EventListener listener) {
        if (topic == null || listener == null) return;
        subs.add(new Sub(topic, (t, p) -> listener.onEvent(p)));
        broadcastHello();   // 订阅变化，重新通告，发布方据此推送
    }

    /** 批量订阅：多个精确 topic 或整模块通配（"module.*"）共用一个带 topic 的回调。 */
    void subscribePatterns(String[] patterns, TopicEventListener listener) {
        if (patterns == null || listener == null) return;
        for (String p : patterns) if (p != null && !p.isEmpty()) subs.add(new Sub(p, listener));
        broadcastHello();
    }

    void publish(String topic, String payload) {
        BridgeEnvelope env = newEnv(BridgeEnvelope.TYPE_EVENT, topic, payload, null, 0);
        for (PeerConnection pc : connections.peers.values()) {
            if (pc.subscribes(topic)) pc.send(env);
        }
    }

    void request(String topic, String payload, BridgeReply reply, long timeoutMs) {
        PeerConnection provider = findProvider(topic);
        if (provider == null) {
            String module = Topics.moduleOf(topic);
            Log.w(TAG, "request 调用时模块未就绪 topic=" + topic + " module=" + module
                    + " isReady=" + isReady(module)
                    + "，当前无提供方，返回 E_NO_PROVIDER；建议等 onReady 回调后再调用");
            reply.onError(BridgeErrors.E_NO_PROVIDER, "无节点提供 " + topic);
            return;
        }
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
            // 订阅声明去重；通配项（"module.*"）原样发出，发布端按前缀匹配推送
            java.util.LinkedHashSet<String> patterns = new java.util.LinkedHashSet<>();
            for (Sub s : subs) patterns.add(s.pattern);
            o.put("subscribe", new JSONArray(patterns));
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

    /** 一条订阅：精确 topic，或以 "module.*" 表示的整模块前缀通配。 */
    private static final class Sub {
        final String pattern;             // 原始声明（精确 topic 或 "module.*"），用于 HELLO 通告
        final boolean wildcard;
        final String prefix;              // 通配时的前缀（如 "usercenter."）
        final TopicEventListener listener;

        Sub(String pattern, TopicEventListener listener) {
            this.pattern = pattern;
            this.wildcard = pattern.endsWith(".*");
            this.prefix = wildcard ? pattern.substring(0, pattern.length() - 1) : pattern; // 去掉末尾 '*'，保留 '.'
            this.listener = listener;
        }

        boolean matches(String topic) {
            if (topic == null) return false;
            return wildcard ? topic.startsWith(prefix) : pattern.equals(topic);
        }
    }
}
