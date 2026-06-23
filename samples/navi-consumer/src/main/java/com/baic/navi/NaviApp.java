package com.baic.navi;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.baic.bridge.contract.media.MediaClient;
import com.baic.bridge.contract.media.MediaSchema;
import com.baic.bridge.contract.usercenter.UserCenterClient;
import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;

/**
 * 导航 App：lite 纯客户端。
 * - 账号：订阅 usercenter.account.state + 主动 getAccount（Event + RPC，挂已有 service 的 provider）。
 * - 媒体：调用 media.play 等（RPC，全量形态的 provider）+ 订阅 media.state。
 * 只依赖两个 contract 的 schema 常量，不依赖任一 provider 的实现（低耦合）。
 */
public class NaviApp extends Application {

    public interface AccountUi { void show(String text); }

    public static volatile AccountUi ui;
    public static volatile String last = "（未收到消息）";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        Bridge.init(this);                              // 纯客户端：不暴露 Service，仅主动连别人 + attach 回调
        Bridge.register(UserCenterSchema.MODULE);
        Bridge.register(MediaSchema.MODULE);
        // 批量订阅：账号状态 + 媒体状态共用一个回调，按 topic 区分（无需逐个 xxxClient）
        Bridge.subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
              .on((topic, payload) -> push(topic + ": " + payload));
        // 也可整模块订阅该模块下全部 event：Bridge.subscribeAll(UserCenterSchema.MODULE).on((t, p) -> ...);
    }

    /** 主动拉取账号（首屏兜底）。 */
    public static void pullAccount() {
        UserCenterClient.getAccount(new BridgeReply() {
            @Override public void onSuccess(String p) { push("账号拉取: " + p); }
            @Override public void onError(int code, String msg) { push("账号拉取失败 code=" + code + " " + msg); }
        }, 3000);
    }

    /** 调用多媒体播放（RPC）。 */
    public static void playMedia() {
        MediaClient.play("1001", new BridgeReply() {
            @Override public void onSuccess(String p) { push("播放成功: " + p); }
            @Override public void onError(int code, String msg) { push("播放失败 code=" + code + " " + msg); }
        }, 3000);
    }

    private static void push(final String text) {
        last = text;
        MAIN.post(() -> { AccountUi u = ui; if (u != null) u.show(text); });   // 回调在 worker 线程，切主线程更新 UI
    }
}
