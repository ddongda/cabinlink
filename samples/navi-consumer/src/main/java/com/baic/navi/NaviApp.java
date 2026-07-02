package com.baic.navi;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.baic.bridge.contract.media.MediaClient;
import com.baic.bridge.contract.media.MediaContract;
import com.baic.bridge.contract.media.MediaSchema;
import com.baic.bridge.contract.usercenter.UserCenterClient;
import com.baic.bridge.contract.usercenter.UserCenterContract;
import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;
import com.baic.bridge.core.ModuleCallback;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        // 链式接入：init 返回 BridgeSetup，注册模块（带状态回调）+ 批量订阅一气呵成。
        // 纯客户端：不暴露 Service，仅主动连别人 + attach 回调。
        Bridge.init(this)
              .register(UserCenterContract.NODE, moduleCb("账号"))
              .register(MediaContract.NODE, moduleCb("多媒体"))
              // 批量订阅：账号状态 + 媒体状态共用一个回调，按 topic 区分（无需逐个 xxxClient）
              .subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
              .on((topic, payload) -> push(topic + ": " + payload));
        // 也可整模块订阅该模块下全部 event：Bridge.subscribeAll(UserCenterSchema.MODULE).on((t, p) -> ...);
    }

    /** 构造一个模块状态回调：连接成功 / 就绪 / 提供方重启恢复，统一推到界面与日志（演示用）。 */
    private static ModuleCallback moduleCb(final String name) {
        return new ModuleCallback() {
            @Override public void onConnected() { push("【" + name + "】连接成功 (onConnected)"); }
            @Override public void onReady()     { push("【" + name + "】就绪，可调用 (onReady)"); }
            @Override public void onRebooted()  { push("【" + name + "】提供方重启恢复 (onRebooted)"); }
        };
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

    private static final String STRESS = "NaviStress";

    /**
     * 【压测工具·保留】并发高频 request media.play，统计吞吐/延迟/错误，结果打到 logcat（tag=NaviStress）。
     * 可重复跑做回归基线：{@code adb logcat -s NaviStress}。默认 8 线程 × 200 = 1600 个并发请求。
     */
    public static void runStressTest() {
        final int threads = 8, perThread = 200, total = threads * perThread;
        final AtomicInteger ok = new AtomicInteger();
        final AtomicInteger err = new AtomicInteger();
        final ConcurrentHashMap<Integer, AtomicInteger> errCodes = new ConcurrentHashMap<>();
        final AtomicLong latSum = new AtomicLong();
        final AtomicLong latMax = new AtomicLong();
        final CountDownLatch done = new CountDownLatch(total);
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final long t0 = SystemClock.uptimeMillis();
        push("[压测] 开始 " + threads + "×" + perThread + "=" + total + " 并发请求，结果见 logcat -s NaviStress");
        Log.i(STRESS, "压测开始 线程=" + threads + " 每线程=" + perThread + " 总请求=" + total);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                for (int j = 0; j < perThread; j++) {
                    final long s = SystemClock.uptimeMillis();
                    Bridge.request(MediaSchema.PLAY, "{\"trackId\":\"1\"}", new BridgeReply() {
                        @Override public void onSuccess(String p) { record(s, latSum, latMax); ok.incrementAndGet(); done.countDown(); }
                        @Override public void onError(int code, String m) {
                            record(s, latSum, latMax); err.incrementAndGet();
                            errCodes.computeIfAbsent(code, k -> new AtomicInteger()).incrementAndGet();
                            done.countDown();
                        }
                    }, 5000);
                }
            });
        }
        new Thread(() -> {
            boolean fin;
            try { fin = done.await(120, TimeUnit.SECONDS); } catch (InterruptedException e) { fin = false; }
            long dur = Math.max(1, SystemClock.uptimeMillis() - t0);
            long n = total - done.getCount();
            String report = "压测结束 完成=" + n + "/" + total + (fin ? "" : "（超时未全完成）")
                    + " 成功=" + ok.get() + " 失败=" + err.get() + " 错误码=" + errCodes
                    + " 耗时=" + dur + "ms QPS=" + (n * 1000 / dur)
                    + " 平均延迟=" + (latSum.get() / Math.max(1, n)) + "ms 最大延迟=" + latMax.get() + "ms";
            Log.i(STRESS, report);
            push("[压测] " + report);
            pool.shutdownNow();
        }, "navi-stress-reporter").start();
    }

    private static void record(long startMs, AtomicLong sum, AtomicLong max) {
        long d = SystemClock.uptimeMillis() - startMs;
        sum.addAndGet(d);
        max.updateAndGet(x -> Math.max(x, d));
    }

    private static void push(final String text) {
        last = text;
        MAIN.post(() -> { AccountUi u = ui; if (u != null) u.show(text); });   // 回调在 worker 线程，切主线程更新 UI
    }
}
