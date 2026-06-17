package com.baic.bridge.core;

import android.util.Log;

import com.baic.bridge.transport.BridgeEnvelope;
import com.baic.bridge.transport.IBridgeNode;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 一个对端连接：远端 IBridgeNode + 它在 HELLO 里声明的能力（provide/subscribe topics）。
 * 并发集合用 CopyOnWriteArraySet（CabinLink 铁律）。
 */
final class PeerConnection {
    private static final String TAG = "Bridge.Peer";

    final String peerId;
    volatile IBridgeNode remote;
    final Set<String> providedTopics   = new CopyOnWriteArraySet<>(); // 对端能响应的 request topic
    final Set<String> subscribedTopics = new CopyOnWriteArraySet<>(); // 对端订阅的 event topic

    PeerConnection(String peerId, IBridgeNode remote) {
        this.peerId = peerId;
        this.remote = remote;
    }

    /** 投递信封；底层 deliver 为 oneway，不阻塞。返回 false 表示通道已失效。 */
    boolean send(BridgeEnvelope env) {
        IBridgeNode r = remote;
        if (r == null) return false;
        try {
            r.deliver(env);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "deliver 失败 peer=" + peerId + " " + e);
            return false;
        }
    }

    boolean provides(String topic)   { return providedTopics.contains(topic); }
    boolean subscribes(String topic) { return subscribedTopics.contains(topic); }
}
