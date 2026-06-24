package com.baic.bridge.core;

/**
 * 初始化后的链式配置句柄，由 {@link Bridge#init} 返回。把「注册模块 / 注册处理器 / 订阅」
 * 串成一条链，提升接入可读性。各方法委托给对应的 {@link Bridge} 静态方法，二者等价、可混用。
 *
 * <pre>
 * Bridge.init(this)
 *       .register(UserCenterContract.NODE, cb)
 *       .register(MediaContract.NODE, cb)
 *       .subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
 *       .on((topic, payload) -> { ... });
 * </pre>
 */
public final class BridgeSetup {

    /** 无状态单例：实际状态都在 BridgeCore，本句柄只做链式转发。 */
    static final BridgeSetup INSTANCE = new BridgeSetup();

    private BridgeSetup() {}

    /** 提供方：声明自身模块（不连接外部）。 */
    public BridgeSetup register(String module) {
        Bridge.register(module);
        return this;
    }

    /** 消费方：注入依赖节点（XxxContract.NODE）—— 连接 + 模块注册。 */
    public BridgeSetup register(ServiceNode node) {
        Bridge.register(node);
        return this;
    }

    /** 消费方：注入依赖节点并监听状态（onConnected/onReady/onRebooted）。 */
    public BridgeSetup register(ServiceNode node, ModuleCallback callback) {
        Bridge.register(node, callback);
        return this;
    }

    /** 提供方：注册某个 request topic 的处理器。 */
    public BridgeSetup onRequest(String topic, RequestHandler handler) {
        Bridge.onRequest(topic, handler);
        return this;
    }

    /** 消费方：订阅单个 event topic（回调无需 topic）。 */
    public BridgeSetup subscribe(String topic, EventListener listener) {
        Bridge.subscribe(topic, listener);
        return this;
    }

    /** 消费方：一次订阅多个 event topic，返回订阅句柄，调用 {@code .on(...)} 绑定回调后生效。 */
    public BridgeSubscription subscribes(String... topics) {
        return Bridge.subscribes(topics);
    }

    /** 消费方：订阅某模块下全部 event（"module." 前缀），调用 {@code .on(...)} 绑定回调后生效。 */
    public BridgeSubscription subscribeAll(String module) {
        return Bridge.subscribeAll(module);
    }
}
