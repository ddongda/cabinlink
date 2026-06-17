package com.baic.contract.phone;

import android.os.Bundle;

import com.baic.cabinlink.runtime.Pipe;
import com.baic.cabinlink.runtime.PipeProxy;
import com.baic.cabinlink.runtime.Reply;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Phone 消费方代理（手写模式样板 = 未来 APT 的输出形态）。
 * 全部样板已由 PipeProxy 托管，这里只剩"契约成员 → op/topic/propId"的映射。
 */
final class PhoneProxy extends PipeProxy implements Phone {

    PhoneProxy() {
        // 声明需要的属性与事件主题（attach 时自动订阅，reattach 自动重放）
        subscribe(Pipe.propTopic(PhoneSchema.P_CALL_STATE),
                  Pipe.propTopic(PhoneSchema.P_PEER_NUMBER),
                  PhoneSchema.T_INCOMING_CALL,
                  PhoneSchema.T_CALL_ENDED);
    }

    // ── Call ───────────────────────────────────────────────
    @Override public void answer(Reply<Void> reply) {
        call(PhoneSchema.OP_ANSWER, new Bundle(), asVoid(reply));
    }

    @Override public void hangup(Reply<Void> reply) {
        call(PhoneSchema.OP_HANGUP, new Bundle(), asVoid(reply));
    }

    @Override public void dial(String number, Reply<Void> reply) {
        Bundle args = new Bundle(); args.putString(Pipe.K_VALUE, number);
        call(PhoneSchema.OP_DIAL, args, asVoid(reply));
    }

    // ── Property ───────────────────────────────────────────
    @Override public int    callState()  { return mirror().getInt(PhoneSchema.P_CALL_STATE, 0); }
    @Override public String peerNumber() { return mirror().getString(PhoneSchema.P_PEER_NUMBER, ""); }
    @Override public boolean isStale()   { return mirror().isStale(); }

    @Override public void onCallState(Consumer<Integer> w) {
        mirror().watch((id, v) -> { if (id == PhoneSchema.P_CALL_STATE) w.accept((Integer) v); });
    }
    @Override public void onPeerNumber(Consumer<String> w) {
        mirror().watch((id, v) -> { if (id == PhoneSchema.P_PEER_NUMBER) w.accept((String) v); });
    }

    // ── Event ──────────────────────────────────────────────
    private final CopyOnWriteArrayList<IncomingCallWatcher> mIncomingWatchers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CallEndedWatcher>     mEndedWatchers    = new CopyOnWriteArrayList<>();
    @Override public void onIncomingCall(IncomingCallWatcher w) { mIncomingWatchers.add(w); }
    @Override public void onCallEnded(CallEndedWatcher w)       { mEndedWatchers.add(w); }

    @Override protected void onEvent(int topic, Bundle data) {   // 已在主线程
        switch (topic) {
            case PhoneSchema.T_INCOMING_CALL: {
                String num = data.getString(Pipe.K_VALUE, "");
                for (IncomingCallWatcher w : mIncomingWatchers) w.on(num);
                break;
            }
            case PhoneSchema.T_CALL_ENDED:
                for (CallEndedWatcher w : mEndedWatchers) w.on();
                break;
        }
    }

    private static Reply<Bundle> asVoid(Reply<Void> r) {
        return res -> { if (r != null) r.onResult(res.isOk()
                ? com.baic.cabinlink.runtime.LinkResult.ok(null)
                : com.baic.cabinlink.runtime.LinkResult.fail(res.code, res.message)); };
    }
}
