package com.baic.contract.car;

/**
 * baic.car.hvac 的 op/topic/propId 分配表（与 capability-map.md 全局登记一致）。
 * 代码生成落地后本文件由 APT 产出；手写阶段是双端唯一对照表——改这里必须同步图谱。
 */
public final class HvacSchema {
    private HvacSchema() {}

    public static final String CAPABILITY_ID = "baic.car.hvac";
    public static final int    VERSION       = 10000;          // 1.0.0

    // Call op
    public static final int OP_SET_AC_POWER    = 1;            // args: K_VALUE boolean
    public static final int OP_SET_TEMPERATURE = 2;            // args: K_VALUE float（16~32 提供方夹紧）

    // Property id
    public static final int P_AC_ON       = 1;                 // boolean
    public static final int P_TEMPERATURE = 2;                 // float
    public static final int P_FAN_SPEED   = 3;                 // int 0~7

    // Event topic
    public static final int T_ALERT = 1;                       // data: K_VALUE String（最小版）
}
