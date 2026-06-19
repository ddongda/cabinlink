package com.baic.bridge;

/** 请求回执。onSuccess / onError 只会被回调一次。 */
public interface Reply {
    void onSuccess(String payload);
    void onError(int code, String msg);
}
