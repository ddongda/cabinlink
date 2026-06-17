package com.baic.contract.phone;

import com.baic.cabinlink.runtime.CabinLink;
import com.baic.cabinlink.runtime.CapabilityDescriptor;
import com.baic.cabinlink.runtime.PropertyMirror;
import com.baic.cabinlink.runtime.Reply;

/**
 * 电话 能力契约（消费方视角的全部 API）。
 *
 * 用法：
 *   CabinLink.of(ctx).require(Phone.DESCRIPTOR, phone -> {
 *       phone.dial("10086", r -> toast(r.isOk() ? "拨号中" : r.message));
 *       phone.answer(Reply.ignore());
 *       int st = phone.callState();                 // 读本地镜像，0 IPC
 *       phone.onCallState(s -> card.show(s));       // 订阅，首回调必为快照
 *       phone.onIncomingCall(num -> popup(num));    // 来电事件
 *   });
 */
public interface Phone {

    // ── Call ───────────────────────────────────────────────
    void answer(Reply<Void> reply);
    void hangup(Reply<Void> reply);
    void dial(String number, Reply<Void> reply);

    // ── Property（镜像读 0 IPC；isStale() 查新鲜度）─────────
    int     callState();   // 0空闲/1响铃/2通话中
    String  peerNumber();
    boolean isStale();

    void onCallState(java.util.function.Consumer<Integer> w);
    void onPeerNumber(java.util.function.Consumer<String> w);

    // ── Event ──────────────────────────────────────────────
    interface IncomingCallWatcher { void on(String number); }
    void onIncomingCall(IncomingCallWatcher w);

    interface CallEndedWatcher { void on(); }
    void onCallEnded(CallEndedWatcher w);

    /** 能力描述符（require 入口；生成期由 APT 产出） */
    CapabilityDescriptor<Phone> DESCRIPTOR = new CapabilityDescriptor<>(
            PhoneSchema.CAPABILITY_ID, PhoneSchema.VERSION,
            (pipe, link) -> new PhoneProxy());
}
