package com.baic.bridge.core;

import java.util.Collections;
import java.util.Set;

/** 一个可连接的服务节点坐标（由各 :contract:X 提供，经组合根注入给 Bridge）。 */
public final class ServiceNode {
    public final String pkg;           // 节点包名（= 节点唯一标识）
    public final String action;        // bind 用的 intent action
    public final String component;     // 形如 "pkg/.Service"；null 则按 action+包名隐式解析
    public final Set<String> modules;  // 该节点提供的模块（用于 onConnected 归属）；缺省空集
    public final int contractVersion;  // 契约门面版本（仅日志），由 contract 填入

    /** 单模块便捷构造（contract 常用）。 */
    public ServiceNode(String pkg, String action, String component, String module, int contractVersion) {
        this(pkg, action, component,
             module == null ? Collections.<String>emptySet() : Collections.singleton(module),
             contractVersion);
    }

    public ServiceNode(String pkg, String action, String component, Set<String> modules, int contractVersion) {
        this.pkg = pkg; this.action = action; this.component = component;
        this.modules = modules != null ? modules : Collections.<String>emptySet();
        this.contractVersion = contractVersion;
    }
}
