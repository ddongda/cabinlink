# Contract 节点注入替代 bridge_nodes.json Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 去掉 `bridge_nodes.json` / `NodeRegistry`，连接目标改由各 `:contract:X` 提供 `ServiceNode`，消费方在组合根经 `Bridge.register(ServiceNode, cb)` 注入（手动 DI）。

**Architecture:** `NodeDescriptor` 重命名为 `ServiceNode`（`id→pkg`，加 `contractVersion` + 单模块构造器）。core 暴露注入口 `register(ServiceNode, cb)`：建模块就绪态 + 记录 node→modules + 触发连接（去重、self 跳过）。`start()` 不再读 assets。各 contract 暴露 `XxxContract.NODE` 常量；样例组合根装配。

**Tech Stack:** Java 17、Android Library（AGP 8.1.4 / Gradle 8.9）、JUnit 4。

**构建环境：** `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/liberica-17.0.18`

**设计依据：** `docs/superpowers/specs/2026-06-24-contract节点注入替代json-design.md`

**任务顺序保证编译绿：** Task 1 纯重命名（行为不变，JSON 仍工作）；Task 2 切换为注入式连接（此后 `:samples:navi-consumer` 在 Task 4 修好前不参与编译验证，故 Task 2/3 只验证对应模块）。

---

### Task 1: 重命名 NodeDescriptor → ServiceNode（id→pkg，加 contractVersion）+ 单测

**Files:**
- Create: `core/lite/src/main/java/com/baic/bridge/core/ServiceNode.java`
- Delete: `core/lite/src/main/java/com/baic/bridge/core/NodeDescriptor.java`
- Modify: `core/lite/src/main/java/com/baic/bridge/core/NodeRegistry.java`
- Modify: `core/lite/src/main/java/com/baic/bridge/core/ConnectionManager.java`
- Modify: `core/lite/src/main/java/com/baic/bridge/core/BridgeCore.java`（`start()` 内类型名）
- Test: `core/lite/src/test/java/com/baic/bridge/core/ServiceNodeTest.java`

- [ ] **Step 1: 写失败测试**

创建 `core/lite/src/test/java/com/baic/bridge/core/ServiceNodeTest.java`：

```java
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
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:lite:testDebugUnitTest`
Expected: 编译失败 —— `ServiceNode` 不存在。

- [ ] **Step 3: 创建 ServiceNode.java**

创建 `core/lite/src/main/java/com/baic/bridge/core/ServiceNode.java`：

```java
package com.baic.bridge.core;

import java.util.Collections;
import java.util.Set;

/** 一个可连接的服务节点坐标（由各 :contract:X 提供，经组合根注入给 Bridge）。 */
public final class ServiceNode {
    public final String pkg;           // 节点包名（= 节点唯一标识）
    public final String action;        // bind 用的 intent action
    public final String component;     // 形如 "pkg/.Service"；null 则按 action+包名隐式解析
    public final Set<String> modules;  // 该节点提供的模块（用于 onConnected 归属）；缺省空集
    public final int contractVersion;  // 契约门面版本（仅日志），由 contract 填入

    /** 单模块便捷构造（contract 常用）。 */
    public ServiceNode(String pkg, String action, String component, String module, int contractVersion) {
        this(pkg, action, component,
             module == null ? Collections.<String>emptySet() : Collections.singleton(module),
             contractVersion);
    }

    public ServiceNode(String pkg, String action, String component, Set<String> modules, int contractVersion) {
        this.pkg = pkg; this.action = action; this.component = component;
        this.modules = modules != null ? modules : Collections.<String>emptySet();
        this.contractVersion = contractVersion;
    }
}
```

- [ ] **Step 4: 删除 NodeDescriptor.java**

Run: `git rm core/lite/src/main/java/com/baic/bridge/core/NodeDescriptor.java`

- [ ] **Step 5: NodeRegistry 改用 ServiceNode（过渡，仍读 JSON，版本传 0）**

将 `core/lite/src/main/java/com/baic/bridge/core/NodeRegistry.java` 的 `load` 方法整体替换为（仅类型 `NodeDescriptor→ServiceNode`，构造加 `, 0`；JSON key "id" 不变）：

```java
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
```

- [ ] **Step 6: ConnectionManager 改用 ServiceNode（id→pkg）**

在 `core/lite/src/main/java/com/baic/bridge/core/ConnectionManager.java` 中做以下替换（仅重命名，逻辑不变）：

把 `connectAll`、`connect`、`scheduleReconnect` 三个方法整体替换为：

```java
    void connectAll(List<ServiceNode> nodes, String selfId) {
        for (ServiceNode n : nodes) {
            if (n.pkg == null || n.pkg.equals(selfId)) continue;
            connect(n);
        }
    }

    private void connect(final ServiceNode n) {
        final Intent intent = new Intent(n.action);
        if (n.component != null && n.component.contains("/")) {
            String[] parts = n.component.split("/", 2);
            String pkg = parts[0];
            String cls = parts[1].startsWith(".") ? pkg + parts[1] : parts[1];
            intent.setComponent(new ComponentName(pkg, cls));
        } else {
            intent.setPackage(n.pkg);  // 全量自带 Service：按包名 + action 隐式解析
        }

        final ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                backoff.remove(n.pkg);
                IBridgeNode remote = IBridgeNode.Stub.asInterface(binder);
                PeerConnection pc = new PeerConnection(n.pkg, remote);
                peers.put(n.pkg, pc);
                core.linkDeath(pc);
                core.attachTo(remote);     // 双向：把本端回调通道交给对端
                core.sendHelloTo(pc);      // 声明本端 provide/subscribe
                core.onPeerConnected(n.pkg); // bind 成功 → 触发该节点模块的 onConnected（排查日志）
                Log.i(TAG, p() + "已连接 " + n.pkg);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w(TAG, p() + "连接断开 " + n.pkg + "（等待 BIND_AUTO_CREATE 自动恢复）");
                core.onPeerLost(n.pkg);     // 移除 + 重算模块就绪
            }
        };

        boolean ok;
        try {
            Log.i(TAG, p() + "发起连接 node=" + n.pkg + " action=" + n.action
                    + (n.component != null ? " component=" + n.component : "")
                    + " modules=" + n.modules);
            ok = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            ok = false;
            Log.w(TAG, p() + "bindService 异常 " + n.pkg + " " + e);
        }
        if (!ok) {
            Log.w(TAG, p() + "bindService 返回 false（可能开机竞速），退避重连 " + n.pkg);
            try {
                ctx.unbindService(conn);
            } catch (Exception ignore) {
            }
            scheduleReconnect(n);
        }
    }

    private void scheduleReconnect(final ServiceNode n) {
        Long cur = backoff.get(n.pkg);
        long delay = (cur == null) ? BACKOFF_START : cur;
        long next = Math.min(delay * 2, BACKOFF_MAX);
        backoff.put(n.pkg, next);
        scheduler.schedule(() -> connect(n), delay, TimeUnit.MILLISECONDS);
    }
```

- [ ] **Step 7: BridgeCore.start() 内类型名 NodeDescriptor→ServiceNode**

在 `core/lite/src/main/java/com/baic/bridge/core/BridgeCore.java` 的 `start()` 中，把：

```java
        List<NodeDescriptor> nodes = NodeRegistry.load(ctx);
        for (NodeDescriptor n : nodes) {
            if (n.id != null && !n.modules.isEmpty()) nodeModules.put(n.id, n.modules);
        }
```

替换为：

```java
        List<ServiceNode> nodes = NodeRegistry.load(ctx);
        for (ServiceNode n : nodes) {
            if (n.pkg != null && !n.modules.isEmpty()) nodeModules.put(n.pkg, n.modules);
        }
```

- [ ] **Step 8: 运行单测确认通过**

Run: `./gradlew :core:lite:testDebugUnitTest`
Expected: PASS（含原 Topics 3 + ModuleState 5 + 新 ServiceNode 2 = 10 用例）。

- [ ] **Step 9: 提交**

```bash
git add -A core/lite/src/main/java/com/baic/bridge/core/ core/lite/src/test/java/com/baic/bridge/core/ServiceNodeTest.java
git commit -m "refactor(core): NodeDescriptor 重命名为 ServiceNode（id→pkg）+ contractVersion + 单模块构造（含单测）"
```

---

### Task 2: 切换为注入式连接，删除 JSON/NodeRegistry

**Files:**
- Modify: `core/lite/src/main/java/com/baic/bridge/core/BridgeCore.java`
- Modify: `core/lite/src/main/java/com/baic/bridge/core/ConnectionManager.java`
- Modify: `core/lite/src/main/java/com/baic/bridge/core/Bridge.java`
- Modify: `core/lite/src/main/java/com/baic/bridge/core/BridgeSetup.java`
- Delete: `core/lite/src/main/java/com/baic/bridge/core/NodeRegistry.java`

- [ ] **Step 1: BridgeCore.start() 去掉 JSON 加载，仅打横幅**

将 `BridgeCore.start()` 整体替换为：

```java
    void start() {
        Log.i(TAG, P + "==== Bridge SDK 启动 ====");
        Log.i(TAG, P + "SDK版本=" + BuildConfig.SDK_VERSION + " gitSha=" + BuildConfig.GIT_SHA
                + " 构建=" + BuildConfig.BUILD_TIME + " 传输ABI=" + BridgeEnvelope.ABI_VERSION);
    }
```

随后删除文件顶部不再使用的 `import java.util.List;`（第 17 行附近；`List` 已无其它用处）。

- [ ] **Step 2: BridgeCore register 改造（注入式 + 私有 registerModule）**

将当前的 `register(String module)` 与 `register(String module, int contractVersion, ModuleCallback cb)` 两个方法整体替换为：

```java
    /** 提供方：声明自身模块（不连接外部，契约版本未知=0）。 */
    void register(String module) {
        registerModule(module, 0, null);
    }

    /** 消费方：注入依赖节点 —— 连接 + 模块注册 +（可选）就绪回调。 */
    void register(ServiceNode node, ModuleCallback cb) {
        if (node == null) return;
        if (!node.modules.isEmpty()) nodeModules.put(node.pkg, node.modules);  // onConnected 归属
        for (String module : node.modules) registerModule(module, node.contractVersion, cb);
        connections.connect(node);   // self 跳过 + 去重在 connect 内
    }

    private void registerModule(String module, int contractVersion, ModuleCallback cb) {
        if (module == null) return;
        ModuleState st = moduleStates.computeIfAbsent(module, m -> new ModuleState(m, contractVersion));
        if (cb != null) st.callbacks.add(cb);
        Log.i(TAG, P + "注册模块 " + module + " 契约门面版本=" + contractVersion + " callback=" + (cb != null));
        worker.execute(() -> reevaluate(module));   // 提供方可能已就绪，补一次评估
    }
```

- [ ] **Step 3: ConnectionManager 暴露 connect(ServiceNode) + 去重，移除 connectAll**

将 `ConnectionManager` 的 `connectAll` 与 `connect` 两个方法替换为（`connect` 公开 + `known` 去重 + self 跳过；原连接体改名 `doConnect`）：

```java
    private final java.util.Set<String> known = ConcurrentHashMap.newKeySet();  // 已发起连接的 package

    /** 注入式连接：self 跳过 + 去重（同一 package 只 bind 一次），幂等。 */
    void connect(ServiceNode n) {
        if (n == null || n.pkg == null || n.pkg.equals(core.selfId())) return;   // 不连自己
        if (peers.containsKey(n.pkg) || !known.add(n.pkg)) return;               // 已连接/已发起则跳过
        doConnect(n);
    }

    private void doConnect(final ServiceNode n) {
        final Intent intent = new Intent(n.action);
        if (n.component != null && n.component.contains("/")) {
            String[] parts = n.component.split("/", 2);
            String pkg = parts[0];
            String cls = parts[1].startsWith(".") ? pkg + parts[1] : parts[1];
            intent.setComponent(new ComponentName(pkg, cls));
        } else {
            intent.setPackage(n.pkg);
        }

        final ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                backoff.remove(n.pkg);
                IBridgeNode remote = IBridgeNode.Stub.asInterface(binder);
                PeerConnection pc = new PeerConnection(n.pkg, remote);
                peers.put(n.pkg, pc);
                core.linkDeath(pc);
                core.attachTo(remote);
                core.sendHelloTo(pc);
                core.onPeerConnected(n.pkg);
                Log.i(TAG, p() + "已连接 " + n.pkg);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.w(TAG, p() + "连接断开 " + n.pkg + "（等待 BIND_AUTO_CREATE 自动恢复）");
                core.onPeerLost(n.pkg);
            }
        };

        boolean ok;
        try {
            Log.i(TAG, p() + "发起连接 node=" + n.pkg + " action=" + n.action
                    + (n.component != null ? " component=" + n.component : "")
                    + " modules=" + n.modules);
            ok = ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            ok = false;
            Log.w(TAG, p() + "bindService 异常 " + n.pkg + " " + e);
        }
        if (!ok) {
            Log.w(TAG, p() + "bindService 返回 false（可能开机竞速），退避重连 " + n.pkg);
            try {
                ctx.unbindService(conn);
            } catch (Exception ignore) {
            }
            scheduleReconnect(n);
        }
    }

    private void scheduleReconnect(final ServiceNode n) {
        Long cur = backoff.get(n.pkg);
        long delay = (cur == null) ? BACKOFF_START : cur;
        long next = Math.min(delay * 2, BACKOFF_MAX);
        backoff.put(n.pkg, next);
        scheduler.schedule(() -> doConnect(n), delay, TimeUnit.MILLISECONDS);
    }
```

随后删除 `ConnectionManager` 顶部不再使用的 `import java.util.List;`。

- [ ] **Step 4: 删除 NodeRegistry.java**

Run: `git rm core/lite/src/main/java/com/baic/bridge/core/NodeRegistry.java`

- [ ] **Step 5: Bridge 门面 —— 用 ServiceNode 重载替换 String+cb 重载**

将 `Bridge.java` 中当前的四个 register/isReady 相关方法块（`register(String)`、`register(String, ModuleCallback)`、`register(String, int, ModuleCallback)`、`isReady`）整体替换为：

```java
    /** 提供方：声明自身模块（不连接外部）。 */
    public static void register(String module) { core().register(module); }

    /** 消费方：注入依赖节点（来自 :contract:X 的 XxxContract.NODE）—— 连接 + 模块注册。 */
    public static void register(ServiceNode node) { core().register(node, null); }

    /** 消费方：注入依赖节点并监听状态（onConnected/onReady/onRebooted）。 */
    public static void register(ServiceNode node, ModuleCallback callback) { core().register(node, callback); }

    /** 查询模块是否就绪（提供方已连接且握手完成）。 */
    public static boolean isReady(String module) { return core().isReady(module); }
```

- [ ] **Step 6: BridgeSetup 同步（链式）**

将 `BridgeSetup.java` 中三个 register 方法（`register(String)`、`register(String, ModuleCallback)`、`register(String, int, ModuleCallback)`）整体替换为：

```java
    /** 提供方：声明自身模块（不连接外部）。 */
    public BridgeSetup register(String module) {
        Bridge.register(module);
        return this;
    }

    /** 消费方：注入依赖节点（XxxContract.NODE）—— 连接 + 模块注册。 */
    public BridgeSetup register(ServiceNode node) {
        Bridge.register(node);
        return this;
    }

    /** 消费方：注入依赖节点并监听状态（onConnected/onReady/onRebooted）。 */
    public BridgeSetup register(ServiceNode node, ModuleCallback callback) {
        Bridge.register(node, callback);
        return this;
    }
```

并把类顶部 javadoc 示例里的两行：

```
 *       .register(UserCenterSchema.MODULE, UserCenterSchema.VERSION, cb)
 *       .register(MediaSchema.MODULE, MediaSchema.VERSION, cb)
```

改为：

```
 *       .register(UserCenterContract.NODE, cb)
 *       .register(MediaContract.NODE, cb)
```

- [ ] **Step 7: 编译 core + 单测**

Run: `./gradlew :core:lite:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，10 用例通过。

> 注：此时 `:samples:navi-consumer` 仍用旧 API，全量 assembleDebug 会失败，属预期（Task 4 修复）。

- [ ] **Step 8: 提交**

```bash
git add -A core/lite/src/main/java/com/baic/bridge/core/
git commit -m "feat(core): 连接改为注入式 register(ServiceNode)，删除 NodeRegistry/JSON 加载"
```

---

### Task 3: 各 contract 暴露 ServiceNode 节点坐标

**Files:**
- Create: `contract/media/src/main/java/com/baic/bridge/contract/media/MediaContract.java`
- Create: `contract/usercenter/src/main/java/com/baic/bridge/contract/usercenter/UserCenterContract.java`

- [ ] **Step 1: 创建 MediaContract**

创建 `contract/media/src/main/java/com/baic/bridge/contract/media/MediaContract.java`：

```java
package com.baic.bridge.contract.media;

import com.baic.bridge.core.ServiceNode;

/** 多媒体节点坐标：消费方注入此 NODE 即可连接多媒体提供方（替代 bridge_nodes.json 条目）。 */
public final class MediaContract {
    /** 多媒体提供方：全量形态，按 action 隐式解析（无自定义 component）。 */
    public static final ServiceNode NODE = new ServiceNode(
            "com.baic.media", "com.baic.bridge.NODE", null,
            MediaSchema.MODULE, MediaSchema.VERSION);

    private MediaContract() {}
}
```

- [ ] **Step 2: 创建 UserCenterContract**

创建 `contract/usercenter/src/main/java/com/baic/bridge/contract/usercenter/UserCenterContract.java`：

```java
package com.baic.bridge.contract.usercenter;

import com.baic.bridge.core.ServiceNode;

/** 用户中心节点坐标：消费方注入此 NODE 即可连接账号提供方（替代 bridge_nodes.json 条目）。 */
public final class UserCenterContract {
    /** 账号提供方：lite 挂宿主 Service，需显式 action + component。 */
    public static final ServiceNode NODE = new ServiceNode(
            "com.baic.usercenter", "com.baic.usercenter.HOST",
            "com.baic.usercenter/.HostService",
            UserCenterSchema.MODULE, UserCenterSchema.VERSION);

    private UserCenterContract() {}
}
```

- [ ] **Step 3: 编译两个 contract 模块**

Run: `./gradlew :contract:media:compileDebugJavaWithJavac :contract:usercenter:compileDebugJavaWithJavac`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```bash
git add contract/media/src/main/java/com/baic/bridge/contract/media/MediaContract.java contract/usercenter/src/main/java/com/baic/bridge/contract/usercenter/UserCenterContract.java
git commit -m "feat(contract): media/usercenter 暴露 ServiceNode 节点坐标"
```

---

### Task 4: 样例组合根改注入装配 + 删除所有 bridge_nodes.json

**Files:**
- Modify: `samples/navi-consumer/src/main/java/com/baic/navi/NaviApp.java`
- Delete: `samples/navi-consumer/src/main/assets/bridge_nodes.json`
- Delete: `samples/account-provider/src/main/assets/bridge_nodes.json`
- Delete: `samples/media-provider/src/main/assets/bridge_nodes.json`

- [ ] **Step 1: NaviApp 改为注入 XxxContract.NODE**

修改 `samples/navi-consumer/src/main/java/com/baic/navi/NaviApp.java`：

把 import 区中：

```java
import com.baic.bridge.contract.media.MediaClient;
import com.baic.bridge.contract.media.MediaSchema;
import com.baic.bridge.contract.usercenter.UserCenterClient;
import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;
import com.baic.bridge.core.ModuleCallback;
```

替换为（新增两个 Contract import）：

```java
import com.baic.bridge.contract.media.MediaClient;
import com.baic.bridge.contract.media.MediaContract;
import com.baic.bridge.contract.media.MediaSchema;
import com.baic.bridge.contract.usercenter.UserCenterClient;
import com.baic.bridge.contract.usercenter.UserCenterContract;
import com.baic.bridge.contract.usercenter.UserCenterSchema;
import com.baic.bridge.core.Bridge;
import com.baic.bridge.core.BridgeReply;
import com.baic.bridge.core.ModuleCallback;
```

把 `onCreate()` 里的链式装配块：

```java
        Bridge.init(this)
              .register(UserCenterSchema.MODULE, UserCenterSchema.VERSION, moduleCb("账号"))
              .register(MediaSchema.MODULE, MediaSchema.VERSION, moduleCb("多媒体"))
              // 批量订阅：账号状态 + 媒体状态共用一个回调，按 topic 区分（无需逐个 xxxClient）
              .subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
              .on((topic, payload) -> push(topic + ": " + payload));
```

替换为（节点坐标来自 contract，不再依赖 assets json）：

```java
        Bridge.init(this)
              .register(UserCenterContract.NODE, moduleCb("账号"))
              .register(MediaContract.NODE, moduleCb("多媒体"))
              // 批量订阅：账号状态 + 媒体状态共用一个回调，按 topic 区分（无需逐个 xxxClient）
              .subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
              .on((topic, payload) -> push(topic + ": " + payload));
```

（`moduleCb(...)` 辅助方法、`MediaSchema`/`UserCenterSchema` 的 topic 常量仍在用，保持不动。）

- [ ] **Step 2: 删除三个 bridge_nodes.json**

```bash
git rm samples/navi-consumer/src/main/assets/bridge_nodes.json \
       samples/account-provider/src/main/assets/bridge_nodes.json \
       samples/media-provider/src/main/assets/bridge_nodes.json
```

- [ ] **Step 3: 全量编译 + 单测**

Run: `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/liberica-17.0.18 && ./gradlew :core:lite:testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL，10 单测通过，三个 sample apk 产出。

- [ ] **Step 4: 提交**

```bash
git add -A samples/
git commit -m "feat(samples): navi 组合根注入 XxxContract.NODE，删除所有 bridge_nodes.json"
```

---

### Task 5: 真机回归验证

**Files:** 无（验证）

- [ ] **Step 1: 安装三端**

```bash
D=$(adb devices | awk 'NR==2{print $1}')
for a in account-provider media-provider navi-consumer; do
  adb -s $D install -r -t samples/$a/build/outputs/apk/debug/$a-debug.apk
done
```

- [ ] **Step 2: 干净重启并验证连接/就绪（注入式）**

```bash
D=$(adb devices | awk 'NR==2{print $1}')
for p in com.baic.navi com.baic.media com.baic.usercenter; do adb -s $D shell am force-stop $p; done
adb -s $D logcat -c
adb -s $D shell am start -n com.baic.usercenter/.MainActivity
adb -s $D shell am start -n com.baic.media/.MainActivity
sleep 2
adb -s $D shell am start -n com.baic.navi/.MainActivity
sleep 5
adb -s $D logcat -d -s Bridge.Core Bridge.Conn | grep com.baic.navi
```

Expected（关键行）：
- `[com.baic.navi] 发起连接 node=com.baic.usercenter ... modules=[usercenter]`
- `[com.baic.navi] 发起连接 node=com.baic.media ... modules=[media]`
- `[com.baic.navi] 模块就绪 usercenter` 与 `模块就绪 media`
- `[com.baic.navi] 注册模块 media 契约门面版本=1`（版本来自 MediaContract.NODE）

- [ ] **Step 3: 播放 RPC 全链路 + onRebooted 回归**

按 `docs/superpowers/specs/2026-06-24-contract节点注入替代json-design.md` §7：点击播放确认 `播放成功`；`adb shell am crash com.baic.media`（或 root 下 `kill -9`）确认 `模块提供方已离线 → 已连接 → 模块提供方重启恢复`。

- [ ] **Step 4: 记录验证结果**

全绿则本计划完成。

---

## 验证命令汇总

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/liberica-17.0.18
./gradlew :core:lite:testDebugUnitTest   # 纯逻辑单测（10 用例）
./gradlew assembleDebug                   # 全量编译
adb logcat -s Bridge.Core Bridge.Conn     # 真机观察
```

## 备注

- 提供方（media/account）不主动连接，删除其空 `bridge_nodes.json` 无影响：被动等消费方 bind + `onAttach` 建反向通道（与现状一致）。
- 端点固化到编译期，丧失"不重编改端点"能力（设计已确认接受，完全移除 JSON）。
