package com.baic.bridge.core;

/**
 * 带 topic 的事件回调：用于一次订阅多个 topic（{@code Bridge.subscribes}）或整模块订阅
 * （{@code Bridge.subscribeAll}）的场景——同一回调收到不同 topic 的事件，靠 topic 区分。
 * 单 topic 订阅仍用 {@link EventListener}（无需 topic）。回调在 SDK worker 线程，更新 UI 自行切主线程。
 */
public interface TopicEventListener {
    void onEvent(String topic, String payload);
}
