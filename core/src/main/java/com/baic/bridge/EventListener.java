package com.baic.bridge;

/** 事件订阅回调（payload 为 JSON 字符串）。 */
public interface EventListener {
    void onEvent(String payload);
}
