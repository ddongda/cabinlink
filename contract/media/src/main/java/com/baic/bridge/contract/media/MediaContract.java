package com.baic.bridge.contract.media;

import com.baic.bridge.core.ServiceNode;

/** 多媒体节点坐标：消费方注入此 NODE 即可连接多媒体提供方（替代 bridge_nodes.json 条目）。 */
public final class MediaContract {
    /** 多媒体提供方：全量形态，按 action 隐式解析（无自定义 component）。 */
    public static final ServiceNode NODE = new ServiceNode(
            "com.baic.media", "com.baic.bridge.NODE", null,
            MediaSchema.MODULE, MediaSchema.VERSION);

    private MediaContract() {}
}
