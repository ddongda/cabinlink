# 用手动 DI（组合根）替代 bridge_nodes.json：节点信息由 contract 提供 设计

- 日期：2026-06-24
- 范围：`:core:lite`（注入口 + 删 NodeRegistry）、`:contract:media` / `:contract:usercenter`（新增节点坐标）、`:samples:*`（组合根装配 + 删 assets json）
- 不触碰：`:transport` AIDL 方法签名（冻结区）

## 1. 背景与目标

当前连接目标由各 app 的 `assets/bridge_nodes.json` 静态声明，`NodeRegistry` 在 `BridgeCore.start()`
里读取并解析（运行时 IO）。问题：配置散落、易漂移、运行时解析失败面、与"模块"概念两处维护。

目标：**去掉 `bridge_nodes.json` 逻辑**，让每个 `:contract:<module>` 成为该模块「服务节点坐标」的
唯一来源；消费方在组合根（`Application.onCreate`）把 contract 提供的 `NodeDescriptor`
**注入**给 Bridge（控制反转，手动 DI / Pure DI，不引入任何 DI 框架）。

## 2. 为何是 DI（控制反转）且不违背铁律

- JSON 方案是 core **主动拉**配置；DI 方案是 contract **推** `NodeDescriptor` 给 core。
- 依赖方向：`App（组合根）→ :contract:X（构造 NodeDescriptor）→ :core（暴露注入口）→ :transport`。
- `:contract:X → :core:lite` 已是 `api` 依赖（合法方向），故 contract 可合法构造 core 的
  `NodeDescriptor`；core 只暴露「接收口」，**永不反向依赖 contract**，铁律不破。

## 3. 关键决策（已确认）

| 决策 | 结论 |
|---|---|
| API 形态 | 并入 `register`：`Bridge.register(NodeDescriptor, cb)` 一调用完成「连接 + 模块注册 + 就绪回调」 |
| 契约版本 | **并入 `NodeDescriptor`**（contract 一次性填好），消费方只传描述符 |
| 节点坐标位置 | 新建 `XxxContract` 类承载 `NodeDescriptor`，`Schema` 仍只管 topic/字段常量 |
| JSON 去留 | **完全移除** `NodeRegistry` + 所有 `assets/bridge_nodes.json` |
| 发现机制 | 编译期静态（contract 常量 + 组合根装配），非反射/PackageManager |

## 4. 组件设计

### 4.1 `NodeDescriptor`（`:core:lite`，已 public）

新增 `contractVersion` 字段 + 单模块便捷构造器：

```java
public final class NodeDescriptor {
    public final String id;            // 节点 id = 包名
    public final String action;        // bind intent action
    public final String component;     // "pkg/.Service"；null 则按 action+包名隐式解析
    public final Set<String> modules;  // 该节点提供的模块（onConnected 归属）
    public final int contractVersion;  // 契约门面版本（仅日志），由 contract 填入

    /** 单模块便捷构造（contract 常用）。 */
    public NodeDescriptor(String id, String action, String component, String module, int contractVersion) {
        this(id, action, component,
             module == null ? Collections.emptySet() : Collections.singleton(module),
             contractVersion);
    }

    public NodeDescriptor(String id, String action, String component, Set<String> modules, int contractVersion) {
        this.id = id; this.action = action; this.component = component;
        this.modules = modules != null ? modules : Collections.emptySet();
        this.contractVersion = contractVersion;
    }
}
```

### 4.2 `:contract:X` — 节点坐标唯一来源（新建 `XxxContract`）

```java
// :contract:media
public final class MediaContract {
    public static final NodeDescriptor NODE = new NodeDescriptor(
            "com.baic.media", "com.baic.bridge.NODE", null,
            MediaSchema.MODULE, MediaSchema.VERSION);
    private MediaContract() {}
}

// :contract:usercenter
public final class UserCenterContract {
    public static final NodeDescriptor NODE = new NodeDescriptor(
            "com.baic.usercenter", "com.baic.usercenter.HOST",
            "com.baic.usercenter/.HostService",
            UserCenterSchema.MODULE, UserCenterSchema.VERSION);
    private UserCenterContract() {}
}
```

> 这些常量精确替代原 `bridge_nodes.json` 里每个节点条目（含 usercenter 的自定义 action + component）。

### 4.3 `:core:lite` — 注入口 + 连接发起

**`Bridge` / `BridgeSetup` 门面：**

```java
// 提供方：声明自身模块（不连接外部，version 未知=0）
register(String module)

// 消费方：注入依赖节点 —— 连接 + 模块注册 +（可选）就绪回调
register(NodeDescriptor node)
register(NodeDescriptor node, ModuleCallback callback)

isReady(String module)   // 不变
```

- **移除** `register(String, ModuleCallback)` 与 `register(String, int, ModuleCallback)`：
  版本已并入描述符，回调与「连接目标（消费方）」配对，统一走 `register(NodeDescriptor, cb)`。

**`BridgeCore`：**

- `register(NodeDescriptor node, ModuleCallback cb)`：
  1. 对 `node.modules` 中每个 module：`moduleStates.computeIfAbsent(module, new ModuleState(module, node.contractVersion))`，
     `cb != null` 则加入该模块回调；
  2. `nodeModules.put(node.id, node.modules)`（onConnected 归属）；
  3. 若 `node.id != selfId`：`connections.connect(node)`（去重）；随后 `worker.execute(reevaluate(module))`。
- `register(String module)`：保持现状（建 ModuleState，不连接），供提供方声明自身模块。
- `start()`：**不再 `NodeRegistry.load`**，仅打印启动横幅；连接在 `register(node)`（onCreate 期间）发起。
- 删除 `nodeModules`/`moduleStates` 的 JSON 预填逻辑（原 start() 里遍历 nodes 那段）。

**`ConnectionManager`：**

- 新增 `void connect(NodeDescriptor n)`（原私有 `connect` 提为可被 `BridgeCore` 调用）。
  **self 跳过 + 去重都在此方法内**，单一位置，幂等：
  ```java
  void connect(NodeDescriptor n) {
      if (n == null || n.id == null || n.id.equals(core.selfId())) return; // 不连自己
      if (peers.containsKey(n.id) || !known.add(n.id)) return;             // 已连接/已发起则跳过
      ... // 原 bindService + ServiceConnection + 退避逻辑（含 n.action/n.component 解析）
  }
  ```
  其中 `known` 为新增的 `Set<String>`（已发起连接的 package），onServiceDisconnected/peerLost 不从 `known` 移除
  （BIND_AUTO_CREATE 仍持有该绑定，无需重复 connect）。
- **移除** `connectAll(List<NodeDescriptor>, selfId)`。

### 4.4 删除项

- `core/lite/.../NodeRegistry.java`
- `samples/navi-consumer/src/main/assets/bridge_nodes.json`
- `samples/account-provider/src/main/assets/bridge_nodes.json`
- `samples/media-provider/src/main/assets/bridge_nodes.json`

## 5. 样例装配（组合根）

**navi-consumer（消费方）：**

```java
Bridge.init(this)
      .register(UserCenterContract.NODE, moduleCb("账号"))
      .register(MediaContract.NODE, moduleCb("多媒体"))
      .subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
      .on((topic, payload) -> push(topic + ": " + payload));
```
（不再依赖 assets json；契约版本从 `XxxContract.NODE` 自带，日志照常打印。）

**media-provider / account-provider（提供方）：** 不连接外部，保持
`Bridge.init(this); Bridge.register(MediaSchema.MODULE); Bridge.onRequest(...)`（或其链式等价）。

## 6. 行为与边界

- **连接时机**：`init()` 建 core + `start()`（横幅）；随后每个 `register(node)` 触发一次 `connect`。
  与原「start 一次性 connectAll」相比，连接发起分散到各 register 调用，但都在 `Application.onCreate`
  同步完成，时序等价。
- **去重**：同一 package 多次 `register(node)`（多模块映射到同一节点）只 bind 一次；模块状态各自独立。
- **self 跳过**：`node.id == selfId` 不连接（提供方误用 `register(自身NODE)` 也安全）。
- **onConnected/onReady/onRebooted/isReady**：逻辑不变，`nodeModules` 改由注入填充，更准确。
- **退避重连 / binderDied / onPeerLost**：不变。

## 7. 测试

- 单测（纯 JVM）：
  - `NodeDescriptor` 单模块构造器：modules 单元素集合、contractVersion 正确。
  - 连接去重逻辑若可抽为纯方法则单测（同一 id 二次 register 不重复连接）。
- 真机（既有验证路径）：
  - navi 经 `register(XxxContract.NODE)` 连上 usercenter + media；
  - 日志见「发起连接 node=… modules=…」「模块就绪 …」「契约门面版本=…」；
  - 播放 RPC 全链路 + onConnected/onReady 正常；
  - `kill -9` provider → onRebooted（已验证路径，回归确认）。

## 8. 非目标（YAGNI）

- 不引入 Dagger/Hilt 或 ServiceLoader 自动发现（保持极简、显式、可控）。
- 不保留 JSON 兜底（完全移除）。
- 不改 AIDL，不引入运行时端点热更新。
