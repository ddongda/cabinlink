package com.baic.usercenter;

import android.app.Application;

import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.Bridge;

import org.json.JSONObject;

/**
 * 用户中心 App：lite 外挂形态。在 Application 里把 Bridge 内核挂到进程，
 * 注册账号能力（响应 getAccount + 发布 account.state）。通道由宿主 HostService.onBind
 * 返回 {@link Bridge#nodeBinder()} 暴露——不新增 Service 类、不增进程。
 */
public class UserCenterApp extends Application {

    private static volatile String currentAccount = "{\"loginState\":0}";

    @Override
    public void onCreate() {
        super.onCreate();
        Bridge.init(this);
        // 提供方：注册能力即声明模块（onRequest 写入 handlers，经 HELLO 通告对端）；无需再单独声明模块
        Bridge.onRequest(UserCenterSchema.GET_ACCOUNT, (req, resp) -> resp.ok(currentAccount));
    }

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
        Bridge.publish(UserCenterSchema.ACCOUNT_STATE, currentAccount);
    }
}
