package com.baic.bridge;

/** 提供方处理某个 topic 的请求，通过 Responder 应答。 */
public interface RequestHandler {
    void handle(String payload, Responder resp);
}
