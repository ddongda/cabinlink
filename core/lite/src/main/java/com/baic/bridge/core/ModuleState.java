package com.baic.bridge.core;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 单个已注册模块的就绪状态 + 回调集合。
 * 状态转移为纯逻辑（不碰 Android / 不触发回调），便于 JVM 单测；回调由 BridgeCore 据返回事件触发。
 */
final class ModuleState {
    /**
     * applyReadiness 返回的应触发事件。
     */
    enum Event {NONE, READY, REBOOTED, LOST}

    final String module;
    final int contractVersion;                 // 契约门面版本，仅用于日志
    final CopyOnWriteArrayList<ModuleCallback> callbacks = new CopyOnWriteArrayList<>();

    private boolean readyOnce;                  // 历史上是否就绪过
    private boolean currentlyReady;             // 当前是否有握手完成的提供方

    ModuleState(String module, int contractVersion) {
        this.module = module;
        this.contractVersion = contractVersion;
    }

    boolean isReady() {
        return currentlyReady;
    }

    /**
     * 依据最新就绪判定推进状态，返回应触发的事件。线程安全（worker 串行调用，仍加锁兜底）。
     */
    synchronized Event applyReadiness(boolean now) {
        if (!currentlyReady && now) {
            currentlyReady = true;
            if (!readyOnce) {
                readyOnce = true;
                return Event.READY;
            }
            return Event.REBOOTED;
        }
        if (currentlyReady && !now) {
            currentlyReady = false;
            return Event.LOST;
        }
        return Event.NONE;
    }
}
