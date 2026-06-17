package com.baic.contract.car;

import com.baic.cabinlink.runtime.CabinLink;
import com.baic.cabinlink.runtime.CapabilityDescriptor;
import com.baic.cabinlink.runtime.PropertyMirror;
import com.baic.cabinlink.runtime.Reply;

/**
 * 车控-空调 能力契约（消费方视角的全部 API）。
 *
 * 用法：
 *   CabinLink.of(ctx).require(Hvac.DESCRIPTOR, hvac -> {
 *       hvac.setAcPower(true, Reply.ignore());
 *       hvac.setTemperature(24f, r -> toast(r.isOk() ? "已设置" : r.message));
 *       float t = hvac.temperature();              // 读本地镜像，0 IPC
 *       hvac.onTemperature(v -> card.show(v));     // 订阅，首回调必为快照
 *   });
 */
public interface Hvac {

    // ── Call ───────────────────────────────────────────────
    void setAcPower(boolean on, Reply<Void> reply);
    void setTemperature(float celsius, Reply<Void> reply);

    // ── Property（镜像读 0 IPC；isStale() 查新鲜度）─────────
    boolean acOn();
    float   temperature();
    int     fanSpeed();
    boolean isStale();

    interface FloatWatcher { void on(float v); }
    void onTemperature(FloatWatcher w);
    void onAcOn(java.util.function.Consumer<Boolean> w);

    // ── Event ──────────────────────────────────────────────
    interface AlertWatcher { void on(String message); }
    void onAlert(AlertWatcher w);

    /** 能力描述符（require 入口；生成期由 APT 产出） */
    CapabilityDescriptor<Hvac> DESCRIPTOR = new CapabilityDescriptor<>(
            HvacSchema.CAPABILITY_ID, HvacSchema.VERSION,
            (pipe, link) -> new HvacProxy());
}
