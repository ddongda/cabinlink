package com.baic.bridge.core;

/** RPC 异步回执。onSuccess / onError 二者只会被回调一次（一次性语义，内部 CAS 保证）。 */
public interface BridgeReply {
    void onSuccess(String payload);
    void onError(int code, String msg);
}
