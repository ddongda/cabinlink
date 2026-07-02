package com.baic.bridge.core;

import org.json.JSONObject;

/**
 * 提供方 onRequest 收到的请求视图：对 payload(JSON) 的只读访问。
 */
public final class BridgeRequest {
    private final String raw;
    private final JSONObject json;

    public BridgeRequest(String payload) {
        this.raw = payload == null ? "{}" : payload;
        JSONObject j;
        try {
            j = new JSONObject(this.raw);
        } catch (Exception e) {
            j = new JSONObject();
        }
        this.json = j;
    }

    /**
     * 取字符串字段，缺失返回 null。
     */
    public String get(String key) {
        return json.has(key) ? json.optString(key, null) : null;
    }

    public int getInt(String key, int def) {
        return json.optInt(key, def);
    }

    public boolean getBool(String key, boolean def) {
        return json.optBoolean(key, def);
    }

    /**
     * 原始 JSON 串。
     */
    public String raw() {
        return raw;
    }
}
