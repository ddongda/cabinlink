package com.baic.contract.navi;

import com.baic.cabinlink.runtime.CapabilityDescriptor;
import com.baic.cabinlink.runtime.Reply;

import java.util.function.IntConsumer;

/**
 * 导航 能力契约（消费方视角的全部 API）。
 *
 * 用法：
 *   CabinLink.of(ctx).require(Navi.DESCRIPTOR, navi -> {
 *       navi.navigateTo("北汽蓝谷", Reply.ignore());     // 按 POI 发起导航
 *       navi.navigateTo(39.9, 116.4, r -> ...);          // 按坐标发起导航
 *       int s = navi.naviState();                         // 读本地镜像，0 IPC
 *       navi.onTurnHint(t -> hud.show(t));                // 转向提示
 *       navi.onArrived(() -> toast("已到达"));            // 到达事件
 *   });
 */
public interface Navi {

    // ── Call ───────────────────────────────────────────────
    void navigateTo(String poi, Reply<Void> reply);
    void navigateTo(double lat, double lng, Reply<Void> reply);
    void cancel(Reply<Void> reply);

    // ── Property（镜像读 0 IPC；isStale() 查新鲜度）─────────
    int     naviState();      // 0空闲/1导航中
    int     etaSeconds();
    int     remainMeters();
    boolean isStale();

    void onNaviState(IntConsumer w);
    void onEtaSeconds(IntConsumer w);
    void onRemainMeters(IntConsumer w);

    // ── Event ──────────────────────────────────────────────
    interface TurnHintWatcher { void on(String hint); }
    void onTurnHint(TurnHintWatcher w);

    interface ArrivedWatcher { void on(); }
    void onArrived(ArrivedWatcher w);

    /** 能力描述符（require 入口；生成期由 APT 产出） */
    CapabilityDescriptor<Navi> DESCRIPTOR = new CapabilityDescriptor<>(
            NaviSchema.CAPABILITY_ID, NaviSchema.VERSION,
            (pipe, link) -> new NaviProxy());
}
