package com.baic.bridge.core;

import android.os.IBinder;

/**
 * lite 外挂入口（Bridge SDK §10.2 用法A）：宿主已有 Service 时，用它把 Bridge 通道挂到宿主 onBind 上，
 * 不新增 Service 类、不增进程。宿主 onBind 按 action 判断后返回 {@link #getBinder()}。
 */
public final class BridgeNodeHost {

    /** 默认 action；宿主也可在清单用自定义 action（连接方清单 component 指向宿主组件即可）。 */
    public static final String ACTION = "com.baic.bridge.NODE";

    private final BridgeCore core;

    BridgeNodeHost(BridgeCore core) { this.core = core; }

    /** 宿主 Service.onBind 返回此 Binder，即对外的 IBridgeNode 通道。 */
    public IBinder getBinder() { return core.localNode(); }

    public void register(String module) { core.register(module); }
    public void onRequest(String topic, RequestHandler handler) { core.onRequest(topic, handler); }
    public void subscribe(String topic, EventListener listener) { core.subscribe(topic, listener); }
    public void publish(String topic, String payload) { core.publish(topic, payload); }
}
