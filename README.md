# Bridge SDK

> 车机座舱**去中心化**跨进程通信 SDK，连接导航、电话、多媒体、用户中心、车控等业务 App。无中心进程、无独立 APK，各 App 以 aar 接入，**连接目标由各 `contract` 提供（注入式 DI，无静态 JSON 清单）**，点对点 bind 建立长连接；以 **Topic + JSON 信封 + Schema + 版本控制** 的 RPC/Event 模型收发消息。兼顾**极致稳定性**与**低耦合**。

> 完整设计见 [docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md](docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md)。

---

## 定位

各业务功能分属不同厂商 APK，独立部署、互相解耦。Bridge SDK 提供统一的去中心化通信底座：

| 问题 | Bridge 解法 |
|------|------------|
| 各模块直接 bindService，耦合高 | 统一 SDK + **contract 注入节点坐标**，模块只依赖 contract 的 schema 常量与 `NODE` |
| 需常驻中心进程（broker/kernel） | **去中心化**，无中心进程、无独立 APK |
| 提供方崩溃后消费方无感知 | DeathRecipient 清路由 + 退避重连 + 恢复后自动重订阅，并回调 **`onRebooted()`** 通知消费方 |
| 消费方不知道依赖的模块是否可用 | 注册即开启就绪跟踪：**`onConnected`/`onReady`/`onRebooted` 回调 + `isReady(module)`** |
| 存量 App 已有自己的 service | **lite aar** 挂到宿主已有 Service，不新增 Service 类 |
| 接口版本演进破坏兼容 | 传输层 AIDL 冻结，演进只在 topic/schema/payload + schemaVersion 协商 |
| 跨进程同步调用拖垮线程池/ANR | `deliver` 为 `oneway`，RPC 默认异步，correlationId + 超时配对 |

---

## 架构分层

```
业务 App（导航 / 电话 / 多媒体 / 用户中心 …）
      │  选择接入形态：全量 aar（自带 Service）/ lite aar（挂已有 service）
      ▼
contract/<module>（topic + schema + 门面 + ServiceNode 节点坐标 NODE，模块团队自治）
      │  组合根注入：Bridge.init(ctx).register(XxxContract.NODE, cb)
      ▼
core:full（= core:lite + 托管 BridgeNodeService） / core:lite（内核，无 Service）
      │  AIDL（冻结区）
      ▼
transport（IBridgeNode：deliver(oneway) / attach；BridgeEnvelope）
```

**依赖方向（严格单向）：** `业务 App → :contract:X → :core:full/:core:lite → :transport`

- `:contract:X` 之间互不依赖；`:core` 不感知任何业务 topic。
- `:transport` 是唯一跨进程层，AIDL 评审通过后永不修改签名。

---

## 模块说明

| Gradle 路径 | artifactId | 职责 |
|------------|-----------|------|
| `:transport` | `bridge-transport` | 唯一 AIDL（冻结区）：`IBridgeNode` + `BridgeEnvelope` |
| `:core:lite` | `bridge-core-lite` | 连接/重连/编解码/RPC/分发/鉴权 + 模块就绪跟踪，统一静态门面 `Bridge`（链式 `init`），**无 Service** |
| `:core:full` | `bridge-core` | = `core-lite` + 自带托管 `BridgeNodeService` |
| `:contract:usercenter` | `bridge-contract-usercenter` | 用户中心/账号契约样板（topic + schema + 门面 + `UserCenterContract.NODE`） |
| `:contract:media` | `bridge-contract-media` | 多媒体契约样板（RPC topic + schema + 门面 + `MediaContract.NODE`） |
| `:samples:account-provider` | — | 账号 provider 示例（lite·挂已有 Service） |
| `:samples:navi-consumer` | — | 导航 consumer 示例（lite·纯客户端：订阅账号 + 拉取 + 调媒体） |
| `:samples:media-provider` | — | 多媒体 provider 示例（full·自带 Service） |

---

## 两种接入形态

| 形态 | 依赖 | Service | 适用 |
|------|------|---------|------|
| **全量 aar** | `bridge-core` | 自带（manifest 自动合并） | 全新模块 |
| **lite aar** | `bridge-core-lite` | 复用宿主已有 service / 无 | 已有 service 的 App、纯消费方 |

---

## 通信语义

### RPC（默认异步）

```java
// 消费方
Bridge.request("media.play", "{\"trackId\":\"123\"}", new BridgeReply() {
    public void onSuccess(String payload) { }
    public void onError(int code, String msg) { }
}, 3000);

// 提供方
Bridge.onRequest("media.play", (req, resp) -> {
    if (player.play(req.get("trackId"))) resp.ok("{\"playState\":1}");
    else resp.fail(MediaError.E_PLAYER_BUSY, "播放器忙");
});
```

同步糖 `Bridge.requestSync(topic, payload, timeoutMs)` 仅限工作线程，主线程调用抛 `IllegalStateException`。

**耗时 handler 用 `onRequestAsync`：** `onRequest` 的 handler 在单线程 worker **串行内联**执行，适合快 handler；若 handler 耗时（查库 / 算路 / 解码），会**队头阻塞**后续所有请求与事件。这类改用 `onRequestAsync`——SDK 在**独立线程池并行**执行，不占 worker：

```java
// 耗时处理：独立线程池并行，不阻塞其它请求
Bridge.onRequestAsync("media.play", (req, resp) -> {
    String r = decodeAndPlay(req.get("trackId"));  // 耗时操作
    resp.ok(r);                                     // 可直接回，也可跨线程稍后回
});
```

> async handler 之间并行，访问共享状态需自管并发。若仍用 `onRequest` 处理耗时任务，SDK 检测到 handler 同步占用 worker **≥200ms** 会打告警，引导改用 `onRequestAsync`。

### Event（pub/sub）

```java
Bridge.publish("usercenter.account.state", accountJson);          // 提供方
Bridge.subscribe("usercenter.account.state", payload -> { ... }); // 消费方：单 topic

// 批量订阅：多个 topic 共用一个回调，回调带 topic 以区分来源
Bridge.subscribes("usercenter.account.state", "media.state")
      .on((topic, payload) -> { ... });

// 整模块订阅：该模块（topic 前缀 "usercenter."）下所有 event
Bridge.subscribeAll("usercenter")
      .on((topic, payload) -> { ... });
```

> 整模块订阅的跨进程推送：消费端在握手里声明通配项 `usercenter.*`，发布端按前缀匹配推送——发布方无需感知消费方订了哪些具体 topic。

---

## 连接与就绪（注入式 DI）

连接目标不再写在 `assets/bridge_nodes.json`，而是由各 `:contract:X` 暴露一个 `ServiceNode`（节点包名 + action + component + 模块 + 契约版本）。消费方在组合根（`Application.onCreate`）把它注入给 Bridge——`register(ServiceNode, cb)` 一调用即完成「连接 + 模块注册 + 就绪回调」。这是手动 DI（控制反转）：`contract` 合法依赖 `core` 并构造 `ServiceNode` 推给 `core`，`core` 永不反向依赖 `contract`。

```java
// contract 侧：节点坐标唯一来源
public final class MediaContract {
    public static final ServiceNode NODE = new ServiceNode(
        "com.baic.media", "com.baic.bridge.NODE", null,   // pkg / action / component
        MediaSchema.MODULE, MediaSchema.VERSION);          // 模块 / 契约版本
}

// 消费方组合根：链式注入
Bridge.init(this)
      .register(UserCenterContract.NODE, accountCb)
      .register(MediaContract.NODE, mediaCb)
      .subscribes(UserCenterSchema.ACCOUNT_STATE, MediaSchema.STATE)
      .on((topic, payload) -> { ... });
```

**模块状态回调**（在 SDK worker 线程串行触发，更新 UI 自行切主线程）：

```java
ModuleCallback cb = new ModuleCallback() {
    public void onConnected() { }  // 该模块节点 bind 成功（未握手），排查日志用，重连会再触发
    public void onReady()     { }  // 提供方握手完成、能力首次可用，此后调 RPC 安全
    public void onRebooted()  { }  // 曾就绪的提供方崩溃恢复后再次可用，宜重新拉取状态
};
boolean up = Bridge.isReady(MediaSchema.MODULE);  // 同步查询是否就绪
```

> 非就绪状态调用 `request` 会打印告警并回 `E_NO_PROVIDER`；提供方（被动等连）用 `Bridge.register(MODULE)` 字符串重载声明自身模块，不主动连接。

---

## 快速接入

**消费方（依赖某模块）：**

```kotlin
implementation(project(":core:lite"))      // 纯客户端
implementation(project(":contract:media")) // 提供 MediaContract.NODE
```
```java
Bridge.init(this)                           // Application.onCreate
      .register(MediaContract.NODE, mediaCb);
```

**全新提供方（全量）：**

```kotlin
implementation(project(":core:full"))
implementation(project(":contract:media"))
```
```java
Bridge.init(this);
Bridge.register(MediaSchema.MODULE);        // 声明自身模块
Bridge.onRequest(MediaSchema.PLAY, (req, resp) -> { ... });
```

**已有 service 的 App（lite·挂载）：** 见 [设计文档 §10.2](docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md)。同样 `Bridge.init(this)`，再在宿主 `Service.onBind` 按 action 返回 `Bridge.nodeBinder()`——不新增 Service 类、不增进程。

---

## 契约自治

SDK 团队只提供内核（transport + core）+ 模板 + 规范；各业务模块在自己的 `:contract:<module>` 自助补充 topic、payload schema、特殊错误码、门面。全局只维护一张「模块前缀 + 错误码区间」分配表防冲突（错误码：0=OK，1–999 SDK 保留，≥1000 各模块按区间自定义）。

---

## 稳定性设计

| 场景 | 机制 |
|------|------|
| 对端崩溃 | DeathRecipient 清路由，在途请求回 `E_NOT_CONNECTED`，模块置 `isReady=false` |
| 对端恢复 | `BIND_AUTO_CREATE` 自愈 + 退避重连（指数，上限 30s）→ 重握手 → 重订阅 → 回调 `onRebooted()` |
| bind 返回 false（开机竞速） | 必须重试 |
| 跨进程同步阻塞 | `deliver` oneway；RPC 异步 + 超时；`requestSync` 禁主线程 |
| 身份伪造 | 接收侧 `Binder.getCallingUid()` 校验，不信 payload.source |
| 并发 | `ConcurrentHashMap`/`CopyOnWriteArraySet` + 回调一次性 `AtomicBoolean` CAS |
| 重复投递 | msgId 有界 LRU 去重 |
| 连接重复 | 按 package 去重（同一节点只 bind 一次）+ self 跳过 |
| 慢 handler 队头阻塞 | 耗时处理用 `onRequestAsync`（独立线程池并行）；误用 `onRequest` 同步占 worker ≥200ms 打告警提示 |

> **可观测性：** 启动打印版本指纹 `SDK版本 / gitSha / 构建时间 / 传输ABI`（由 `BuildConfig` 构建期注入）；全链路日志（发起连接 / 请求 / 响应 / 事件 / 模块就绪）统一带 `[包名]` 前缀，多进程 logcat 混排可一眼区分来源（`adb logcat -s Bridge.Core Bridge.Conn`）。

---

## 构建与运行

**环境：** Android SDK 34，minSdk 28，**Java 17**，Gradle 8.9。

> ⚠️ Android Studio 自带 JBR 跑 `jlink`（JdkImageTransform）会失败，命令行构建请用标准 JDK 17：
> `export JAVA_HOME=/path/to/jdk-17`（本机示例：`~/Library/Java/JavaVirtualMachines/liberica-17.0.18`）

```bash
# 全量编译（5 个 aar + 3 个 sample apk）
./gradlew assembleDebug

# 安装两个样板验证账号订阅链路
./gradlew :samples:account-provider:installDebug :samples:navi-consumer:installDebug
```

验证：导航 App 点"主动拉取账号"、用户中心 App 点"登录/切换/登出"，观察导航端账号状态实时刷新。

---

## 目录结构

```
.
├── transport/                   # 传输层 AIDL（冻结区）
├── core/
│   ├── lite/                    # 内核（无 Service），统一门面 Bridge
│   └── full/                    # = core:lite + 托管 BridgeNodeService
├── contract/
│   ├── usercenter/              # 用户中心/账号契约样板
│   └── media/                   # 多媒体契约样板（RPC）
├── samples/
│   ├── account-provider/        # 账号 provider（lite 挂已有 service）
│   ├── navi-consumer/           # 导航 consumer（lite 纯客户端）
│   └── media-provider/          # 多媒体 provider（full 自带 service）
└── docs/20-design/              # Bridge SDK 设计文档
```

---

## License

内部项目，版权归 Baic 新能源所有。
