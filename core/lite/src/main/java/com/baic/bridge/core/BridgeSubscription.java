package com.baic.bridge.core;

/**
 * 批量订阅的链式句柄，由 {@link Bridge#subscribes} / {@link Bridge#subscribeAll} 返回，
 * 调用 {@link #on} 绑定回调后才真正生效。
 *
 * <pre>
 * Bridge.subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
 *       .on((topic, payload) -> { ... });          // 多个精确 topic 共用一个回调
 *
 * Bridge.subscribeAll(UserCenterSchema.MODULE)
 *       .on((topic, payload) -> { ... });          // 该模块下所有 event（前缀匹配）
 * </pre>
 */
public final class BridgeSubscription {

    private final BridgeCore core;
    private final String[] patterns;   // 精确 topic，或整模块通配 "module.*"

    BridgeSubscription(BridgeCore core, String[] patterns) {
        this.core = core;
        this.patterns = patterns;
    }

    /**
     * 绑定回调，完成订阅。回调在 SDK worker 线程。
     */
    public void on(TopicEventListener listener) {
        core.subscribePatterns(patterns, listener);
    }
}
