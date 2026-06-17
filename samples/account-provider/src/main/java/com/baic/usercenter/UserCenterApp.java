package com.baic.usercenter;

import android.app.Application;

import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeNodeHost;

import org.json.JSONObject;

/**
 * 用户中心 App：lite 外挂形态。在 Application 里把 Bridge 内核挂到进程，
 * 注册账号能力（响应 getAccount + 发布 account.state）。被 HostService.onBind 暴露通道。
 */
public class UserCenterApp extends Application {

    private static BridgeNodeHost host;
    private static volatile String currentAccount = "{\"loginState\":0}";

    @Override
    public void onCreate() {
        super.onCreate();
        host = Bridge.attachHost(this);
        host.register(UserCenterSchema.MODULE);
        // 提供方：响应首屏主动拉取
        host.onRequest(UserCenterSchema.GET_ACCOUNT, (req, resp) -> resp.ok(currentAccount));
    }

    public static BridgeNodeHost host() { return host; }

    /** 模拟登录/切换/登出，更新当前账号并发布事件给所有订阅者（导航、多媒体…）。 */
    public static void updateAccount(int loginState, String userId, String nickname) {
        try {
            JSONObject o = new JSONObject();
            o.put(UserCenterSchema.K_LOGIN_STATE, loginState);
            o.put(UserCenterSchema.K_USER_ID, userId == null ? "" : userId);
            o.put(UserCenterSchema.K_NICKNAME, nickname == null ? "" : nickname);
            o.put(UserCenterSchema.K_VIP_LEVEL, loginState == 1 ? 3 : 0);
            currentAccount = o.toString();
        } catch (Exception e) {
            currentAccount = "{\"loginState\":" + loginState + "}";
        }
        if (host != null) host.publish(UserCenterSchema.ACCOUNT_STATE, currentAccount);
    }
}
