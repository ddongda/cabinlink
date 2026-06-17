package com.baic.bridge.core;

/** 提供方处理某个 request topic 的回调。 */
public interface RequestHandler {
    void handle(BridgeRequest req, BridgeResponder resp);
}
