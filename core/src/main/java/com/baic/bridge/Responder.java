package com.baic.bridge;

import java.util.concurrent.atomic.AtomicBoolean;

/** 应答器：ok / fail 只会真正生效一次（防 handler 误回多次造成乱序）。 */
public final class Responder {

    interface Sink { void send(int code, String payload); }

    private final AtomicBoolean done = new AtomicBoolean(false);
    private final Sink sink;

    Responder(Sink sink) { this.sink = sink; }

    public void ok(String payload) {
        if (done.compareAndSet(false, true)) sink.send(0, payload == null ? "{}" : payload);
    }

    public void fail(int code, String msg) {
        if (done.compareAndSet(false, true)) sink.send(code, msg == null ? "" : msg);
    }
}
