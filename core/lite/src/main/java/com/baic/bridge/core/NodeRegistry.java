package com.baic.bridge.core;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取静态节点清单 assets/bridge_nodes.json（Bridge SDK §4.1）。
 * 业务 App 可在自己的 assets 覆盖此文件；找不到则返回空清单（仅靠被动 attach 也能工作）。
 */
final class NodeRegistry {
    private static final String TAG = "Bridge.Registry";
    private static final String ASSET = "bridge_nodes.json";

    static List<ServiceNode> load(Context ctx) {
        List<ServiceNode> out = new ArrayList<>();
        try (InputStream in = ctx.getAssets().open(ASSET)) {
            byte[] buf = new byte[in.available()];
            int n = in.read(buf);
            String text = new String(buf, 0, Math.max(n, 0), "UTF-8");
            JSONObject root = new JSONObject(text);
            JSONArray nodes = root.optJSONArray("nodes");
            if (nodes != null) {
                for (int i = 0; i < nodes.length(); i++) {
                    JSONObject o = nodes.getJSONObject(i);
                    java.util.Set<String> mods = new java.util.LinkedHashSet<>();
                    JSONArray ma = o.optJSONArray("modules");
                    if (ma != null) for (int j = 0; j < ma.length(); j++) {
                        String m = ma.optString(j, null);
                        if (m != null && !m.isEmpty()) mods.add(m);
                    }
                    out.add(new ServiceNode(
                            o.optString("id", null),
                            o.optString("action", "com.baic.bridge.NODE"),
                            o.has("component") ? o.optString("component", null) : null,
                            mods, 0));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "未找到/无法解析 " + ASSET + "，使用空清单（仅被动 attach）：" + e);
        }
        return out;
    }

    private NodeRegistry() {}
}
