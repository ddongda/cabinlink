package com.baic.contract.navi;

/**
 * baic.navi 的 op/topic/propId 分配表（与 capability-map.md 全局登记一致）。
 * 代码生成落地后本文件由 APT 产出；手写阶段是双端唯一对照表——改这里必须同步图谱。
 */
public final class NaviSchema {
    private NaviSchema() {}

    public static final String CAPABILITY_ID = "baic.navi";
    public static final int    VERSION       = 10000;          // 1.0.0

    // Call op（本能力内从 1 起分配）
    public static final int OP_NAVIGATE_POI    = 1;            // args: K_VALUE String（POI 名/地址）
    public static final int OP_NAVIGATE_COORD  = 2;            // args: K_LAT double, K_LNG double
    public static final int OP_CANCEL          = 3;            // args: 无

    // Property id（本能力内从 1 起分配）
    public static final int P_NAVI_STATE   = 1;                // int 0空闲/1导航中
    public static final int P_ETA_SECONDS  = 2;                // int ETA 秒
    public static final int P_REMAIN_METERS = 3;              // int 剩余距离米

    // Event topic（本能力内从 1 起分配）
    public static final int T_TURN_HINT = 1;                   // data: K_VALUE String 转向提示
    public static final int T_ARRIVED   = 2;                   // data: 无（到达目的地）

    // 本能力私有 Bundle key（坐标导航专用，避免与 K_VALUE 单值冲突）
    public static final String K_LAT = "lat";
    public static final String K_LNG = "lng";

    // 导航态枚举
    public static final int STATE_IDLE       = 0;
    public static final int STATE_NAVIGATING = 1;
}
