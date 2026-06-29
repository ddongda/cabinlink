package com.baic.bridge.contract.media;

/** 多媒体模块特殊错误码（分配区间 1000–1999，Bridge SDK §9.2）。 */
public final class MediaError {
    public static final int E_DEVICE = 1001; // 播放器异常
    public static final int E_PLAYER_BUSY = 1002; // 播放器忙（更名避免与 SDK BridgeErrors.E_BUSY=7 重名）
    public static final int E_SOURCE = 1003; // 片源不可用

    private MediaError() {}
}
