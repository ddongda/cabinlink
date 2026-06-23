package com.baic.bridge.core;

/** 模块状态回调。所有方法在 SDK worker 线程串行触发，更新 UI 请自行切主线程。 */
public interface ModuleCallback {
    /** 提供该模块的节点 bindService 成功（传输通道建立，尚未握手）。排查日志用。 */
    default void onConnected() {}

    /** 提供方握手完成、该模块能力首次可用。 */
    void onReady();

    /** 提供方重启恢复（曾 ready → 断开 → 重连重握手）后再次可用。 */
    default void onRebooted() {}
}
