package com.baic.bridge.contract.usercenter;

import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;
import com.baic.bridge.core.EventListener;

/**
 * 消费方强类型门面（可选）：把账号相关的 topic 封装成方法，调用方无需记 topic 字符串。
 * 导航、多媒体等订阅账号状态的模块用这个即可（Bridge SDK §11）。
 */
public final class UserCenterClient {

    /** 订阅账号状态变化（登录/登出/切换/资料变更）。 */
    public static void subscribeAccountState(EventListener listener) {
        Bridge.subscribe(UserCenterSchema.ACCOUNT_STATE, listener);
    }

    /** 主动拉取当前账号（首屏兜底，避免漏掉订阅前已发的最后状态）。 */
    public static void getAccount(BridgeReply reply, long timeoutMs) {
        Bridge.request(UserCenterSchema.GET_ACCOUNT, "{}", reply, timeoutMs);
    }

    private UserCenterClient() {}
}
