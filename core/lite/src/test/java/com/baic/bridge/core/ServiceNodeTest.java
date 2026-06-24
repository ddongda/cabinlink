package com.baic.bridge.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServiceNodeTest {
    @Test public void 单模块构造_字段正确且modules为单元素集() {
        ServiceNode n = new ServiceNode("com.baic.media", "com.baic.bridge.NODE", null, "media", 3);
        assertEquals("com.baic.media", n.pkg);
        assertEquals("com.baic.bridge.NODE", n.action);
        assertEquals(1, n.modules.size());
        assertTrue(n.modules.contains("media"));
        assertEquals(3, n.contractVersion);
    }

    @Test public void module为null时modules为空集() {
        ServiceNode n = new ServiceNode("p", "a", null, (String) null, 0);
        assertTrue(n.modules.isEmpty());
    }
}
