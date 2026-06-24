package com.baic.bridge.contract.usercenter;

import com.baic.bridge.core.ServiceNode;

/** 用户中心节点坐标：消费方注入此 NODE 即可连接账号提供方（替代 bridge_nodes.json 条目）。 */
public final class UserCenterContract {
    /** 账号提供方：lite 挂宿主 Service，需显式 action + component。 */
    public static final ServiceNode NODE = new ServiceNode(
            "com.baic.usercenter", "com.baic.usercenter.HOST",
            "com.baic.usercenter/.HostService",
            UserCenterSchema.MODULE, UserCenterSchema.VERSION);

    private UserCenterContract() {}
}
