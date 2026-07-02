package com.baic.bridge.core.full;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.baic.bridge.core.Bridge;

/**
 * 全量形态的托管 Service：被别的节点 bind 时返回 Bridge 内核的 IBridgeNode 通道。
 * 业务在 Application.onCreate 调 Bridge.init() 后，本 Service 由框架托管，无需业务感知。
 */
public class BridgeNodeService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return Bridge.nodeBinder();
    }
}
