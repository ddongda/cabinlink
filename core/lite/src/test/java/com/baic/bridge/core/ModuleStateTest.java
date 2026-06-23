package com.baic.bridge.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModuleStateTest {
    @Test public void 首次就绪触发READY() {
        ModuleState st = new ModuleState("media", 1);
        assertEquals(ModuleState.Event.READY, st.applyReadiness(true));
        assertTrue(st.isReady());
    }

    @Test public void 已就绪再次就绪无事件() {
        ModuleState st = new ModuleState("media", 1);
        st.applyReadiness(true);
        assertEquals(ModuleState.Event.NONE, st.applyReadiness(true));
    }

    @Test public void 就绪后掉线触发LOST且isReady转false() {
        ModuleState st = new ModuleState("media", 1);
        st.applyReadiness(true);
        assertEquals(ModuleState.Event.LOST, st.applyReadiness(false));
        assertFalse(st.isReady());
    }

    @Test public void 掉线后再就绪触发REBOOTED() {
        ModuleState st = new ModuleState("media", 1);
        st.applyReadiness(true);
        st.applyReadiness(false);
        assertEquals(ModuleState.Event.REBOOTED, st.applyReadiness(true));
        assertTrue(st.isReady());
    }

    @Test public void 从未就绪掉线判定无事件() {
        ModuleState st = new ModuleState("media", 1);
        assertEquals(ModuleState.Event.NONE, st.applyReadiness(false));
    }
}
