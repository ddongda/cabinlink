package com.baic.bridge.core;

import android.util.Log;

import com.baic.bridge.transport.BridgeEnvelope;
import com.baic.bridge.transport.IBridgeNode;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <b>一个对端的"能力表"</b>：远端 {@link IBridgeNode} 通道 + 它在 HELLO 里声明的能力
 * （provide / subscribe 两类 topic）。一个 PeerConnection 对应一个对端节点（key=包名）。
 *
 * <p>生命周期：在本端 bind 成功（onServiceConnected）或被对端 attach 时创建；对端重连时
 * 仅刷新 {@code remote} 引用（见 BridgeCore.onAttach），表本身复用。
 *
 * <p>线程模型：两张 topic 表由 HELLO 在 <b>worker 线程</b>（handleHello → applyTopics）整体替换，
 * 而 {@link #provides}/{@link #subscribes} 在 <b>调用线程</b>（request/publish）被读——读写跨线程，
 * 故用 {@link CopyOnWriteArraySet}（CabinLink 铁律：跨线程集合一律并发容器）。
 */
final class PeerConnection {
    private static final String TAG = "Bridge.Peer";

    /** 对端节点 ID（=对端包名）：既是 ConnectionManager.peers 的键，也是 Envelope 路由寻址键。 */
    final String peerId;
    /** 对端 IBridgeNode 代理：经它 deliver（oneway）投递信封。volatile——对端重连时被刷新。 */
    volatile IBridgeNode remote;
    /** 对端能响应的 <b>RPC</b> request topic：BridgeCore.findProvider 据此挑选提供方。 */
    final Set<String> providedTopics = new CopyOnWriteArraySet<>();
    /** 对端订阅的 <b>event</b> topic（含整模块通配 "module.*"）：BridgeCore.publish 据此定向推送。 */
    final Set<String> subscribedTopics = new CopyOnWriteArraySet<>();

    PeerConnection(String peerId, IBridgeNode remote) {
        this.peerId = peerId;
        this.remote = remote;
    }

    /**
     * 投递信封；底层 deliver 为 oneway，不阻塞。返回 false 表示通道已失效（remote 为空或 RemoteException）。
     */
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

    /** 对端是否能响应该 request topic：精确匹配（RPC 提供能力，无通配）。 */
    boolean provides(String topic) {
        return providedTopics.contains(topic);
    }

    /**
     * 对端是否订阅该 topic：精确匹配，或命中其整模块通配声明（"module.*"）。
     */
    boolean subscribes(String topic) {
        if (topic == null) return false;
        if (subscribedTopics.contains(topic)) return true;
        for (String s : subscribedTopics) {
            if (s.endsWith(".*") && topic.startsWith(s.substring(0, s.length() - 1))) return true;
        }
        return false;
    }
}
