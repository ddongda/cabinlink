package com.baic.bridge.core;

/** 消费方订阅某个 event topic 的回调（payload 为 JSON 串）。 */
public interface EventListener {
    void onEvent(String payload);
}
