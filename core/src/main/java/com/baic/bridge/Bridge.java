package com.baic.bridge;

import android.content.Context;
import android.os.IBinder;

/**
 * Bridge SDK 门面。业务方只跟这个类打交道。
 *
 * 提供方：{@link #init} 后 {@link #onRequest} 注册处理器，并由一个 Service 在 onBind 返回 {@link #nodeBinder()}。
 * 消费方：{@link #init} 后 {@link #connect} 目标提供方，再 {@link #request} 发起调用。
 */
public final class Bridge {

    // 错误码：0=OK；以下为 SDK 保留
    public static final int ERR_NOT_CONNECTED = 1;
    public static final int ERR_TIMEOUT       = 2;
    public static final int ERR_NO_HANDLER    = 3;
    public static final int ERR_INTERNAL      = 4;

    private static volatile BridgeCore core;

    public static synchronized void init(Context ctx) {
        if (core == null) core = new BridgeCore(ctx);
    }

    /** 消费方：连接目标提供方（按包名 + Service action 显式 bind，系统按需拉起）。 */
    public static void connect(String targetPackage, String action) {
        core().connect(targetPackage, action);
    }

    /** 提供方：注册某个 topic 的请求处理器。 */
    public static void onRequest(String topic, RequestHandler handler) {
        core().onRequest(topic, handler);
    }

    /** 消费方：异步发起请求，结果走 Reply（含超时）。回调在 SDK 工作线程，更新 UI 自行切主线程。 */
    public static void request(String topic, String payload, Reply reply, long timeoutMs) {
        core().request(topic, payload, reply, timeoutMs);
    }

    /** 提供方：发布事件给所有已连接的对端（fire-and-forget）。 */
    public static void publish(String topic, String payload) {
        core().publish(topic, payload);
    }

    /** 消费方：订阅事件。回调在 SDK 工作线程，更新 UI 自行切主线程。 */
    public static void subscribe(String topic, EventListener listener) {
        core().subscribe(topic, listener);
    }

    /** 提供方的 Service 在 onBind 返回此 Binder，对外暴露通道。 */
    public static IBinder nodeBinder() {
        return core().nodeBinder();
    }

    private static BridgeCore core() {
        BridgeCore c = core;
        if (c == null) throw new IllegalStateException("Bridge 未初始化，请先调用 Bridge.init(context)");
        return c;
    }

    private Bridge() {}
}
