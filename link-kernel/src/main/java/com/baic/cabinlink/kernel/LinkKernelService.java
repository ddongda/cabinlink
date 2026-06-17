package com.baic.cabinlink.kernel;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * 控制面 Service。onBind 返回进程级单例（教训#9）。
 * 量产版：persistent 主进程（教训#2，不设 android:process）。
 *
 * 启动稳定性三件套：onCreate 拉起 Watchdog（主线程 ANR 自检，教训#1）
 * 与 KernelImpl 内部的 HealthMonitor（能力周期 ping）。
 */
public class LinkKernelService extends Service {

    private static final String TAG = "LinkKernelService";

    /** 进程级单例：首次 onCreate 时用 application context 构造（AclGuard 需要）。 */
    private static volatile KernelImpl sKernel;
    private static final Object sLock = new Object();

    /** 主线程看门狗：进程级单例，随 Service 生命周期启停。 */
    private static final Watchdog sWatchdog = new Watchdog();

    private static KernelImpl kernel(Context ctx) {
        if (sKernel == null) synchronized (sLock) {
            if (sKernel == null) {
                KernelImpl k = new KernelImpl(ctx.getApplicationContext());
                k.onKernelStart();   // 启动健康巡检
                sKernel = k;
            }
        }
        return sKernel;
    }

    @Override public void onCreate() {
        super.onCreate();
        kernel(this);            // 确保内核 + HealthMonitor 就绪
        sWatchdog.start();       // 主线程 ANR 自检（独立线程检查，教训#1）
        Log.i(TAG, "kernel service created");
    }

    @Override public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind " + intent);
        return kernel(this);
    }
}
