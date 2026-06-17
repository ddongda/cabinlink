package com.baic.cabinlink.kernel;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 签名级访问控制（控制面入口的唯一鉴权点）。
 *
 * 铁律落地（教训#4/#5）：
 *  · pid/uid 一律取 {@link Binder#getCallingPid()} / {@link Binder#getCallingUid()} 内核值，
 *    绝不接收调用方通过接口参数传入的身份（一期 L946 把 uid 当参数传，被伪造）；
 *  · 注册/敏感操作要求调用方与内核同签名（或系统进程），非白名单签名一律拒绝；
 *  · 本类只做判定，不向调用方暴露任何裸 Binder。
 *
 * 注意：所有 check* 方法必须在 Binder 调用线程同步调用（此时
 * getCallingUid 才是真实调用方）；严禁先把 uid 缓存再跨线程判定。
 */
final class AclGuard {

    private static final String TAG = "LinkKernel.Acl";

    private final Context mAppContext;
    private final PackageManager mPm;
    /** 本内核自身 uid（与之同签名 = 同一开发者签发，视为可信） */
    private final int mSelfUid;
    /** uid → 签名校验结果缓存（uid 在安装期固定，可安全缓存） */
    private final ConcurrentHashMap<Integer, Boolean> mTrustCache = new ConcurrentHashMap<>();

    AclGuard(Context ctx) {
        mAppContext = ctx.getApplicationContext();
        mPm = mAppContext.getPackageManager();
        mSelfUid = Process.myUid();
    }

    /**
     * 注册/注销能力的权限校验。
     * @return true 放行；false 拒绝（KernelImpl 应回 -1 无权限）
     */
    boolean checkRegister() {
        return isTrustedCaller("register");
    }

    /** 等待/查询等发现类操作的权限校验（与注册同策略；如需放宽可在此分级）。 */
    boolean checkDiscover() {
        return isTrustedCaller("discover");
    }

    /**
     * 核心判定：调用方是否可信。
     * 取内核真实 uid → 系统进程直接放行 → 否则要求与内核同签名。
     */
    private boolean isTrustedCaller(String action) {
        final int uid = Binder.getCallingUid();   // ★ 内核值，不可被参数伪造
        final int pid = Binder.getCallingPid();

        // root / system 进程（车机系统服务）放行
        if (uid == Process.SYSTEM_UID || uid == Process.ROOT_UID || uid == mSelfUid) {
            return true;
        }

        Boolean cached = mTrustCache.get(uid);
        if (cached != null) {
            if (!cached) Log.w(TAG, "deny " + action + " uid=" + uid + " pid=" + pid + " (cached)");
            return cached;
        }

        boolean trusted;
        try {
            // 签名级校验：调用方与内核必须同签名才视为同一受信方
            int match = mPm.checkSignatures(uid, mSelfUid);
            trusted = (match == PackageManager.SIGNATURE_MATCH);
        } catch (Exception e) {
            Log.w(TAG, "checkSignatures failed uid=" + uid, e);
            trusted = false;
        }

        mTrustCache.put(uid, trusted);
        if (!trusted) {
            Log.w(TAG, "deny " + action + " uid=" + uid + " pid=" + pid + " pkgs="
                    + java.util.Arrays.toString(mPm.getPackagesForUid(uid)));
        }
        return trusted;
    }
}
