package com.baic.bridge.core;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 提供方 onRequest 的应答器。ok / fail 只会真正生效一次（一次性语义，AtomicBoolean CAS，CabinLink 铁律）。
 * 重复调用静默忽略，避免 handler 误回多次造成乱序。
 */
public final class BridgeResponder {

    /** 由 BridgeCore 注入：把应答封成 RESPONSE 信封投回请求方。 */
    public interface Sink { void send(int code, String payload); }

    private final AtomicBoolean done = new AtomicBoolean(false);
    private final Sink sink;

    public BridgeResponder(Sink sink) { this.sink = sink; }

    public void ok(String payload) {
        if (done.compareAndSet(false, true)) sink.send(BridgeErrors.OK, payload == null ? "{}" : payload);
    }

    public void fail(int code, String msg) {
        if (done.compareAndSet(false, true)) {
            String p;
            try { p = new JSONObject().put("msg", msg == null ? "" : msg).toString(); }
            catch (Exception e) { p = "{}"; }
            sink.send(code, p);
        }
    }
}
