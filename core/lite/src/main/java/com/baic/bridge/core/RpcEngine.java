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
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, Pending> pending = new ConcurrentHashMap<>();

    RpcEngine(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private static final class Pending {
        final BridgeReply reply;
        final String peerId;
        final AtomicBoolean done = new AtomicBoolean(false);
        ScheduledFuture<?> timeoutFuture;

        Pending(BridgeReply reply, String peerId) {
            this.reply = reply;
            this.peerId = peerId;
        }
    }

    void register(String corr, String peerId, BridgeReply reply, long timeoutMs) {
        Pending p = new Pending(reply, peerId);
        pending.put(corr, p);
        p.timeoutFuture = scheduler.schedule(
                () -> complete(corr, BridgeErrors.E_TIMEOUT, "请求超时"),
                timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * code==OK → onSuccess(payloadOrMsg)；否则 onError(code, payloadOrMsg 作为 msg)。
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
     * 某对端断开时，只快速失败该对端的在途请求，避免悬挂，也不误伤其它对端。
     */
    void failPeer(String peerId, int code, String msg) {
        if (peerId == null) return;
        for (Map.Entry<String, Pending> e : pending.entrySet()) {
            if (peerId.equals(e.getValue().peerId)) complete(e.getKey(), code, msg);
        }
    }
}
