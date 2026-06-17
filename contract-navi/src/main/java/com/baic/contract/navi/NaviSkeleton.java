package com.baic.contract.navi;

import android.os.Bundle;

import com.baic.cabinlink.runtime.CapabilitySkeleton;
import com.baic.cabinlink.runtime.Pipe;

/**
 * Navi 提供方骨架（手写模式样板 = 未来 APT 的输出形态）。
 * 业务团队继承本类，只实现抽象方法；推送/快照/订阅/线程全部继承自基类。
 */
public abstract class NaviSkeleton extends CapabilitySkeleton {

    @Override public final String capabilityId() { return NaviSchema.CAPABILITY_ID; }
    @Override public final int    version()      { return NaviSchema.VERSION; }

    // ── 业务方法（在能力级串行线程执行，免锁）────────────────
    public abstract void navigateToPoi(String poi, ReplySink reply);
    public abstract void navigateToCoord(double lat, double lng, ReplySink reply);
    public abstract void cancel(ReplySink reply);

    // ── 给业务用的属性/事件快捷方法 ──────────────────────────
    protected final void setNaviStateProp(int state)     { properties().set(NaviSchema.P_NAVI_STATE, state); }
    protected final void setEtaSecondsProp(int seconds)  { properties().set(NaviSchema.P_ETA_SECONDS, seconds); }
    protected final void setRemainMetersProp(int meters) { properties().set(NaviSchema.P_REMAIN_METERS, meters); }

    protected final void emitTurnHint(String hint) {
        Bundle b = new Bundle(); b.putString(Pipe.K_VALUE, hint);
        emit(NaviSchema.T_TURN_HINT, b);
    }
    protected final void emitArrived() {
        emit(NaviSchema.T_ARRIVED, new Bundle());   // 到达事件无载荷
    }

    // ── op 分发（生成物形态）────────────────────────────────
    @Override protected final void onInvoke(int op, Bundle args, ReplySink reply) {
        switch (op) {
            case NaviSchema.OP_NAVIGATE_POI:
                navigateToPoi(args.getString(Pipe.K_VALUE, ""), reply); break;
            case NaviSchema.OP_NAVIGATE_COORD:
                navigateToCoord(args.getDouble(NaviSchema.K_LAT, 0d),
                                args.getDouble(NaviSchema.K_LNG, 0d), reply); break;
            case NaviSchema.OP_CANCEL:
                cancel(reply); break;
            default:
                reply.fail(Pipe.E_UNKNOWN_OP, "op=" + op);
        }
    }
}
