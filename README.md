# Bridge SDK

> 车机座舱**去中心化**跨进程通信 SDK，连接导航、电话、多媒体、用户中心、车控等业务 App。无中心进程、无独立 APK，各 App 以 aar 接入，按静态清单互相 bind 建立点对点长连接；以 **Topic + JSON 信封 + Schema + 版本控制** 的 RPC/Event 模型收发消息。兼顾**极致稳定性**与**低耦合**。

> 完整设计见 [docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md](docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md)。

---

## 定位

各业务功能分属不同厂商 APK，独立部署、互相解耦。Bridge SDK 提供统一的去中心化通信底座：

| 问题 | Bridge 解法 |
|------|------------|
| 各模块直接 bindService，耦合高 | 统一 SDK + 静态清单发现，模块只依赖 contract 的 schema 常量 |
| 需常驻中心进程（broker/kernel） | **去中心化**，无中心进程、无独立 APK |
| 提供方崩溃后消费方无感知 | DeathRecipient 清路由 + 退避重连 + 恢复后自动重订阅 |
| 存量 App 已有自己的 service | **lite aar** 挂到宿主已有 Service，不新增 Service 类 |
| 接口版本演进破坏兼容 | 传输层 AIDL 冻结，演进只在 topic/schema/payload + schemaVersion 协商 |
| 跨进程同步调用拖垮线程池/ANR | `deliver` 为 `oneway`，RPC 默认异步，correlationId + 超时配对 |

---

## 架构分层

```
业务 App（导航 / 电话 / 多媒体 / 用户中心 …）
      │  选择接入形态：全量 aar（自带 Service）/ lite aar（挂已有 service）
      ▼
contract/<module>（topic + schema + 门面，模块团队自治）
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
| `:core:lite` | `bridge-core-lite` | 发现/连接/重连/编解码/RPC/分发/鉴权 + `BridgeNodeHost`，**无 Service** |
| `:core:full` | `bridge-core` | = `core-lite` + 自带托管 `BridgeNodeService` |
| `:contract:usercenter` | `bridge-contract-usercenter` | 用户中心/账号契约样板（topic + schema + 门面） |
| `:samples:account-provider` | — | 账号 provider 示例（lite·挂已有 Service） |
| `:samples:navi-consumer` | — | 导航 consumer 示例（lite·纯客户端：订阅账号 + 拉取） |

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
    else resp.fail(MediaError.E_BUSY, "播放器忙");
});
```

同步糖 `Bridge.requestSync(topic, payload, timeoutMs)` 仅限工作线程，主线程调用抛 `IllegalStateException`。

### Event（pub/sub）

```java
Bridge.publish("usercenter.account.state", accountJson);          // 提供方
Bridge.subscribe("usercenter.account.state", payload -> { ... }); // 消费方
```

---

## 快速接入

**全新模块（全量）：**

```kotlin
implementation(project(":core:full"))
implementation(project(":contract:usercenter"))
```
```java
Bridge.init(this);                       // Application.onCreate
Bridge.register(UserCenterSchema.MODULE);
```

**已有 service 的 App（lite·挂载）：** 见 [设计文档 §10.2](docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md)，在宿主 `Service.onBind` 按 action 返回 `Bridge.attachHost(this).getBinder()`。

---

## 契约自治

SDK 团队只提供内核（transport + core）+ 模板 + 规范；各业务模块在自己的 `:contract:<module>` 自助补充 topic、payload schema、特殊错误码、门面。全局只维护一张「模块前缀 + 错误码区间」分配表防冲突（错误码：0=OK，1–999 SDK 保留，≥1000 各模块按区间自定义）。

---

## 稳定性设计

| 场景 | 机制 |
|------|------|
| 对端崩溃 | DeathRecipient 清路由，在途请求回 `E_NOT_CONNECTED` |
| 对端恢复 | 退避重连（指数，上限 30s）→ 重握手 → 重订阅，业务零感知 |
| bind 返回 false（开机竞速） | 必须重试 |
| 跨进程同步阻塞 | `deliver` oneway；RPC 异步 + 超时；`requestSync` 禁主线程 |
| 身份伪造 | 接收侧 `Binder.getCallingUid()` 校验，不信 payload.source |
| 并发 | `ConcurrentHashMap`/`CopyOnWriteArraySet` + 回调一次性 `AtomicBoolean` CAS |
| 重复投递 | msgId 有界 LRU 去重 |

---

## 构建与运行

**环境：** Android SDK 34，minSdk 28，**Java 17**，Gradle 8.9。

> ⚠️ Android Studio 自带 JBR 跑 `jlink`（JdkImageTransform）会失败，命令行构建请用标准 JDK 17：
> `export JAVA_HOME=/path/to/jdk-17`（本机示例：`~/Library/Java/JavaVirtualMachines/liberica-17.0.18`）

```bash
# 全量编译（4 个 aar + 2 个 sample apk）
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
│   ├── lite/                    # 内核（无 Service）+ BridgeNodeHost
│   └── full/                    # = core:lite + 托管 BridgeNodeService
├── contract/
│   └── usercenter/              # 用户中心/账号契约样板
├── samples/
│   ├── account-provider/        # 账号 provider（lite 挂已有 service）
│   └── navi-consumer/           # 导航 consumer（lite 纯客户端）
└── docs/20-design/              # Bridge SDK 设计文档
```

---

## License

内部项目，版权归 Baic 新能源所有。
