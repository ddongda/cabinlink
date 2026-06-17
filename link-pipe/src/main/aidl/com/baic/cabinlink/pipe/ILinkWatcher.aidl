// ═══ 控制面 ═══ 能力上线/下线通知
package com.baic.cabinlink.pipe;

import com.baic.cabinlink.pipe.ICapabilityPipe;

oneway interface ILinkWatcher {
    void onAvailable(String capabilityId, ICapabilityPipe pipe, int version);
    void onUnavailable(String capabilityId);
}
