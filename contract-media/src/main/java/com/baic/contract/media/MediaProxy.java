package com.baic.contract.media;

import android.os.Bundle;

import com.baic.cabinlink.runtime.Pipe;
import com.baic.cabinlink.runtime.PipeProxy;
import com.baic.cabinlink.runtime.Reply;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * Media 消费方代理（手写模式样板 = 未来 APT 的输出形态）。
 * 全部样板已由 PipeProxy 托管，这里只剩"契约成员 → op/topic/propId"的映射。
 */
final class MediaProxy extends PipeProxy implements Media {

    MediaProxy() {
        // 声明需要的属性与事件主题（attach 时自动订阅，reattach 自动重放）
        subscribe(Pipe.propTopic(MediaSchema.P_PLAY_STATE),
                  Pipe.propTopic(MediaSchema.P_TITLE),
                  Pipe.propTopic(MediaSchema.P_VOLUME),
                  MediaSchema.T_TRACK_CHANGED);
    }

    // ── Call ───────────────────────────────────────────────
    @Override public void play(Reply<Void> reply)  { call(MediaSchema.OP_PLAY,  new Bundle(), asVoid(reply)); }
    @Override public void pause(Reply<Void> reply) { call(MediaSchema.OP_PAUSE, new Bundle(), asVoid(reply)); }
    @Override public void next(Reply<Void> reply)  { call(MediaSchema.OP_NEXT,  new Bundle(), asVoid(reply)); }
    @Override public void prev(Reply<Void> reply)  { call(MediaSchema.OP_PREV,  new Bundle(), asVoid(reply)); }

    @Override public void setVolume(int volume, Reply<Void> reply) {
        Bundle args = new Bundle(); args.putInt(Pipe.K_VALUE, volume);
        call(MediaSchema.OP_SET_VOLUME, args, asVoid(reply));
    }

    // ── Property ───────────────────────────────────────────
    @Override public int    playState() { return mirror().getInt(MediaSchema.P_PLAY_STATE, 0); }
    @Override public String title()     { return mirror().getString(MediaSchema.P_TITLE, ""); }
    @Override public int    volume()    { return mirror().getInt(MediaSchema.P_VOLUME, 0); }
    @Override public boolean isStale()  { return mirror().isStale(); }

    @Override public void onPlayState(IntConsumer w) {
        mirror().watch((id, v) -> { if (id == MediaSchema.P_PLAY_STATE) w.accept((Integer) v); });
    }
    @Override public void onTitle(TitleWatcher w) {
        mirror().watch((id, v) -> { if (id == MediaSchema.P_TITLE) w.on((String) v); });
    }
    @Override public void onVolume(IntConsumer w) {
        mirror().watch((id, v) -> { if (id == MediaSchema.P_VOLUME) w.accept((Integer) v); });
    }

    // ── Event ──────────────────────────────────────────────
    private final CopyOnWriteArrayList<TrackWatcher> mTrackWatchers = new CopyOnWriteArrayList<>();
    @Override public void onTrackChanged(TrackWatcher w) { mTrackWatchers.add(w); }

    @Override protected void onEvent(int topic, Bundle data) {   // 已在主线程
        if (topic == MediaSchema.T_TRACK_CHANGED) {
            String title = data.getString(Pipe.K_VALUE, "");
            for (TrackWatcher w : mTrackWatchers) w.on(title);
        }
    }

    private static Reply<Bundle> asVoid(Reply<Void> r) {
        return res -> { if (r != null) r.onResult(res.isOk()
                ? com.baic.cabinlink.runtime.LinkResult.ok(null)
                : com.baic.cabinlink.runtime.LinkResult.fail(res.code, res.message)); };
    }
}
