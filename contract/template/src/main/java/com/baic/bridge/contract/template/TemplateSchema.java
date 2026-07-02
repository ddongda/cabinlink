package com.baic.bridge.contract.template;

/**
 * 【契约模板】&lt;模块&gt; 的 topic 与 payload 字段常量。复制后：
 * <ol>
 *   <li>改 MODULE 为你的模块前缀（全局唯一，= Gradle 路径 :contract:&lt;module&gt; = topic 前缀）；</li>
 *   <li>topic 常量值必须以 {@code MODULE + "."} 开头；payload 字段常量统一用 {@code K_} 前缀（值不含 "."）；</li>
 *   <li>schema 演进时递增 VERSION，并保证向后兼容（只增字段、不改既有语义）。</li>
 * </ol>
 * 命名规范由 :contract-verify 单测强制校验，违反即构建失败。
 */
public final class TemplateSchema {
    public static final String MODULE  = "template";   // TODO 改成你的模块前缀（全局唯一）
    public static final int    VERSION = 1;

    // ── request topic（提供方用 onRequest / onRequestAsync 响应）──
    public static final String DO_ACTION = "template.doAction";   // TODO 改

    // ── event topic（提供方用 publish 发布）──
    public static final String STATE     = "template.state";      // TODO 改

    // ── payload 字段常量（双端共用，防拼写漂移；统一 K_ 前缀，值不含 "."）──
    public static final String K_PARAM   = "param";
    public static final String K_RESULT  = "result";

    // ── 大模块可选：按子域分组为嵌套类，避免单类膨胀（引用 TemplateSchema.Sub.ACTION）。
    //    约束不变：嵌套里的 topic 仍须以 MODULE+"." 开头、字段仍用 K_ 前缀（ContractVerifyTest 会递归扫到）──
    public static final class Sub {            // TODO 子域示例（如车控的 Temp / Seat / Window）
        public static final String ACTION = "template.sub.action";
        public static final String K_FLAG  = "flag";

        private Sub() {}
    }

    private TemplateSchema() {}
}
