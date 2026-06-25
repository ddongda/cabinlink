package com.baic.bridge.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RPC 引擎：按 correlationId 关联 request→response，每个 pending 带超时计时器。
 * 回调一次性语义用 AtomicBoolean CAS（CabinLink 铁律），杜绝超时与正常响应竞态导致的双回调。
 * 每个 pending 记录所属对端 peerId，断开时只失败该对端的在途请求（精准，不误伤其它 peer）。
 */
final class RpcEngine {
    /** 共享调度线程池：仅用于挂超时计时器（到点回 E_TIMEOUT），与投递/分发复用同一线程资源。 */
    private final ScheduledExecutorService scheduler;
    /**
     * 在途请求表：correlationId → Pending。
     * <p>response/timeout/对端断开三条路径会并发访问，故用 ConcurrentHashMap（铁律：跨线程集合并发安全）。
     */
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

    RpcEngine(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    /** 一次在途请求的上下文：回调 + 归属对端 + 一次性闸门 + 超时句柄。 */
    private static final class Pending {
        /** 业务回调；只允许被触发一次（成功 xor 失败 xor 超时）。 */
        final BridgeReply reply;
        /** 请求发往的对端包名；对端断开时据此精准失败本请求，不误伤其它 peer 的在途请求。 */
        final String peerId;
        /** 一次性闸门：CAS 抢占，保证「正常响应」与「超时」竞态下回调只触发一次（铁律：回调一次性语义）。 */
        final AtomicBoolean done = new AtomicBoolean(false);
        /** 超时计时器句柄；正常完成时 cancel，避免到点再触发一次空回调。 */
        ScheduledFuture<?> timeoutFuture;

        Pending(BridgeReply reply, String peerId) {
            this.reply = reply;
            this.peerId = peerId;
        }
    }

    /**
     * 登记一条在途请求并挂超时计时器。
     * 由调用线程在 deliver 成功投递后调用：先入表，再 schedule 超时——到点未收到响应即回 {@link BridgeErrors#E_TIMEOUT}。
     *
     * @param corr      关联 id（correlationId），response 凭它回到本请求
     * @param peerId    目标对端包名，用于断开时精准失败
     * @param reply     业务回调
     * @param timeoutMs 超时毫秒数
     */
    void register(String corr, String peerId, BridgeReply reply, long timeoutMs) {
        Pending p = new Pending(reply, peerId);
        pending.put(corr, p);
        p.timeoutFuture = scheduler.schedule(
                () -> complete(corr, BridgeErrors.E_TIMEOUT, "请求超时"),
                timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 完成（终结）一条在途请求——响应到达、超时、对端断开三条路径的统一收口。
     * <p>先从表移除并 CAS 抢占 done 闸门：抢到才回调（取消超时计时器后），
     * {@code code==OK} 回 {@link BridgeReply#onSuccess}，否则回 {@link BridgeReply#onError}；
     * 抢不到（已被其它路径终结）则直接返回，杜绝双回调。对未知/已终结 corr 是幂等空操作。
     *
     * @param corr         关联 id
     * @param code         结果码，{@link BridgeErrors#OK} 表示成功
     * @param payloadOrMsg 成功时为响应 payload，失败时作为错误 msg
     */
    void complete(String corr, int code, String payloadOrMsg) {
        Pending p = pending.remove(corr);
        if (p == null) return;
        if (!p.done.compareAndSet(false, true)) return;
        if (p.timeoutFuture != null) p.timeoutFuture.cancel(false);
        if (code == BridgeErrors.OK) p.reply.onSuccess(payloadOrMsg);
        else p.reply.onError(code, payloadOrMsg);
    }

    /**
     * 某对端断开（binderDied / onServiceDisconnected）时，快速失败发往该对端的所有在途请求，
     * 避免它们空等到超时才报错；按 peerId 精准匹配，不误伤其它对端的在途请求。
     *
     * @param peerId 断开的对端包名；为 null 直接返回（无归属信息，不处理）
     * @param code   失败结果码（通常 {@link BridgeErrors#E_NOT_CONNECTED}）
     * @param msg    失败说明
     */
    void failPeer(String peerId, int code, String msg) {
        if (peerId == null) return;
        for (Map.Entry<String, Pending> e : pending.entrySet()) {
            if (peerId.equals(e.getValue().peerId)) complete(e.getKey(), code, msg);
        }
    }
}
