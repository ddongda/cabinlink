// ═══ 传输层·冻结区 ═══ 全总线唯一跨进程接口（ADR：Bridge SDK §5）。
// 方法签名评审通过后永不修改；请求/响应/事件/握手统一走 deliver。
package com.baic.bridge.transport;

import com.baic.bridge.transport.BridgeEnvelope;

interface IBridgeNode {

    /**
     * 投递一条信封（REQUEST/RESPONSE/EVENT/HELLO 统一入口）。
     * oneway：投递即返回，绝不同步阻塞调用线程——对端再慢也不会拖垮本端 Binder 线程池或触发 ANR。
     * 请求-响应的配对由上层 correlationId + 超时计时器完成，而非 Binder 同步返回值。
     */
    oneway void deliver(in BridgeEnvelope envelope);

    /**
     * 注册反向通道：建连方把自己的 IBridgeNode 回调交给对端，形成全双工。
     * 这是 lite 形态（无 Service 的纯客户端 / 宿主外挂）能收到 response/event 的关键。
     * 非 oneway：建连时同步确认一次，确保对端已登记反向通道再开始收发。
     */
    void attach(IBridgeNode peer, String peerNodeId);
}
