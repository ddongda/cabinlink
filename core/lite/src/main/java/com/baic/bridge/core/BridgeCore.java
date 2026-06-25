package com.baic.bridge.core;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.baic.bridge.transport.BridgeEnvelope;
import com.baic.bridge.transport.IBridgeNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static final long SLOW_SYNC_HANDLER_WARN_MS = 200L;   // onRequest 的 sync handler 同步占用 worker 超此值即告警（误用提醒：应改用 onRequestAsync）

    /** Application Context，用于 bindService / PackageManager 等系统调用，避免 Activity 泄漏 */
    private final Context ctx;
    /** 本端节点 ID，等于宿主包名，全局唯一，用于 Envelope.source 填充与路由寻址 */
    private final String selfId;
    /** 日志前缀 "[包名] "：多进程 logcat 混排时区分日志来源（TAG 仍为 Bridge.Core，不影响过滤） */
    private final String P;
    /** 单线程定时调度器：RPC 超时检测、断线退避重连，daemon 线程 */
    private final ScheduledExecutorService scheduler;
    /** 单线程串行工作队列：所有入站消息处理均 post 到此线程，消除并发竞争，daemon 线程 */
    private final ExecutorService worker;
    /** RPC 引擎：管理在途请求（correlationId → 回调）及超时/失败通知 */
    private final RpcEngine rpc;
    /** 连接管理器：按静态节点清单发起 bindService，维护对端 PeerConnection 生命周期 */
    private final ConnectionManager connections;
    /** 访问控制守卫：基于 Binder.getCallingUid() 校验消息来源，防 source 字段伪造 */
    private final AclGuard acl;

    // ───── 注册 / 分发 / 状态 五张本端表（分工：provide 能力 / subscribe 订阅 / 模块就绪 / onConnected 归属 / 去重）─────
    // provide 能力表：本端能响应的 request topic → 处理器。onRequest/onRequestAsync 写、handleRequest 读；经 HELLO 的 provide 列表通告对端。
    private final ConcurrentHashMap<String, RequestHandler> handlers = new ConcurrentHashMap<>();
    // 异步 handler 标记：onRequestAsync 注册的 topic 收录于此。handleRequest 据此把 handler 交给 handlerPool（而非 worker 内联），消除慢 handler 队头阻塞。
    private final java.util.Set<String> asyncTopics = ConcurrentHashMap.newKeySet();
    // 异步 handler 线程池：onRequestAsync 的 handler 在此并行执行，不占单线程 worker。懒建（首个 onRequestAsync 时），固定大小 daemon；纯 sync 形态永不创建。
    private volatile ExecutorService handlerPool;
    // subscribe 订阅表：精确 topic 与整模块通配（"module.*"）混存，入站事件按 Sub.matches 分发；经 HELLO 的 subscribe 列表通告对端。
    private final CopyOnWriteArrayList<Sub> subs = new CopyOnWriteArrayList<>();
    // 模块就绪表：已注册模块 → 就绪状态机 + 回调（onConnected/onReady/onRebooted）。
    private final ConcurrentHashMap<String, ModuleState> moduleStates = new ConcurrentHashMap<>();
    // 节点归属表：节点包名 → 它提供的模块集合（来自注入的 ServiceNode.modules），仅用于 bind 成功时把 onConnected 归到对应模块。
    private final ConcurrentHashMap<String, java.util.Set<String>> nodeModules = new ConcurrentHashMap<>();

    // 去重表：msgId 有界 LRU（容量 DEDUP_CAP），重连补发等场景下保证幂等
    private final LinkedHashMap<String, Boolean> seen =
            new LinkedHashMap<String, Boolean>(DEDUP_CAP, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
                    return size() > DEDUP_CAP;
                }
            };

    // 本端 Binder：既作为全量 Service 的 onBind 返回值，也作为 lite 通过 attach 交给对端的回调
    private final IBridgeNode.Stub localNode = new IBridgeNode.Stub() {
        @Override
        public void deliver(BridgeEnvelope env) {
            final int uid = Binder.getCallingUid();           // 内核身份，绝不信 env.source（CabinLink 铁律）
            worker.execute(() -> onInbound(env, uid));
        }

        @Override
        public void attach(IBridgeNode peer, String peerNodeId) {
            onAttach(peer, peerNodeId);
        }
    };

    BridgeCore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.selfId = this.ctx.getPackageName();
        this.P = "[" + this.selfId + "] ";
        this.scheduler = Executors.newSingleThreadScheduledExecutor(daemon("bridge-sched"));
        this.worker = Executors.newSingleThreadExecutor(daemon("bridge-worker"));
        this.rpc = new RpcEngine(scheduler);
        this.connections = new ConnectionManager(this.ctx, this, scheduler);
        this.acl = new AclGuard(this.ctx);
    }

    private static ThreadFactory daemon(final String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }

    void start() {
        Log.i(TAG, P + "==== Bridge SDK 启动 ====");
        Log.i(TAG, P + "SDK版本=" + BuildConfig.SDK_VERSION + " gitSha=" + BuildConfig.GIT_SHA
                + " 构建=" + BuildConfig.BUILD_TIME + " 传输ABI=" + BridgeEnvelope.ABI_VERSION);
    }

    // ───────────────────────── 供 ConnectionManager 调用 ─────────────────────────

    String selfId() {
        return selfId;
    }

    IBinder localNode() {
        return localNode;
    }

    void attachTo(IBridgeNode remote) {
        try {
            remote.attach(localNode, selfId);
        } catch (RemoteException e) {
            Log.w(TAG, P + "attach 对端失败 " + e);
        }
    }

    void linkDeath(final PeerConnection pc) {
        try {
            final IBinder b = pc.remote.asBinder();
            b.linkToDeath(new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    Log.w(TAG, P + "提供方死亡 " + pc.peerId + "，清理路由 + 失败该对端在途请求");
                    worker.execute(() -> {
                        connections.peers.remove(pc.peerId);
                        rpc.failPeer(pc.peerId, BridgeErrors.E_NOT_CONNECTED, "对端已断开");
                        reevaluateAll();
                    });
                }
            }, 0);
        } catch (Exception e) {
            Log.w(TAG, P + "linkToDeath 失败 " + pc.peerId + " " + e);
        }
    }

    void sendHelloTo(PeerConnection pc) {
        pc.send(buildHello());
    }

    // ───────────────────────── 入站处理（worker 线程串行）─────────────────────────

    private void onInbound(BridgeEnvelope env, int callingUid) {
        if (env == null) return;
        if (env.msgId != null && !firstSeen(env.msgId)) return;   // 去重
        if (!acl.verifySource(callingUid, env.source)) return;    // 身份校验：防 source 伪造，失败即丢弃（请求方超时感知）
        switch (env.type) {
            case BridgeEnvelope.TYPE_HELLO:
                handleHello(env);
                break;
            case BridgeEnvelope.TYPE_REQUEST:
                handleRequest(env);
                break;
            case BridgeEnvelope.TYPE_RESPONSE:
                handleResponse(env);
                break;
            case BridgeEnvelope.TYPE_EVENT:
                handleEvent(env);
                break;
            default:
                break;
        }
    }

    private void handleHello(BridgeEnvelope env) {
        PeerConnection pc = connections.peers.get(env.source);
        if (pc == null) return;   // attach 尚未建立，等对端 attach 后会补握手
        try {
            JSONObject o = new JSONObject(env.payload == null ? "{}" : env.payload);
            applyTopics(pc.providedTopics, o.optJSONArray("provide"));
            applyTopics(pc.subscribedTopics, o.optJSONArray("subscribe"));
            Log.i(TAG, P + "握手 from " + env.source + " provide=" + pc.providedTopics + " subscribe=" + pc.subscribedTopics);
            reevaluateAll();   // 提供方能力到位，重算各模块就绪
        } catch (Exception e) {
            Log.w(TAG, P + "解析 HELLO 失败 " + e);
        }
    }

    private static void applyTopics(java.util.Set<String> set, JSONArray arr) {
        set.clear();
        if (arr != null) for (int i = 0; i < arr.length(); i++) set.add(arr.optString(i));
    }

    private void handleRequest(final BridgeEnvelope env) {
        final PeerConnection src = connections.peers.get(env.source);
        RequestHandler h = handlers.get(env.topic);
        Log.d(TAG, P + "收到请求 topic=" + env.topic + " from=" + env.source + " corr=" + env.correlationId);
        if (h == null) {
            // 本端未注册该 topic 的处理器：回 E_NO_PROVIDER，让请求方立即失败而非空等超时
            Log.w(TAG, P + "收到无处理器的请求 topic=" + env.topic + " from=" + env.source + "，回 E_NO_PROVIDER");
            if (src != null)
                src.send(newEnv(BridgeEnvelope.TYPE_RESPONSE, env.topic, "{}", env.correlationId, BridgeErrors.E_NO_PROVIDER));
            return;
        }
        BridgeResponder resp = new BridgeResponder((code, payload) -> {
            if (src != null)
                src.send(newEnv(BridgeEnvelope.TYPE_RESPONSE, env.topic, payload, env.correlationId, code));
        });
        // onRequestAsync 注册的 topic 交独立线程池并行执行，不占 worker（消除慢 handler 队头阻塞）；
        // 其余在 worker 线程内联执行（适合快 handler）。
        if (asyncTopics.contains(env.topic)) {
            handlerPool().execute(() -> invokeHandler(h, env, resp));
        } else {
            // sync handler 在 worker 线程内联：计时，超阈值告警（误用 onRequest 处理耗时任务，会队头阻塞后续入站）。
            // 只测 handle() 同步占用 worker 的时长——异步回包的 handler 此处秒返回，不会误报。
            long t0 = SystemClock.uptimeMillis();
            invokeHandler(h, env, resp);
            long cost = SystemClock.uptimeMillis() - t0;
            if (cost >= SLOW_SYNC_HANDLER_WARN_MS) {
                Log.w(TAG, P + "onRequest handler 同步耗时 " + cost + "ms 占用单线程 worker（阈值 "
                        + SLOW_SYNC_HANDLER_WARN_MS + "ms），topic=" + env.topic
                        + "，会队头阻塞后续请求/事件；建议改用 Bridge.onRequestAsync(\"" + env.topic
                        + "\", ...) 或在 handler 内自行异步回包");
            }
        }
    }

    /** 执行 handler 并隔离异常（异常 → 回 E_INTERNAL）。worker 内联（sync）与线程池（async）两条路共用。 */
    private void invokeHandler(RequestHandler h, BridgeEnvelope env, BridgeResponder resp) {
        try {
            h.handle(new BridgeRequest(env.payload), resp);
        } catch (Exception e) {
            Log.w(TAG, P + "handler 异常 topic=" + env.topic + " " + e);
            resp.fail(BridgeErrors.E_INTERNAL, "提供方处理异常");
        }
    }

    private void handleResponse(BridgeEnvelope env) {
        // 按 correlationId 唤醒在途请求；OK 透传 payload，非 OK 提取错误 msg
        Log.d(TAG, P + "收到响应 corr=" + env.correlationId + " code=" + env.code + " from=" + env.source);
        if (env.code == BridgeErrors.OK) {
            rpc.complete(env.correlationId, BridgeErrors.OK, env.payload);
        } else {
            rpc.complete(env.correlationId, env.code, extractMsg(env.payload));
        }
    }

    private void handleEvent(BridgeEnvelope env) {
        // 遍历本端订阅表，分发给所有匹配（精确或整模块通配）的监听者；单个监听异常隔离，不影响其它
        int hit = 0;
        for (Sub s : subs) {
            if (s.matches(env.topic)) {
                hit++;
                try {
                    s.listener.onEvent(env.topic, env.payload);
                } catch (Exception e) {
                    Log.w(TAG, P + "listener 异常 topic=" + env.topic + " " + e);
                }
            }
        }
        Log.d(TAG, P + "收到事件 topic=" + env.topic + " from=" + env.source + " 命中订阅数=" + hit);
    }

    private void onAttach(IBridgeNode peer, String peerId) {
        if (peerId == null) return;
        // 对端主动把它的回调通道交给本端（纯客户端形态据此感知到对方），登记后回握
        PeerConnection pc = connections.peers.get(peerId);
        if (pc == null) {
            pc = new PeerConnection(peerId, peer);
            connections.peers.put(peerId, pc);
            linkDeath(pc);
            Log.i(TAG, P + "对端 attach 接入 " + peerId);
        } else {
            pc.remote = peer;   // 对端重连：刷新远端引用
            Log.i(TAG, P + "对端 attach 刷新 " + peerId);
        }
        sendHelloTo(pc);   // 回握，让对端知道本端 provide/subscribe
    }

    // ───────────────────────── 对外 API（经统一门面 Bridge）─────────────────────────

    /** 提供方：声明自身模块（不连接外部，契约版本未知=0）。 */
    void register(String module) {
        registerModule(module, 0, null);
    }

    /** 消费方：注入依赖节点 —— 连接 + 模块注册 +（可选）就绪回调。 */
    void register(ServiceNode node, ModuleCallback cb) {
        if (node == null) return;
        if (!node.modules.isEmpty()) nodeModules.put(node.pkg, node.modules);  // onConnected 归属
        for (String module : node.modules) registerModule(module, node.contractVersion, cb);
        connections.connect(node);   // self 跳过 + 去重在 connect 内
    }

    private void registerModule(String module, int contractVersion, ModuleCallback cb) {
        if (module == null) return;
        ModuleState st = moduleStates.computeIfAbsent(module, m -> new ModuleState(m, contractVersion));
        if (cb != null) st.callbacks.add(cb);
        Log.i(TAG, P + "注册模块 " + module + " 契约门面版本=" + contractVersion + " callback=" + (cb != null));
        worker.execute(() -> reevaluate(module));   // 提供方可能已就绪，补一次评估
    }

    boolean isReady(String module) {
        ModuleState st = moduleStates.get(module);
        return st != null && st.isReady();
    }

    /**
     * 该模块是否存在握手完成、provide 了其前缀 topic 的提供方。
     */
    private boolean evaluateReady(String module) {
        String prefix = module + ".";
        for (PeerConnection pc : connections.peers.values()) {
            for (String t : pc.providedTopics) if (t != null && t.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * worker 线程：推进单模块状态并触发回调/日志。
     */
    private void reevaluate(String module) {
        ModuleState st = moduleStates.get(module);
        if (st == null) return;
        switch (st.applyReadiness(evaluateReady(module))) {
            case READY:
                Log.i(TAG, P + "模块就绪 " + module);
                for (ModuleCallback cb : st.callbacks) safe(cb::onReady);
                break;
            case REBOOTED:
                Log.i(TAG, P + "模块提供方重启恢复 " + module);
                for (ModuleCallback cb : st.callbacks) safe(cb::onRebooted);
                break;
            case LOST:
                Log.w(TAG, P + "模块提供方已离线 " + module);
                break;
            default:
                break;
        }
    }

    private void reevaluateAll() {
        for (String m : moduleStates.keySet()) reevaluate(m);
    }

    /**
     * bind 成功（onServiceConnected）—— 对该节点提供的模块触发 onConnected（排查日志用）。
     */
    void onPeerConnected(String nodeId) {
        worker.execute(() -> {
            java.util.Set<String> mods = nodeModules.get(nodeId);
            if (mods == null) return;
            for (String m : mods) {
                ModuleState st = moduleStates.get(m);
                if (st != null) {
                    Log.i(TAG, P + "模块节点已连接 module=" + m + " node=" + nodeId);
                    for (ModuleCallback cb : st.callbacks) safe(cb::onConnected);
                }
            }
        });
    }

    /**
     * 连接断开（onServiceDisconnected）—— 移除、失败该对端在途请求并重算模块。
     */
    void onPeerLost(String nodeId) {
        worker.execute(() -> {
            connections.peers.remove(nodeId);
            rpc.failPeer(nodeId, BridgeErrors.E_NOT_CONNECTED, "对端已断开");  // 与 binderDied 路径对齐，避免在途请求空等超时
            reevaluateAll();
        });
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            Log.w(TAG, P + "模块回调异常 " + e);
        }
    }

    /** 提供方：注册某 request topic 的处理器。handler 在单线程 worker【串行】执行，适合快 handler；耗时请改用 onRequestAsync。 */
    void onRequest(String topic, RequestHandler handler) {
        handlers.put(topic, handler);
        Log.i(TAG, P + "注册请求处理器 topic=" + topic);
        broadcastHello();   // 能力变化，重新通告，订阅/请求方据此更新对端能力表
    }

    /**
     * 提供方：注册一个在【独立线程池并行执行】的 request 处理器。
     * 与 onRequest 的唯一区别：handler 不在单线程 worker 内联跑，而是交给 handlerPool——
     * 慢 handler（查库/IO/算路）不占 worker，不会队头阻塞其它请求。
     * 注意：async handler 之间并行，访问共享业务状态需自管并发；handler 内可直接同步 resp.ok。
     */
    void onRequestAsync(String topic, RequestHandler handler) {
        handlers.put(topic, handler);
        asyncTopics.add(topic);
        handlerPool();   // 懒建线程池，注册即就绪
        Log.i(TAG, P + "注册异步请求处理器 topic=" + topic + "（独立线程池执行）");
        broadcastHello();   // 能力变化，重新通告（async 对对端透明，一样进 provide 列表）
    }

    /** 异步 handler 线程池：double-checked 懒建，固定大小（≥2，默认 CPU 核数），daemon 线程。 */
    private ExecutorService handlerPool() {
        ExecutorService p = handlerPool;
        if (p == null) {
            synchronized (this) {
                p = handlerPool;
                if (p == null) {
                    int n = Math.max(2, Runtime.getRuntime().availableProcessors());
                    p = Executors.newFixedThreadPool(n, daemon("bridge-async-handler"));
                    handlerPool = p;
                    Log.i(TAG, P + "异步 handler 线程池已建，线程数=" + n);
                }
            }
        }
        return p;
    }

    /**
     * 单 topic 订阅（旧 API，回调无需 topic）。
     */
    void subscribe(String topic, EventListener listener) {
        if (topic == null || listener == null) return;
        subs.add(new Sub(topic, (t, p) -> listener.onEvent(p)));
        Log.i(TAG, P + "订阅事件 topic=" + topic);
        broadcastHello();   // 订阅变化，重新通告，发布方据此推送
    }

    /**
     * 批量订阅：多个精确 topic 或整模块通配（"module.*"）共用一个带 topic 的回调。
     */
    void subscribePatterns(String[] patterns, TopicEventListener listener) {
        if (patterns == null || listener == null) return;
        for (String p : patterns) if (p != null && !p.isEmpty()) subs.add(new Sub(p, listener));
        Log.i(TAG, P + "批量订阅事件 patterns=" + java.util.Arrays.toString(patterns));
        broadcastHello();
    }

    void publish(String topic, String payload) {
        // 只推给「声明订阅了该 topic」的对端（精确或整模块通配），避免无谓投递
        BridgeEnvelope env = newEnv(BridgeEnvelope.TYPE_EVENT, topic, payload, null, 0);
        int delivered = 0;
        for (PeerConnection pc : connections.peers.values()) {
            if (pc.subscribes(topic) && pc.send(env)) delivered++;
        }
        Log.d(TAG, P + "发布事件 topic=" + topic + " 推送对端数=" + delivered);
    }

    void request(String topic, String payload, BridgeReply reply, long timeoutMs) {
        PeerConnection provider = findProvider(topic);
        if (provider == null) {
            String module = Topics.moduleOf(topic);
            Log.w(TAG, P + "request 调用时模块未就绪 topic=" + topic + " module=" + module
                    + " isReady=" + isReady(module)
                    + "，当前无提供方，返回 E_NO_PROVIDER；建议等 onReady 回调后再调用");
            reply.onError(BridgeErrors.E_NO_PROVIDER, "无节点提供 " + topic);
            return;
        }
        String corr = UUID.randomUUID().toString();
        rpc.register(corr, provider.peerId, reply, timeoutMs);
        boolean sent = provider.send(newEnv(BridgeEnvelope.TYPE_REQUEST, topic, payload, corr, 0));
        Log.d(TAG, P + "发起请求 topic=" + topic + " -> " + provider.peerId + " corr=" + corr
                + " timeoutMs=" + timeoutMs + " sent=" + sent);
        if (!sent) rpc.complete(corr, BridgeErrors.E_NOT_CONNECTED, "投递失败，对端通道已断");
    }

    BridgeResult requestSync(String topic, String payload, long timeoutMs) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("requestSync 禁止在主线程调用，会阻塞导致 ANR；请用异步 request");
        }
        final CountDownLatch latch = new CountDownLatch(1);
        final BridgeResult[] box = new BridgeResult[1];
        request(topic, payload, new BridgeReply() {
            @Override
            public void onSuccess(String p) {
                box[0] = new BridgeResult(BridgeErrors.OK, p, null);
                latch.countDown();
            }

            @Override
            public void onError(int c, String m) {
                box[0] = new BridgeResult(c, null, m);
                latch.countDown();
            }
        }, timeoutMs);
        try {
            latch.await(timeoutMs + 500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return box[0] != null ? box[0] : new BridgeResult(BridgeErrors.E_TIMEOUT, null, "请求超时");
    }

    // ───────────────────────── 内部工具 ─────────────────────────

    private PeerConnection findProvider(String topic) {
        for (PeerConnection pc : connections.peers.values()) {
            if (pc.provides(topic)) return pc;
        }
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
        } catch (Exception ignore) {
        }
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
        try {
            return new JSONObject(payload).optString("msg", payload);
        } catch (Exception e) {
            return payload;
        }
    }

    /**
     * 一条订阅：精确 topic，或以 "module.*" 表示的整模块前缀通配。
     */
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
