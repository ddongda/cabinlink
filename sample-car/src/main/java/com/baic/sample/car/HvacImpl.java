package com.baic.sample.car;

import android.util.Log;

import com.baic.contract.car.HvacSkeleton;

/**
 * 提供方业务实现 —— 这就是车控团队要写的【全部】代码。
 * 没有 AIDL / RemoteCallbackList / 死亡监听 / 重注册 / 线程锁。
 */
public class HvacImpl extends HvacSkeleton {

    private static final String TAG = "HvacImpl";

    public HvacImpl() {
        // 初始状态（真实场景来自 CAN 总线）
        setAcOnProp(false);
        setTemperatureProp(24.0f);
        setFanSpeedProp(3);
    }

    @Override
    public void setAcPower(boolean on, ReplySink reply) {
        Log.i(TAG, "CAN <- AC_POWER " + on);     // 模拟下发 CAN
        setAcOnProp(on);                          // 改属性即自动推送给所有订阅者
        reply.ok();
    }

    @Override
    public void setTemperature(float celsius, ReplySink reply) {
        float v = Math.max(16f, Math.min(32f, celsius));   // 夹紧，不信任入参
        Log.i(TAG, "CAN <- AC_TEMP " + v);
        setTemperatureProp(v);
        reply.ok();
    }

    // ── 供本进程调试页模拟"车端自发变化"（真实场景由 CAN 回调驱动）──
    public void simulateCanTemperature(float t) { setTemperatureProp(t); }
    public void simulateAlert(String message)   { emitAlert(message); }
}
