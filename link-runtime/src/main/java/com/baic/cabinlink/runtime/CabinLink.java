package com.baic.cabinlink.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.baic.cabinlink.pipe.ICapabilityPipe;
import com.baic.cabinlink.pipe.ILinkKernel;
import com.baic.cabinlink.pipe.ILinkWatcher;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * CabinLink 门面（业务唯一入口）：
 *   提供方：CabinLink.of(ctx).publish(new HvacImpl());
 *   消费方：CabinLink.of(ctx).require(Hvac.DESCRIPTOR, hvac -> {...});
 *
 * 托管：内核连接（退避重连，bind 返回 false 也重试——教训#6）、
 *      发布重放（内核重启后自动重注册——教训#7）、
 *      require 终身挂接（提供方崩溃恢复后自动 reattach，业务零感知）。
 */
public final class CabinLink {

    private static final String TAG = "CabinLink";
    private static final long MAX_RETRY_DELAY_MS = 30_000L;

    private static volatile CabinLink sInstance;
    public static CabinLink of(Context ctx) {
        if (sInstance == null) synchronized (CabinLink.class) {
            if (sInstance == null) sInstance = new CabinLink(ctx.getApplicationContext());
        }
        return sInstance;
    }

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private volatile ILinkKernel mKernel;

    /** 已发布骨架（重放依据，教训#7） */
    private final ConcurrentHashMap<String, CapabilitySkeleton> mPublished = new ConcurrentHashMap<>();
    /** 进行中的 require 挂接（终身托管） */
    private final CopyOnWriteArrayList<Attachment<?>> mAttachments = new CopyOnWriteArrayList<>();

    private CabinLink(Context ctx) { mContext = ctx; connect(); }

    // ════ 提供方 API ════════════════════════════════════════
    public void publish(CapabilitySkeleton skeleton) {
        // P1 修复：同一 skeleton 实例二次 publish 必须幂等，不得重复 doRegister。
        // putIfAbsent 失败且为同一实例 → 已发布过，直接返回；不同实例 → 替换并重注册（升级语义）。
        CapabilitySkeleton prev = mPublished.putIfAbsent(skeleton.capabilityId(), skeleton);
        if (prev == skeleton) return;                       // 完全重复，幂等忽略
        if (prev != null) mPublished.put(skeleton.capabilityId(), skeleton);  // 实例替换
        ILinkKernel k = mKernel;
        if (k != null) doRegister(k, skeleton);
        // 未连接：connect 成功后统一重放
    }

    // ════ 消费方 API ════════════════════════════════════════
    /**
     * 获取强类型代理。onReady 在主线程回调一次（拿到即用）；
     * 之后提供方崩溃/恢复由运行时静默 reattach，无需业务处理。
     */
    public <T> void require(CapabilityDescriptor<T> desc, Consumer<T> onReady) {
        Attachment<T> att = new Attachment<>(desc, onReady);
        mAttachments.add(att);
        ILinkKernel k = mKernel;
        if (k != null) att.start(k);
    }

    // ════ 内核连接（退避重连） ═══════════════════════════════
    private final AtomicBoolean mScheduling = new AtomicBoolean(false);
    private final AtomicInteger mRetry = new AtomicInteger(0);
    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "link-reconnect"); t.setDaemon(true); return t;
            });

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mRetry.set(0);
            ILinkKernel kernel = ILinkKernel.Stub.asInterface(b);
            mKernel = kernel;
            Log.i(TAG, "kernel connected");
            for (CapabilitySkeleton s : mPublished.values()) doRegister(kernel, s);  // 重放发布
            for (Attachment<?> a : mAttachments) a.start(kernel);                    // 重放等待
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            mKernel = null;
            Log.w(TAG, "kernel disconnected");
            scheduleRetry();
        }
    };

    private void connect() {
        try {
            Intent i = new Intent(Pipe.KERNEL_ACTION)
                    .setComponent(new ComponentName(Pipe.KERNEL_PKG, Pipe.KERNEL_CLS));
            boolean bound = mContext.bindService(i, mConn, Context.BIND_AUTO_CREATE);
            if (!bound) {   // 教训#6：返回 false 必须重试，否则开机竞速永久断连
                try { mContext.unbindService(mConn); } catch (Exception ignored) {}
                scheduleRetry();
            }
        } catch (Exception e) { scheduleRetry(); }
    }

    private void scheduleRetry() {
        if (mKernel != null) return;
        if (!mScheduling.compareAndSet(false, true)) return;
        long delay = Math.min((1L << Math.min(mRetry.getAndIncrement(), 5)) * 1000L, MAX_RETRY_DELAY_MS);
        mScheduler.schedule(() -> { mScheduling.set(false); if (mKernel == null) connect(); },
                delay, TimeUnit.MILLISECONDS);
    }

    private void doRegister(ILinkKernel k, CapabilitySkeleton s) {
        try {
            int ret = k.register(s.capabilityId(), s.version(), s.pipe());
            Log.i(TAG, "register " + s.capabilityId() + " ret=" + ret);
        } catch (RemoteException e) { Log.e(TAG, "register failed", e); }
    }

    // ════ require 挂接体 ═════════════════════════════════════
    private final class Attachment<T> {
        final CapabilityDescriptor<T> desc;
        final Consumer<T> onReady;
        volatile T proxy;                              // 复用同一代理实例（镜像/订阅状态保留）
        /**
         * 已向哪个内核 Binder 注册过 waitFor。
         * P0 修复：started 只防「同一内核实例下」重复 waitFor，
         * 内核重连/重启会换新 Binder，此时必须重新 waitFor 以恢复订阅（reattach）。
         * 旧实现用单纯 AtomicBoolean，置 true 后永不复位 → 重连后 start() 直接 return，reattach 丢失。
         */
        private final java.util.concurrent.atomic.AtomicReference<IBinder> startedOn =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        final AtomicBoolean delivered = new AtomicBoolean(false);

        Attachment(CapabilityDescriptor<T> d, Consumer<T> r) { desc = d; onReady = r; }

        void start(ILinkKernel kernel) {
            final IBinder kb = kernel.asBinder();
            // 仅当目标内核 Binder 与上次相同才视为重复（防同一连接内重复 waitFor）；
            // 换了新内核（重启/重连）则 CAS 必然成功，触发重新 waitFor。
            IBinder prev = startedOn.get();
            if (prev == kb) return;
            if (!startedOn.compareAndSet(prev, kb)) return;
            try {
                kernel.waitFor(desc.id, new ILinkWatcher.Stub() {
                    @Override public void onAvailable(String id, ICapabilityPipe pipe, int version) {
                        if (version < desc.minVersion) {
                            Log.e(TAG, id + " version " + version + " < required " + desc.minVersion);
                            return;   // 版本门禁：拒绝挂接（E_VERSION 语义）
                        }
                        if (proxy == null) proxy = desc.factory.create(pipe, CabinLink.this);
                        ((PipeProxy) proxy).attach(pipe);   // 首挂或 reattach（重订阅+镜像刷新）
                        if (delivered.compareAndSet(false, true))
                            mMain.post(() -> onReady.accept(proxy));
                    }
                    @Override public void onUnavailable(String id) { /* 镜像已由 deathRecipient 置 stale */ }
                });
            } catch (RemoteException e) {
                startedOn.compareAndSet(kb, null);   // 回滚，允许下次重连重试
                Log.e(TAG, "waitFor failed " + desc.id, e);
            }
        }
    }
}
