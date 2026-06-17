package com.baic.bridge.core;

import android.content.Context;
import android.os.IBinder;

/**
 * Bridge SDK 统一门面（静态）。业务方只跟这个类打交道。
 *
 * 全量形态：{@link #init} 后由 core:full 的 BridgeNodeService 暴露 {@link #nodeBinder()}。
 * lite 纯客户端：{@link #initLite} 只起客户端，主动连清单节点并 attach 回调。
 * lite 外挂宿主：{@link #attachHost} 拿到 {@link BridgeNodeHost} 在已有 Service 暴露通道。
 */
public final class Bridge {

    private static volatile BridgeCore core;

    /** 全量形态初始化（配合 core:full 的托管 Service）。 */
    public static void init(Context ctx) { ensure(ctx); }

    /** lite 纯客户端初始化（无 Service，只主动连别人 + attach 回调）。 */
    public static void initLite(Context ctx) { ensure(ctx); }

    /** lite 外挂：把内核挂到宿主进程，返回宿主在已有 Service.onBind 里暴露通道用的 Host。 */
    public static BridgeNodeHost attachHost(Context ctx) { ensure(ctx); return new BridgeNodeHost(core); }

    /** 注册模块（声明关心该模块、打开收发开关）。 */
    public static void register(String module) { core().register(module); }

    /** 提供方：处理某个 request topic。 */
    public static void onRequest(String topic, RequestHandler handler) { core().onRequest(topic, handler); }

    /** 消费方：订阅某个 event topic。 */
    public static void subscribe(String topic, EventListener listener) { core().subscribe(topic, listener); }

    /** 提供方：发布事件给所有订阅者。 */
    public static void publish(String topic, String payload) { core().publish(topic, payload); }

    /** 异步 RPC（推荐）。回调在 SDK worker 线程，更新 UI 请自行切主线程。 */
    public static void request(String topic, String payload, BridgeReply reply, long timeoutMs) {
        core().request(topic, payload, reply, timeoutMs);
    }

    /** 同步 RPC（仅工作线程；主线程调用抛 IllegalStateException）。 */
    public static BridgeResult requestSync(String topic, String payload, long timeoutMs) {
        return core().requestSync(topic, payload, timeoutMs);
    }

    /** core:full 的 BridgeNodeService.onBind 返回此 Binder。 */
    public static IBinder nodeBinder() { return core().localNode(); }

    private static void ensure(Context ctx) {
        if (core == null) {
            synchronized (Bridge.class) {
                if (core == null) {
                    BridgeCore c = new BridgeCore(ctx);
                    c.start();
                    core = c;
                }
            }
        }
    }

    private static BridgeCore core() {
        BridgeCore c = core;
        if (c == null) throw new IllegalStateException("Bridge 未初始化，请先调用 Bridge.init/initLite/attachHost");
        return c;
    }

    private Bridge() {}
}
