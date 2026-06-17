package com.baic.bridge.core;

import android.content.Context;
import android.util.Log;

import java.util.Arrays;

/**
 * 访问控制（Bridge SDK §12）。两道防线：
 *  1) bind 入口的 signature 权限（com.baic.bridge.permission.LINK，在 manifest 声明）——只有同签名 App 能建连，这是硬防线；
 *  2) 本类做"身份不信参数"的加固：用 Binder.getCallingUid() 解析真实包名，校验是否与信封声明的 source 一致，防 source 伪造。
 *
 * 当 uid 因包可见性受限解析不到包名时降级放行（仅告警）——签名权限已兜底，不因此破坏正常通信。
 */
final class AclGuard {
    private static final String TAG = "Bridge.Acl";

    private final Context ctx;

    AclGuard(Context ctx) { this.ctx = ctx; }

    boolean verifySource(int callingUid, String claimedSource) {
        if (claimedSource == null) {
            Log.w(TAG, "信封缺少 source，拒绝 uid=" + callingUid);
            return false;
        }
        String[] pkgs = ctx.getPackageManager().getPackagesForUid(callingUid);
        if (pkgs == null || pkgs.length == 0) {
            Log.w(TAG, "uid=" + callingUid + " 解析不到包名（包可见性受限），签名权限已兜底，降级放行 source=" + claimedSource);
            return true;
        }
        for (String p : pkgs) if (claimedSource.equals(p)) return true;
        Log.w(TAG, "source 伪造嫌疑：uid=" + callingUid + " 真实包=" + Arrays.toString(pkgs) + " 声明=" + claimedSource + "，丢弃");
        return false;
    }
}
