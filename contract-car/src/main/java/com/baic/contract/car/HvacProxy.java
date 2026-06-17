package com.baic.contract.car;

import android.os.Bundle;

import com.baic.cabinlink.runtime.Pipe;
import com.baic.cabinlink.runtime.PipeProxy;
import com.baic.cabinlink.runtime.Reply;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Hvac 消费方代理（手写模式样板 = 未来 APT 的输出形态）。
 * 全部样板已由 PipeProxy 托管，这里只剩"契约成员 → op/topic/propId"的映射。
 */
final class HvacProxy extends PipeProxy implements Hvac {

    HvacProxy() {
        // 声明需要的属性与事件主题（attach 时自动订阅，reattach 自动重放）
        subscribe(Pipe.propTopic(HvacSchema.P_AC_ON),
                  Pipe.propTopic(HvacSchema.P_TEMPERATURE),
                  Pipe.propTopic(HvacSchema.P_FAN_SPEED),
                  HvacSchema.T_ALERT);
    }

    // ── Call ───────────────────────────────────────────────
    @Override public void setAcPower(boolean on, Reply<Void> reply) {
        Bundle args = new Bundle(); args.putBoolean(Pipe.K_VALUE, on);
        call(HvacSchema.OP_SET_AC_POWER, args, asVoid(reply));
    }

    @Override public void setTemperature(float celsius, Reply<Void> reply) {
        Bundle args = new Bundle(); args.putFloat(Pipe.K_VALUE, celsius);
        call(HvacSchema.OP_SET_TEMPERATURE, args, asVoid(reply));
    }

    // ── Property ───────────────────────────────────────────
    @Override public boolean acOn()        { return mirror().getBool(HvacSchema.P_AC_ON, false); }
    @Override public float   temperature() { return mirror().getFloat(HvacSchema.P_TEMPERATURE, 24f); }
    @Override public int     fanSpeed()    { return mirror().getInt(HvacSchema.P_FAN_SPEED, 0); }
    @Override public boolean isStale()     { return mirror().isStale(); }

    @Override public void onTemperature(FloatWatcher w) {
        mirror().watch((id, v) -> { if (id == HvacSchema.P_TEMPERATURE) w.on((Float) v); });
    }
    @Override public void onAcOn(Consumer<Boolean> w) {
        mirror().watch((id, v) -> { if (id == HvacSchema.P_AC_ON) w.accept((Boolean) v); });
    }

    // ── Event ──────────────────────────────────────────────
    private final CopyOnWriteArrayList<AlertWatcher> mAlertWatchers = new CopyOnWriteArrayList<>();
    @Override public void onAlert(AlertWatcher w) { mAlertWatchers.add(w); }

    @Override protected void onEvent(int topic, Bundle data) {   // 已在主线程
        if (topic == HvacSchema.T_ALERT) {
            String msg = data.getString(Pipe.K_VALUE, "");
            for (AlertWatcher w : mAlertWatchers) w.on(msg);
        }
    }

    private static Reply<Bundle> asVoid(Reply<Void> r) {
        return res -> { if (r != null) r.onResult(res.isOk()
                ? com.baic.cabinlink.runtime.LinkResult.ok(null)
                : com.baic.cabinlink.runtime.LinkResult.fail(res.code, res.message)); };
    }
}
