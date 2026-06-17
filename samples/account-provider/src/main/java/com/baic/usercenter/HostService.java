package com.baic.usercenter;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * 模拟"宿主已有的 Service"。除自身业务通道外，按 Bridge 约定的 action 复用同一 Service
 * 返回 Bridge 通道（Bridge SDK §10.2 用法A）——不新增 Service 类、不增进程。
 */
public class HostService extends Service {
    public static final String BRIDGE_ACTION = "com.baic.usercenter.HOST";

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && BRIDGE_ACTION.equals(intent.getAction())) {
            return UserCenterApp.host().getBinder();   // Bridge 通道
        }
        return null;   // 宿主原有业务通道（示例从略）
    }
}
