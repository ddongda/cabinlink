package com.baic.contract.media;

import com.baic.cabinlink.runtime.CapabilityDescriptor;
import com.baic.cabinlink.runtime.Reply;

/**
 * 多媒体 能力契约（消费方视角的全部 API）。
 *
 * 用法：
 *   CabinLink.of(ctx).require(Media.DESCRIPTOR, media -> {
 *       media.play(Reply.ignore());
 *       media.setVolume(60, r -> toast(r.isOk() ? "已设置" : r.message));
 *       int s = media.playState();                 // 读本地镜像，0 IPC
 *       media.onTitle(t -> card.show(t));           // 订阅，首回调必为快照
 *       media.onTrackChanged(t -> card.flash(t));   // 曲目切换事件
 *   });
 */
public interface Media {

    // ── Call ───────────────────────────────────────────────
    void play(Reply<Void> reply);
    void pause(Reply<Void> reply);
    void next(Reply<Void> reply);
    void prev(Reply<Void> reply);
    void setVolume(int volume, Reply<Void> reply);

    // ── Property（镜像读 0 IPC；isStale() 查新鲜度）─────────
    int    playState();
    String title();
    int    volume();
    boolean isStale();

    void onPlayState(java.util.function.IntConsumer w);
    interface TitleWatcher { void on(String title); }
    void onTitle(TitleWatcher w);
    void onVolume(java.util.function.IntConsumer w);

    // ── Event ──────────────────────────────────────────────
    interface TrackWatcher { void on(String title); }
    void onTrackChanged(TrackWatcher w);

    /** 能力描述符（require 入口；生成期由 APT 产出） */
    CapabilityDescriptor<Media> DESCRIPTOR = new CapabilityDescriptor<>(
            MediaSchema.CAPABILITY_ID, MediaSchema.VERSION,
            (pipe, link) -> new MediaProxy());
}
