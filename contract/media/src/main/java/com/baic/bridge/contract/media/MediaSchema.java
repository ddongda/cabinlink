package com.baic.bridge.contract.media;

/** 多媒体契约（Bridge SDK §9）。验证 RPC 语义：play/pause/next/setVolume + state 事件。 */
public final class MediaSchema {
    public static final String MODULE = "media";
    public static final int    VERSION = 1;

    // request topics（多媒体 provider 处理）
    public static final String PLAY       = "media.play";
    public static final String PAUSE      = "media.pause";
    public static final String NEXT       = "media.next";
    public static final String SET_VOLUME = "media.setVolume";

    // event topic（多媒体 provider 发布）
    public static final String STATE      = "media.state";

    // payload 字段常量
    public static final String K_TRACK_ID   = "trackId";
    public static final String K_VOLUME     = "volume";
    public static final String K_PLAY_STATE = "playState"; // 0 停 1 播
    public static final String K_TITLE      = "title";

    private MediaSchema() {}
}
