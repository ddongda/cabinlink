package com.baic.cabinlink.kernel;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 主线程 ANR 自检看门狗。
 *
 * 一期血泪教训#1（不可回退）：
 *  · 监控对象必须是「主线程」Looper，不是看门狗自己的 Looper；
 *  · 检查逻辑放在「独立线程」while+sleep，绝不 post 死循环给自己
 *    （一期 new Handler(getLooper()) 监控自己 + 自喂 post，每 10s 自杀）。
 *
 * 原理：独立线程每 tick 周期向主线程 Handler post 一个递增计数的「打卡」任务；
 * 主线程只要在跑就会执行打卡使计数追平。独立线程下一轮检查若发现计数长时间未推进，
 * 即判定主线程被卡（ANR 前兆），dump 主线程堆栈用于定位。看门狗自身永不阻塞主线程。
 */
final class Watchdog {

    private static final String TAG = "LinkKernel.Watchdog";
    private static final long DEFAULT_TICK_MS = 2_000L;
    private static final long DEFAULT_ANR_THRESHOLD_MS = 6_000L;   // 连续无打卡超此值判定卡死

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());  // 仅用于向主线程投打卡任务
    private final long mTickMs;
    private final long mAnrThresholdMs;

    /** 独立线程投出的打卡序号；主线程执行打卡后追平到此值 */
    private final AtomicLong mScheduled = new AtomicLong(0);
    private final AtomicLong mAcked = new AtomicLong(0);
    private final AtomicLong mLastAckUptime = new AtomicLong(0);

    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private volatile Thread mThread;

    Watchdog() { this(DEFAULT_TICK_MS, DEFAULT_ANR_THRESHOLD_MS); }
    Watchdog(long tickMs, long anrThresholdMs) {
        mTickMs = tickMs; mAnrThresholdMs = anrThresholdMs;
    }

    void start() {
        if (!mRunning.compareAndSet(false, true)) return;   // 幂等
        mLastAckUptime.set(android.os.SystemClock.uptimeMillis());
        Thread t = new Thread(this::loop, "link-watchdog");   // ★ 独立线程，不是主线程
        t.setDaemon(true);
        mThread = t;
        t.start();
        Log.i(TAG, "watchdog started tick=" + mTickMs + "ms threshold=" + mAnrThresholdMs + "ms");
    }

    void stop() {
        mRunning.set(false);
        Thread t = mThread;
        if (t != null) t.interrupt();
        mMainHandler.removeCallbacksAndMessages(null);
    }

    private void loop() {
        boolean reported = false;   // 同一次卡死只报一次，恢复后复位
        while (mRunning.get()) {
            // 1) 向主线程投打卡任务（任务体极轻：只把 acked 追平到本序号）
            final long seq = mScheduled.incrementAndGet();
            mMainHandler.post(() -> {
                mAcked.set(seq);
                mLastAckUptime.set(android.os.SystemClock.uptimeMillis());
            });

            // 2) 独立线程睡眠一个 tick，期间主线程应已执行打卡
            try {
                Thread.sleep(mTickMs);
            } catch (InterruptedException e) {
                if (!mRunning.get()) break;
            }

            // 3) 检查打卡是否追平；长时间不追平 = 主线程卡死
            long lag = android.os.SystemClock.uptimeMillis() - mLastAckUptime.get();
            boolean stuck = (mAcked.get() < mScheduled.get() - 1) && lag >= mAnrThresholdMs;
            if (stuck) {
                if (!reported) {
                    reported = true;
                    onMainThreadStuck(lag);
                }
            } else {
                reported = false;   // 主线程已恢复，允许下次再报
            }
        }
        Log.i(TAG, "watchdog stopped");
    }

    /** 判定主线程卡死：dump 主线程堆栈（不杀进程，交由系统 ANR 机制处置）。 */
    private void onMainThreadStuck(long lagMs) {
        Thread main = Looper.getMainLooper().getThread();
        StringBuilder sb = new StringBuilder();
        sb.append("MAIN THREAD STUCK ~").append(lagMs).append("ms; stack:\n");
        for (StackTraceElement el : main.getStackTrace()) {
            sb.append("\tat ").append(el).append('\n');
        }
        Log.e(TAG, sb.toString());
    }
}
