package com.baic.bridge.contract.template;

import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;
import com.baic.bridge.core.EventListener;

import org.json.JSONObject;

/**
 * 【契约模板·可选】&lt;模块&gt; 消费方门面：把 topic 封成强类型方法，消费方少写 payload 拼装。
 * <b>约束</b>：薄封装——只做「payload 拼装 + 转发 Bridge」，<b>禁止</b>塞业务逻辑 / 状态 / 缓存。
 * 门面是可选的：消费方也可直接用 {@code Bridge.request(TemplateSchema.DO_ACTION, ...)}。
 */
public final class TemplateClient {

    /** RPC 示例：调用 template.doAction（耗时处理建议提供方用 onRequestAsync 注册）。 */
    public static void doAction(String param, BridgeReply reply, long timeoutMs) {
        String payload;
        try { payload = new JSONObject().put(TemplateSchema.K_PARAM, param == null ? "" : param).toString(); }
        catch (Exception e) { payload = "{}"; }
        Bridge.request(TemplateSchema.DO_ACTION, payload, reply, timeoutMs);
    }

    /** Event 示例：订阅 template.state。 */
    public static void subscribeState(EventListener listener) {
        Bridge.subscribe(TemplateSchema.STATE, listener);
    }

    private TemplateClient() {}
}
