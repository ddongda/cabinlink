package com.baic.bridge.core;

/** 统一错误码（Bridge SDK §7.3）。0=OK；1–999 SDK 保留；≥1000 各模块在自己 contract 里定义。 */
public final class BridgeErrors {
    public static final int OK             = 0;
    public static final int E_TIMEOUT      = 1;  // 请求超时
    public static final int E_NO_PROVIDER  = 2;  // 无节点提供该 topic
    public static final int E_NOT_CONNECTED = 3; // 目标未连接
    public static final int E_VERSION      = 4;  // schemaVersion 不兼容
    public static final int E_ACL          = 5;  // 鉴权拒绝
    public static final int E_INTERNAL     = 6;  // 内部错误

    private BridgeErrors() {}
}
