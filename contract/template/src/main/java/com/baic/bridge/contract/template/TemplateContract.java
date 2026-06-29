package com.baic.bridge.contract.template;

import com.baic.bridge.core.ServiceNode;

/**
 * 【契约模板】&lt;模块&gt; 节点坐标：消费方注入此 NODE 即完成「连接 + 模块注册」。复制后改 pkg / action / component：
 * <ul>
 *   <li>全量形态（provider 自带 BridgeNodeService）：component 传 null，按 action + 包名隐式解析；</li>
 *   <li>lite 挂宿主 Service：component 传 {@code "pkg/.HostService"}，action 用宿主约定的 action。</li>
 * </ul>
 * NODE 的 module 必须等于 {@link TemplateSchema#MODULE}（:contract-verify 会校验一致性）。
 */
public final class TemplateContract {
    public static final ServiceNode NODE = new ServiceNode(
            "com.baic.template",          // TODO 改成提供方包名
            "com.baic.bridge.NODE",       // TODO 改成 bind 用的 action
            null,                          // TODO 全量=null；挂宿主 Service 填 "pkg/.HostService"
            TemplateSchema.MODULE, TemplateSchema.VERSION);

    private TemplateContract() {}
}
