package com.baic.cabinlink.runtime;

/** 管道层全局约定（双端共用常量；冻结区的"数据面字典"） */
public final class Pipe {
    private Pipe() {}

    // ── Bundle keys ──────────────────────────────────────
    public static final String K_VALUE   = "v";        // 单值载荷
    public static final String K_MESSAGE = "msg";      // 错误描述
    public static final String K_VERSION = "ver";      // describe() 返回

    // ── Topic 空间：Event 用 1..0xFFFF；Property 主题 = BASE+propId ──
    public static final int PROP_TOPIC_BASE = 0x10000;
    public static int propTopic(int propId) { return PROP_TOPIC_BASE + propId; }
    public static boolean isPropTopic(int topic) { return topic >= PROP_TOPIC_BASE; }
    public static int propIdOf(int topic) { return topic - PROP_TOPIC_BASE; }

    // ── 错误码：0 OK；101~199 总线保留段（业务从 200 起）──
    public static final int OK               = 0;
    public static final int E_TIMEOUT        = 101;
    public static final int E_DETACHED       = 102;  // 提供方不在线/已崩溃
    public static final int E_ACL_DENIED     = 103;
    public static final int E_VERSION        = 104;
    public static final int E_BAD_ARGS       = 105;
    public static final int E_UNKNOWN_OP     = 106;

    // ── 内核连接约定 ──
    public static final String KERNEL_PKG    = "com.baic.cabinlink.kernel";
    public static final String KERNEL_CLS    = "com.baic.cabinlink.kernel.LinkKernelService";
    public static final String KERNEL_ACTION = "com.baic.cabinlink.BIND_KERNEL";
}
