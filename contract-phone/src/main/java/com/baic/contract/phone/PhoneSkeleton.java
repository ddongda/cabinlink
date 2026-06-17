package com.baic.contract.phone;

import android.os.Bundle;

import com.baic.cabinlink.runtime.CapabilitySkeleton;
import com.baic.cabinlink.runtime.Pipe;

/**
 * Phone 提供方骨架（手写模式样板 = 未来 APT 的输出形态）。
 * 业务团队继承本类，只实现 3 个抽象方法；推送/快照/订阅/线程全部继承自基类。
 */
public abstract class PhoneSkeleton extends CapabilitySkeleton {

    @Override public final String capabilityId() { return PhoneSchema.CAPABILITY_ID; }
    @Override public final int    version()      { return PhoneSchema.VERSION; }

    // ── 业务方法（在能力级串行线程执行，免锁）────────────────
    public abstract void answer(ReplySink reply);
    public abstract void hangup(ReplySink reply);
    public abstract void dial(String number, ReplySink reply);

    // ── 给业务用的属性/事件快捷方法 ──────────────────────────
    protected final void setCallStateProp(int state)        { properties().set(PhoneSchema.P_CALL_STATE, state); }
    protected final void setPeerNumberProp(String number)   { properties().set(PhoneSchema.P_PEER_NUMBER, number); }
    protected final void emitIncomingCall(String number) {
        Bundle b = new Bundle(); b.putString(Pipe.K_VALUE, number);
        emit(PhoneSchema.T_INCOMING_CALL, b);
    }
    protected final void emitCallEnded() {
        emit(PhoneSchema.T_CALL_ENDED, new Bundle());
    }

    // ── op 分发（生成物形态）────────────────────────────────
    @Override protected final void onInvoke(int op, Bundle args, ReplySink reply) {
        switch (op) {
            case PhoneSchema.OP_ANSWER:
                answer(reply); break;
            case PhoneSchema.OP_HANGUP:
                hangup(reply); break;
            case PhoneSchema.OP_DIAL:
                dial(args.getString(Pipe.K_VALUE, ""), reply); break;
            default:
                reply.fail(Pipe.E_UNKNOWN_OP, "op=" + op);
        }
    }
}
