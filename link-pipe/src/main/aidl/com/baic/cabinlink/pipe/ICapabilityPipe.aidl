// ═══ 数据面·冻结区 ═══ 全总线唯一传输接口（ADR-0001）。
// 评审通过后本文件永不修改方法签名；演进只发生在 opcode 表与 Bundle schema。
package com.baic.cabinlink.pipe;

import com.baic.cabinlink.pipe.IPipeReply;
import com.baic.cabinlink.pipe.IPipeCallback;

interface ICapabilityPipe {

    /** Call 原语：1 次 IPC 发起，reply 异步回执（含统一错误码） */
    oneway void invoke(int opCode, in Bundle args, IPipeReply reply);

    /**
     * Event/Property 订阅。topics 含属性主题（PROP_TOPIC_BASE+propId）时，
     * 提供方必须立即向该 callback 补推一次全量快照（不变量#3）。
     */
    void subscribe(in int[] topics, IPipeCallback callback);

    void unsubscribe(IPipeCallback callback);

    /** Property 快照批量拉取（重连兜底；常态读消费端本地镜像） */
    Bundle snapshot(in int[] propertyIds);

    /** 统一健康检查：内核 HealthMonitor 无差别 ping，须原样回传 nonce */
    int ping(int nonce);

    /** 协商：contract version 等元信息，消费端据此做版本门禁 */
    Bundle describe();
}
