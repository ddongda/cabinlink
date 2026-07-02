package com.baic.bridge.core;

/** topic ↔ module 工具。module = topic 首个 '.' 前的前缀（与 contract 的前缀约定一致）。 */
final class Topics {
    /** 取 topic 的模块前缀；null→null；无 '.' 原样返回。 */
    static String moduleOf(String topic) {
        if (topic == null) return null;
        int i = topic.indexOf('.');
        return i < 0 ? topic : topic.substring(0, i);
    }

    private Topics() {}
}
