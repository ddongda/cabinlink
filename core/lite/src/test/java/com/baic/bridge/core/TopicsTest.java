package com.baic.bridge.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TopicsTest {
    @Test public void 取首个点前的前缀() {
        assertEquals("media", Topics.moduleOf("media.play"));
        assertEquals("usercenter", Topics.moduleOf("usercenter.account.state"));
    }
    @Test public void 无点时返回原串() {
        assertEquals("media", Topics.moduleOf("media"));
    }
    @Test public void null返回null() {
        assertNull(Topics.moduleOf(null));
    }
}
