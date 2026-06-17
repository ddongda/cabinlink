package com.baic.bridge.core;

/** requestSync 的同步返回值（仅工作线程可用，见 Bridge.requestSync）。 */
public final class BridgeResult {
    private final int code;
    private final String payload;
    private final String msg;

    public BridgeResult(int code, String payload, String msg) {
        this.code = code; this.payload = payload; this.msg = msg;
    }
    public boolean isOk()   { return code == BridgeErrors.OK; }
    public int code()       { return code; }
    public String payload() { return payload; }
    public String msg()     { return msg; }
}
