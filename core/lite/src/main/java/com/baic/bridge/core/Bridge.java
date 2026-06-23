package com.baic.bridge.core;

import android.content.Context;
import android.os.IBinder;

/**
 * Bridge SDK 统一门面（静态）。业务方只跟这个类打交道。
 *
 * 唯一初始化入口 {@link #init}。要对外暴露通道的 Service（core:full 自带的 BridgeNodeService，
 * 或宿主已有 Service）在 onBind 里返回 {@link #nodeBinder()} 即可。
 * 三种形态（全量自带 Service / 外挂宿主 Service / 纯客户端）的区别只在「依赖哪个 aar + 是否暴露 Service」，
 * 不在初始化方法——所以只有一个 init。
 */
public final class Bridge {

    private static volatile BridgeCore core;

    /**
     * 唯一初始化入口（在 Application.onCreate 调用）。三种形态都用它：
     * 全量（依赖 core:full，自带 Service）、外挂（宿主 Service.onBind 返回 nodeBinder()）、
     * 纯客户端（不暴露 Service，只主动连别人）。
     */
    public static void init(Context ctx) { ensure(ctx); }

    /** 注册模块（声明关心该模块、打开收发开关）。 */
    public static void register(String module) { core().register(module); }

    /** 提供方：处理某个 request topic。 */
    public static void onRequest(String topic, RequestHandler handler) { core().onRequest(topic, handler); }

    /** 消费方：订阅单个 event topic（回调无需 topic）。 */
    public static void subscribe(String topic, EventListener listener) { core().subscribe(topic, listener); }

    /**
     * 消费方：一次订阅多个 event topic，链式绑定回调（回调带 topic 以区分来源）。
     * 例：{@code Bridge.subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE).on((topic, payload) -> ...)}
     */
    public static BridgeSubscription subscribes(String... topics) {
        return new BridgeSubscription(core(), topics);
    }

    /**
     * 消费方：订阅某模块下的所有 event（按 "module." 前缀匹配），链式绑定回调。
     * 例：{@code Bridge.subscribeAll(UserCenterSchema.MODULE).on((topic, payload) -> ...)}
     */
    public static BridgeSubscription subscribeAll(String module) {
        return new BridgeSubscription(core(), new String[]{ module + ".*" });
    }

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
        if (c == null) throw new IllegalStateException("Bridge 未初始化，请先调用 Bridge.init(context)");
        return c;
    }

    private Bridge() {}
}
