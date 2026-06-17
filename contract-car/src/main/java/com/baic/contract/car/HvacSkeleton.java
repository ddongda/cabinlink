package com.baic.contract.car;

import android.os.Bundle;

import com.baic.cabinlink.runtime.CapabilitySkeleton;
import com.baic.cabinlink.runtime.Pipe;

/**
 * Hvac 提供方骨架（手写模式样板 = 未来 APT 的输出形态）。
 * 业务团队继承本类，只实现 3 个抽象方法；推送/快照/订阅/线程全部继承自基类。
 */
public abstract class HvacSkeleton extends CapabilitySkeleton {

    @Override public final String capabilityId() { return HvacSchema.CAPABILITY_ID; }
    @Override public final int    version()      { return HvacSchema.VERSION; }

    // ── 业务方法（在能力级串行线程执行，免锁）────────────────
    public abstract void setAcPower(boolean on, ReplySink reply);
    public abstract void setTemperature(float celsius, ReplySink reply);

    // ── 给业务用的属性/事件快捷方法 ──────────────────────────
    protected final void setAcOnProp(boolean on)      { properties().set(HvacSchema.P_AC_ON, on); }
    protected final void setTemperatureProp(float t)  { properties().set(HvacSchema.P_TEMPERATURE, t); }
    protected final void setFanSpeedProp(int level)   { properties().set(HvacSchema.P_FAN_SPEED, level); }
    protected final void emitAlert(String message) {
        Bundle b = new Bundle(); b.putString(Pipe.K_VALUE, message);
        emit(HvacSchema.T_ALERT, b);
    }

    // ── op 分发（生成物形态）────────────────────────────────
    @Override protected final void onInvoke(int op, Bundle args, ReplySink reply) {
        switch (op) {
            case HvacSchema.OP_SET_AC_POWER:
                setAcPower(args.getBoolean(Pipe.K_VALUE, false), reply); break;
            case HvacSchema.OP_SET_TEMPERATURE:
                setTemperature(args.getFloat(Pipe.K_VALUE, 24f), reply); break;
            default:
                reply.fail(Pipe.E_UNKNOWN_OP, "op=" + op);
        }
    }
}
