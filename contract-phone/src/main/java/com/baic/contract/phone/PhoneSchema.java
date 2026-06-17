package com.baic.contract.phone;

/**
 * baic.phone 的 op/topic/propId 分配表（与 capability-map.md 全局登记一致）。
 * 代码生成落地后本文件由 APT 产出；手写阶段是双端唯一对照表——改这里必须同步图谱。
 */
public final class PhoneSchema {
    private PhoneSchema() {}

    public static final String CAPABILITY_ID = "baic.phone";
    public static final int    VERSION       = 10000;          // 1.0.0

    // Call op
    public static final int OP_ANSWER = 1;                     // args: 无
    public static final int OP_HANGUP = 2;                     // args: 无
    public static final int OP_DIAL   = 3;                     // args: K_VALUE String（号码）

    // Property id
    public static final int P_CALL_STATE  = 1;                 // int 0空闲/1响铃/2通话中
    public static final int P_PEER_NUMBER = 2;                 // String 对端号码

    // Event topic
    public static final int T_INCOMING_CALL = 1;              // data: K_VALUE String（来电号码）
    public static final int T_CALL_ENDED    = 2;              // data: 无（通话结束）
}
