package com.baic.bridge.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RPC 引擎核心单测（纯 JVM，无 Android 依赖）：在途请求的完成、超时、一次性回调（CAS）、按对端失败。
 * 时序用例给足余量（超时 50ms / 等待 2s），避免 CI 抖动。
 */
public class RpcEngineTest {

    private ScheduledExecutorService scheduler;
    private RpcEngine rpc;

    @Before public void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        rpc = new RpcEngine(scheduler);
    }

    @After public void tearDown() {
        scheduler.shutdownNow();
    }

    /** 记录回调次数与内容的 BridgeReply 桩。 */
    private static final class RecordingReply implements BridgeReply {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger successN = new AtomicInteger();
        final AtomicInteger errorN = new AtomicInteger();
        volatile String payload;
        volatile int code;
        volatile String msg;

        @Override public void onSuccess(String p) {
            payload = p; successN.incrementAndGet(); latch.countDown();
        }
        @Override public void onError(int c, String m) {
            code = c; msg = m; errorN.incrementAndGet(); latch.countDown();
        }
        boolean awaitCalled() throws InterruptedException { return latch.await(2, TimeUnit.SECONDS); }
    }

    @Test public void 正常完成回onSuccess() throws Exception {
        RecordingReply r = new RecordingReply();
        rpc.register("c1", "peerA", r, 1000);
        rpc.complete("c1", BridgeErrors.OK, "{\"a\":1}");
        assertTrue(r.awaitCalled());
        assertEquals(1, r.successN.get());
        assertEquals(0, r.errorN.get());
        assertEquals("{\"a\":1}", r.payload);
    }

    @Test public void 错误码回onError() throws Exception {
        RecordingReply r = new RecordingReply();
        rpc.register("c1", "peerA", r, 1000);
        rpc.complete("c1", BridgeErrors.E_NO_PROVIDER, "无提供方");
        assertTrue(r.awaitCalled());
        assertEquals(1, r.errorN.get());
        assertEquals(BridgeErrors.E_NO_PROVIDER, r.code);
        assertEquals("无提供方", r.msg);
    }

    @Test public void 超时回E_TIMEOUT() throws Exception {
        RecordingReply r = new RecordingReply();
        rpc.register("c1", "peerA", r, 50);     // 50ms 后未完成即超时
        assertTrue(r.awaitCalled());
        assertEquals(1, r.errorN.get());
        assertEquals(BridgeErrors.E_TIMEOUT, r.code);
    }

    @Test public void 完成后超时不再二次回调() throws Exception {
        RecordingReply r = new RecordingReply();
        rpc.register("c1", "peerA", r, 100);
        rpc.complete("c1", BridgeErrors.OK, "{}");
        assertTrue(r.awaitCalled());
        Thread.sleep(200);                       // 越过超时点
        assertEquals(1, r.successN.get());        // 仍只 1 次
        assertEquals(0, r.errorN.get());          // 超时已被 cancel + CAS 拦截
    }

    @Test public void 重复完成只回调一次() throws Exception {
        RecordingReply r = new RecordingReply();
        rpc.register("c1", "peerA", r, 1000);
        rpc.complete("c1", BridgeErrors.OK, "{}");
        rpc.complete("c1", BridgeErrors.E_INTERNAL, "二次应被忽略");
        assertTrue(r.awaitCalled());
        Thread.sleep(50);
        assertEquals(1, r.successN.get());
        assertEquals(0, r.errorN.get());
    }

    @Test public void failPeer只失败该对端不误伤其它() throws Exception {
        RecordingReply a = new RecordingReply();
        RecordingReply b = new RecordingReply();
        rpc.register("ca", "peerA", a, 5000);
        rpc.register("cb", "peerB", b, 5000);

        rpc.failPeer("peerA", BridgeErrors.E_NOT_CONNECTED, "断开");

        assertTrue(a.awaitCalled());
        assertEquals(1, a.errorN.get());
        assertEquals(BridgeErrors.E_NOT_CONNECTED, a.code);
        // peerB 的在途请求不受影响
        assertFalse("peerB 不应被回调", b.latch.await(150, TimeUnit.MILLISECONDS));
        assertEquals(0, b.errorN.get());
        assertEquals(0, b.successN.get());
    }

    @Test public void 未知corr完成是幂等空操作() {
        rpc.complete("不存在", BridgeErrors.OK, "{}");   // 不抛异常、无回调即通过
    }
}
