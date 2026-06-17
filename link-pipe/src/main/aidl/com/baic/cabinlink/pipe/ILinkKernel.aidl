// ═══ 控制面 ═══ 仅注册/发现/等待，不传业务数据（铁律）
package com.baic.cabinlink.pipe;

import com.baic.cabinlink.pipe.ICapabilityPipe;
import com.baic.cabinlink.pipe.ILinkWatcher;

interface ILinkKernel {
    /** @return 0=成功 -1=无权限 -2=id非法 -3=pipe无效 */
    int register(String capabilityId, int version, ICapabilityPipe pipe);

    void unregister(String capabilityId);

    /** 已注册返回 pipe，否则 null（消费方常用 waitFor） */
    ICapabilityPipe query(String capabilityId);

    /** 等待能力上线：已在线立即回调；崩溃后恢复也会再次回调（reattach 依据） */
    void waitFor(String capabilityId, ILinkWatcher watcher);

    void unwatch(String capabilityId, ILinkWatcher watcher);
}
