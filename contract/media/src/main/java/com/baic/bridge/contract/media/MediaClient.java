package com.baic.bridge.contract.media;

import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;
import com.baic.bridge.core.EventListener;

import org.json.JSONObject;

/** 多媒体消费方门面（可选）：把 media.* topic 封成强类型方法。 */
public final class MediaClient {

    public static void play(String trackId, BridgeReply reply, long timeoutMs) {
        String payload;
        try { payload = new JSONObject().put(MediaSchema.K_TRACK_ID, trackId == null ? "" : trackId).toString(); }
        catch (Exception e) { payload = "{}"; }
        Bridge.request(MediaSchema.PLAY, payload, reply, timeoutMs);
    }

    public static void pause(BridgeReply reply, long timeoutMs) {
        Bridge.request(MediaSchema.PAUSE, "{}", reply, timeoutMs);
    }

    public static void next(BridgeReply reply, long timeoutMs) {
        Bridge.request(MediaSchema.NEXT, "{}", reply, timeoutMs);
    }

    /** 设置音量（0–100）。 */
    public static void setVolume(int volume, BridgeReply reply, long timeoutMs) {
        String payload;
        try { payload = new JSONObject().put(MediaSchema.K_VOLUME, volume).toString(); }
        catch (Exception e) { payload = "{}"; }
        Bridge.request(MediaSchema.SET_VOLUME, payload, reply, timeoutMs);
    }

    /** 订阅播放状态变化。 */
    public static void subscribeState(EventListener listener) {
        Bridge.subscribe(MediaSchema.STATE, listener);
    }

    private MediaClient() {}
}
