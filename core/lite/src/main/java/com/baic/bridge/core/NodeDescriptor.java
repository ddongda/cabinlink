package com.baic.bridge.core;

/** 静态清单中的一个节点条目（Bridge SDK §4.1）。 */
public final class NodeDescriptor {
    public final String id;         // 节点 id = 包名
    public final String action;     // bind 用的 intent action
    public final String component;  // 形如 "pkg/.HostService"；为空则按 action 隐式解析（全量自带 Service）

    public NodeDescriptor(String id, String action, String component) {
        this.id = id; this.action = action; this.component = component;
    }
}
