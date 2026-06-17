package com.baic.contract.media;

/**
 * baic.media 的 op/topic/propId 分配表（与 capability-map.md 全局登记一致）。
 * 代码生成落地后本文件由 APT 产出；手写阶段是双端唯一对照表——改这里必须同步图谱。
 */
public final class MediaSchema {
    private MediaSchema() {}

    public static final String CAPABILITY_ID = "baic.media";
    public static final int    VERSION       = 10000;          // 1.0.0

    // Call op
    public static final int OP_PLAY       = 1;                 // 无参
    public static final int OP_PAUSE      = 2;                 // 无参
    public static final int OP_NEXT       = 3;                 // 无参
    public static final int OP_PREV       = 4;                 // 无参
    public static final int OP_SET_VOLUME = 5;                 // args: K_VALUE int（0~100 提供方夹紧）

    // Property id
    public static final int P_PLAY_STATE = 1;                 // int 0停/1播/2暂停
    public static final int P_TITLE      = 2;                 // String
    public static final int P_VOLUME     = 3;                 // int 0~100

    // Event topic
    public static final int T_TRACK_CHANGED = 1;              // data: K_VALUE String（曲目标题）
}
