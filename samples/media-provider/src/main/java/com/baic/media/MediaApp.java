package com.baic.media;

import android.app.Application;

import com.baic.bridge.contract.media.MediaSchema;
import com.baic.bridge.core.Bridge;

import org.json.JSONObject;

/**
 * 多媒体 App：全量形态。Bridge.init 后由 core:full 的 BridgeNodeService 托管被 bind。
 * 注册 media.* 的 request 处理器，并在状态变化时 publish media.state。
 */
public class MediaApp extends Application {

    private static volatile int playState = 0;     // 0 停 1 播
    private static volatile String title = "（无）";

    @Override
    public void onCreate() {
        super.onCreate();
        Bridge.init(this);
        Bridge.register(MediaSchema.MODULE);

        Bridge.onRequest(MediaSchema.PLAY, (req, resp) -> {
            String track = req.get(MediaSchema.K_TRACK_ID);
            playState = 1;
            title = "曲目 " + (track == null || track.isEmpty() ? "?" : track);
            publishState();
            resp.ok(stateJson());
        });
        Bridge.onRequest(MediaSchema.PAUSE, (req, resp) -> {
            playState = 0;
            publishState();
            resp.ok(stateJson());
        });
        Bridge.onRequest(MediaSchema.NEXT, (req, resp) -> {
            playState = 1;
            title = "下一曲";
            publishState();
            resp.ok(stateJson());
        });
    }

    static String stateJson() {
        try {
            return new JSONObject()
                    .put(MediaSchema.K_PLAY_STATE, playState)
                    .put(MediaSchema.K_TITLE, title)
                    .toString();
        } catch (Exception e) { return "{}"; }
    }

    private static void publishState() {
        Bridge.publish(MediaSchema.STATE, stateJson());
    }
}
