package com.baic.cabinlink.runtime;

/** Call 的统一结果（吸收 IMapAPICallback 的 onSuccess/onFail，合一为单回调） */
public final class LinkResult<T> {
    public final int    code;     // Pipe.OK / 1xx 总线段 / 2xx 业务段
    public final T      value;    // 成功载荷，可 null
    public final String message;  // 失败描述

    private LinkResult(int code, T value, String message) {
        this.code = code; this.value = value; this.message = message;
    }
    public boolean isOk() { return code == Pipe.OK; }

    public static <T> LinkResult<T> ok(T v)              { return new LinkResult<>(Pipe.OK, v, null); }
    public static <T> LinkResult<T> fail(int c, String m){ return new LinkResult<>(c, null, m); }

    @Override public String toString() { return isOk() ? "OK" : ("E" + code + ":" + message); }
}
