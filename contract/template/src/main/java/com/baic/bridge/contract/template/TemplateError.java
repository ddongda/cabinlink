package com.baic.bridge.contract.template;

/**
 * 【契约模板·可选】&lt;模块&gt; 特殊错误码。复制后改成你模块分配的区间：
 * 0=OK、1–999 为 SDK 保留，业务错误码一律 ≥1000；同区间内自定义、模块内不重复、区间不与其它模块重叠。
 * 冲突由 :contract-verify 单测自动检测（无需维护外部分配表，直接比对实际值）。
 * 若模块无特殊错误码，可删除本类（Error 类是可选的，如 usercenter 即没有）。
 */
public final class TemplateError {
    // TODO 改成你的分配区间，例如 2000–2999
    public static final int E_EXAMPLE = 9000;   // 示例错误码，复制后替换

    private TemplateError() {}
}
