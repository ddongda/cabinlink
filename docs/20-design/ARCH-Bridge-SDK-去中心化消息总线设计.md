# Bridge SDK 设计文档 · 去中心化座舱消息总线

> 状态：设计评审中（待用户确认）
> 分支：`feat/bridge-sdk`
> 日期：2026-06-17
> 作者：高级系统工程师（Claude）/ wei.wang

---

## 0. 一句话定位

在 **CabinLink** 基础上另起一套**去中心化**的座舱通信 SDK：**废弃 kernel/services 中心进程**，各业务 App（导航、电话、多媒体、用户中心…）通过 **base + 模块 aar** 接入，按 **静态节点清单**互相 bind 建立点对点长连接，以 **Topic + JSON 信封（通用头部）+ Schema + 消息版本控制** 的 **RPC-over-messaging** 模型收发消息。复用 CabinLink 已用线上事故换来的稳定性/重连/鉴权经验。

---

## 1. 背景与目标

### 1.1 与 CabinLink 的关系

CabinLink（main 分支）是**中心化**方案：依赖独立的 `link-kernel` APK 做注册/发现/健康检查，业务通过"能力契约 + AIDL opcode + Proxy/Skeleton"通信。

本设计（`feat/bridge-sdk` 分支）是一次**通信范式重做**，差异如下：

| 维度 | CabinLink（旧） | Bridge SDK（本设计） |
|------|----------------|---------------------|
| 拓扑 | 中心化，kernel 做注册中心 | **去中心化**，无中心进程 |
| 部署 | 需常驻 kernel APK | **无独立 APK**，逻辑全在 SDK 内 |
| 发现 | kernel 注册表 + waitFor | **静态节点清单** + queryIntentServices 兜底（见 §4） |
| 通信原语 | Call / Property / Event（AIDL opcode + Bundle） | **RPC（request/response）+ Event（pub/sub）**，统一 JSON 信封 |
| 编解码 | Bundle schema | **JSON payload + 通用头部** |
| 版本演进 | opcode 表 + Bundle schema | **消息 schemaVersion 协商** |
| 交付 | link-runtime 单库 | **base aar + 每模块独立 aar（+BOM）** |

> 复用而非抛弃：连接管理、退避重连、Binder 死亡监听、并发集合、Binder 内核身份鉴权等**经过验证的实现**，从 `link-runtime` 移植到 `bridge-core`，详见 §11。

### 1.2 设计目标

- **G1 低耦合**：业务方只依赖 `bridge-core` + 自己关心的模块 aar；模块之间零编译期依赖；没注册某模块就收发不到它，天然隔离。
- **G2 稳定性**：进程崩溃、bind 竞速、内核乱序启动、僵尸 Binder、ANR、身份伪造——逐一有对策（§11）。
- **G3 重连**：连接断开自动退避重连，恢复后自动重注册 + 重订阅，业务零感知。
- **G4 易接入**：集成 aar + `Application.onCreate` 注册 schema，两步即可收发。
- **G5 可演进**：消息带版本号，跨版本兼容；传输层 AIDL 冻结，演进只发生在 topic/schema/payload。

### 1.3 非目标（本期）

- ❌ 不做独立 broker/kernel APK。
- ❌ 不保证消息持久化/离线补发（节点不在线即投递失败，由 RPC 超时暴露）。
- ❌ 本期只交付 **1 个样板模块：多媒体（media）**，跑通全链路；导航/电话/用户中心/车控照样板复制（§12、§14）。
- ❌ 不做跨设备/跨车机通信，仅同一车机内跨进程。

---

## 2. 整体架构与分层

```
┌──────────────────────────────────────────────────────────────┐
│  业务 App（语音适配层 / HMI / 多媒体 App / 导航 App …）         │
│  · 集成 bridge-core + 按需模块 aar                              │
│  · Application.onCreate 注册模块 schema                         │
└───────────────────────────────┬──────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────┐
│  bridge-contract-media / navi / phone / user   （各模块 aar）   │
│  · Topic 常量表（如 media.play / media.state）                 │
│  · Schema：payload 字段定义 + schemaVersion                    │
│  · 强类型门面（可选）：MediaClient.play(...) 内部封 request     │
└───────────────────────────────┬──────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────┐
│  bridge-core   （base aar，业务唯一必需依赖）                   │
│  ┌──────────────┬──────────────┬──────────────────────────┐  │
│  │ NodeRegistry │ ConnectionMgr│ RpcEngine                │  │
│  │ 节点清单/发现 │ 连接网格/重连 │ request 关联+超时         │  │
│  ├──────────────┼──────────────┼──────────────────────────┤  │
│  │ EnvelopeCodec│ Dispatcher   │ AclGuard                 │  │
│  │ 信封编解码    │ 订阅分发/去重 │ 包名白名单+Binder uid 校验│  │
│  └──────────────┴──────────────┴──────────────────────────┘  │
└───────────────────────────────┬──────────────────────────────┘
                                 │  AIDL（冻结区）
┌────────────────────────────────▼─────────────────────────────┐
│  bridge-transport   （唯一跨进程接口，签名冻结）                │
│  IBridgeNode（双向通道：deliver(Envelope) / linkToDeath）       │
└──────────────────────────────────────────────────────────────┘

去中心化拓扑：每个集成 SDK 的 App 都是一个对等 Node，
              按静态清单互相 bindService，建立双向 Binder 长连接。
```

### 2.1 模块清单与依赖方向

| 模块 | 类型 | 职责 | 发布 |
|------|------|------|------|
| `bridge-transport` | aar | 唯一 AIDL（`IBridgeNode` + Envelope Parcelable），**冻结区** | ✅ |
| `bridge-core` | aar | 发现/连接/重连/编解码/RPC引擎/分发/鉴权 | ✅ |
| `bridge-contract-media` | aar | 多媒体 Topic + Schema + 门面（**本期样板**） | ✅ |
| `bridge-contract-navi/phone/user` | aar | 同结构，后续复制 | 规划 |
| `bridge-bom` | pom | 统一版本管理 | ✅ |
| `sample-media-provider` | apk | 多媒体提供方示例（响应 request、发 Event） | 不发布 |
| `sample-voice-consumer` | apk | 消费方示例（发 request、订阅 Event） | 不发布 |

**依赖方向（严格单向）：**

```
业务 App → bridge-contract-X → bridge-core → bridge-transport
```

- `bridge-contract-X` 之间互不依赖（低耦合）。
- `bridge-core` 不依赖任何 contract（不感知具体业务 topic）。
- `bridge-transport` 是唯一跨进程层，AIDL 签名评审后永不修改。

---

## 3. 核心概念

| 概念 | 说明 |
|------|------|
| **Node（节点）** | 一个集成了 SDK 的 App 进程，既可发也可收。节点 id = 包名。 |
| **Topic** | 路由键，点分命名，如 `media.play`（请求）、`media.state`（事件）。 |
| **Schema** | 某模块的协议描述：它提供哪些 request topic、发布哪些 event topic、各 payload 的字段与版本。注册 schema = 声明能力 + 打开收发开关。 |
| **Envelope（信封）** | 跨进程传输的最小单元 = 通用头部 Header + JSON payload。 |
| **RPC** | request → response，带 correlationId 关联 + 超时。 |
| **Event** | publish → 所有订阅者，fire-and-forget。 |

---

## 4. 节点发现与连接（去中心化命门）

### 4.1 静态节点清单（主方案）

车型出厂时节点集合是确定的，用一份**静态清单**描述所有节点：

```json
// bridge_nodes.json（放 bridge-core 的 assets 或业务 App 覆盖）
{
  "version": 1,
  "nodes": [
    { "id": "com.baic.media",  "action": "com.baic.bridge.NODE", "pkg": "com.baic.media" },
    { "id": "com.baic.navi",   "action": "com.baic.bridge.NODE", "pkg": "com.baic.navi" },
    { "id": "com.baic.voice",  "action": "com.baic.bridge.NODE", "pkg": "com.baic.voice" }
  ]
}
```

- 每个节点在 `AndroidManifest` 声明一个带固定 `action`（`com.baic.bridge.NODE`）的 `<service>`，作为被 bind 的入口。
- SDK 启动时读清单，对清单内**除自己外**的每个节点 `bindService` 建立双向 Binder 长连接。
- 新增模块只需更新清单（OTA 可下发新版清单），无需改 SDK 代码。

### 4.2 兜底：queryIntentServices（可选增强）

清单缺失或需要动态性时，用 `PackageManager.queryIntentServices(action=com.baic.bridge.NODE)` 查出所有声明了该 action 的已安装 App，与清单取并集。进程未起也能查到（manifest 静态声明）。

> **决策**：本期以**静态清单为准**（最可控）；queryIntentServices 作为兜底实现但默认关闭，避免误连未授权 App（配合 §11 ACL 白名单）。

### 4.3 连接生命周期

```
SDK.init()
  → 读清单 → 逐节点 bindService（BIND_AUTO_CREATE）
  → onServiceConnected：拿到对端 IBridgeNode，linkToDeath，握手交换"我注册了哪些 topic"
  → 双方更新本地路由表
  → bind 返回 false / onServiceDisconnected / binderDied
      → 退避重连（指数退避，上限 30s）→ 恢复后重新握手 + 重订阅
```

握手报文本身也是一条 Envelope（`type=HELLO`），携带本节点已注册的 request/event topic 列表，使对端建立路由表。

---

## 5. 传输层 AIDL（冻结区）

```aidl
// IBridgeNode.aidl —— 唯一跨进程接口，签名冻结
interface IBridgeNode {
    // 投递一条信封（请求/响应/事件/握手统一走这里）
    void deliver(in BridgeEnvelope envelope);
    // 对端建连时回调，注册反向通道（双向）
    void attach(in IBridgeNode peer, String peerNodeId);
}
```

```aidl
// BridgeEnvelope.aidl —— Parcelable
parcelable BridgeEnvelope;
```

- **唯一**跨进程接口，方法签名一旦评审通过**永不修改**（继承 CabinLink 的 ABI 冻结铁律）。
- 所有演进发生在 Envelope 的 `type` / `topic` / `schemaVersion` / payload，不动 AIDL 签名。
- `deliver` 是双向的：A bind B 后，A 也把自己的 `IBridgeNode` 通过 `attach` 交给 B，形成全双工。

---

## 6. 消息信封与通用头部

```
BridgeEnvelope {
  // ── Header（通用头部，定制）──────────────────
  String  msgId;          // UUID，端到端幂等去重
  int     type;           // REQUEST | RESPONSE | EVENT | HELLO | ERROR
  String  topic;          // 路由键，如 "media.play" / "media.state"
  int     schemaVersion;  // 消息版本，跨版本兼容/协商（见 §9）
  String  source;         // 发送方节点 id（包名）；接收侧用 Binder uid 校验
  String  correlationId;  // REQUEST↔RESPONSE 关联；EVENT/HELLO 留空
  long    timestamp;      // 发送时刻（ms）
  boolean needAck;        // QoS：是否需要回执
  int     code;           // RESPONSE/ERROR 错误码（0=OK）

  // ── Payload ─────────────────────────────────
  String  payload;        // JSON 字符串，按 topic 的 schema 约定
}
```

设计要点：

- **payload 用 JSON 字符串**而非 Bundle：跨版本字段增删容忍度高，调试可读，与 schemaVersion 配合做兼容。
- **Header 与 payload 分离**：路由/去重/版本判断只读 Header，无需反序列化 payload，转发开销低。
- **msgId 去重**：Dispatcher 维护有界 LRU（如最近 1024 条），重连补发场景下幂等。

---

## 7. 通信语义（RPC over messaging）

### 7.1 RPC（主）

```java
// 消费方（如语音）：发起请求，拿成功/失败回执
bridge.request("media.play", "{\"trackId\":\"123\"}", new BridgeReply() {
    @Override public void onSuccess(String payload) { /* code=0 */ }
    @Override public void onError(int code, String msg) { /* 超时/无提供方/业务失败 */ }
}, /*timeoutMs=*/3000);
```

```java
// 提供方（多媒体 App）：注册 request 处理器
bridge.onRequest("media.play", (req, resp) -> {
    boolean ok = player.play(req.get("trackId"));
    if (ok) resp.ok("{\"playState\":1}");
    else    resp.fail(E_DEVICE, "播放器忙");
});
```

- RpcEngine 生成 `correlationId`，按路由表把 REQUEST 定向投给**声明提供该 topic** 的节点。
- 启动超时计时器；收到匹配 `correlationId` 的 RESPONSE 则回调；超时回 `E_TIMEOUT`。
- 无人提供该 topic → 立即回 `E_NO_PROVIDER`。
- 多节点提供同一 request topic → 默认投给清单中**首个**，并告警（请求语义应唯一归属）。

### 7.2 Event（辅）

```java
// 提供方：状态变更广播
bridge.publish("media.state", "{\"playState\":1,\"title\":\"xxx\"}");

// 消费方：订阅
bridge.subscribe("media.state", payload -> updateUi(payload));
```

- publish 推给**所有订阅了该 topic** 的已连接节点。
- subscribe 在重连恢复后由 SDK 自动重订阅（业务零感知）。
- Event 不需回执（除非 needAck），fire-and-forget。

### 7.3 错误码（统一）

| 码 | 含义 |
|----|------|
| 0 | OK |
| E_TIMEOUT | 请求超时 |
| E_NO_PROVIDER | 无节点提供该 topic |
| E_NOT_CONNECTED | 目标节点未连接 |
| E_VERSION | schemaVersion 不兼容 |
| E_ACL | 鉴权拒绝 |
| E_DEVICE / E_BUSY / … | 业务自定义（≥1000） |

---

## 8. 去中心化路由

无 broker，路由表由握手维护：

1. 节点 A 注册了 media schema → 声明"我提供 request: `media.play/pause/next`；我发布 event: `media.state`"。
2. A 与 B 建连握手时，把上述声明发给 B；B 记入 `routeTable[topic] = {nodeId, channel}`。
3. B 要 `request("media.play")` → 查 `routeTable` → 定向投给 A。
4. B `subscribe("media.state")` → 把订阅声明发给所有连接的节点；A publish 时据此推送。

> 关键：**注册 schema 同时声明了"提供/发布"的 topic**，这是去中心化路由的唯一信息来源，也对应你说的"注册+集成 aar 才收发"。

---

## 9. Schema 与消息版本控制

每个模块 aar 内含 schema 声明（代码常量，非运行时文件）：

```java
public final class MediaSchema {
    public static final String MODULE = "media";
    public static final int    VERSION = 1;          // 模块 schemaVersion

    // request topics（本模块提供方处理）
    public static final String PLAY  = "media.play";
    public static final String PAUSE = "media.pause";
    public static final String NEXT  = "media.next";
    public static final String SET_VOLUME = "media.setVolume";

    // event topics（本模块提供方发布）
    public static final String STATE = "media.state";   // playState/title/volume

    // payload 字段名常量（双端共用，避免拼写漂移）
    public static final String K_TRACK_ID = "trackId";
    public static final String K_VOLUME   = "volume";
    public static final String K_PLAY_STATE = "playState";
}
```

**版本协商：**

- Envelope.`schemaVersion` 标记本条消息所用版本。
- 接收方按"**宽进严出**"：能解析的高版本字段忽略未知项；缺失字段取默认；不兼容（如必填语义变更）回 `E_VERSION`。
- 模块大版本不兼容时，topic 改名（如 `media.play.v2`）而非破坏旧 topic。

---

## 10. 接入方式（业务方视角）

**Step 1 — 依赖（Gradle）**

```kotlin
dependencies {
    implementation(platform("com.baic.bridge:bridge-bom:1.0.0"))
    implementation("com.baic.bridge:bridge-core")          // 必需
    implementation("com.baic.bridge:bridge-contract-media") // 按需，只引关心的模块
}
```

**Step 2 — Application.onCreate 注册 schema**

```java
public class VoiceApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Bridge.init(this);                      // 读清单、建连、起重连
        Bridge.register(MediaSchema.MODULE);    // 注册多媒体：打开收发开关
        // 未注册的模块，路由表里没有，收发不到（隔离）
    }
}
```

**Step 3 — 提供方声明 NODE service（AndroidManifest）**

```xml
<service android:name="com.baic.bridge.core.BridgeNodeService"
         android:exported="true"
         android:permission="com.baic.bridge.permission.LINK">
    <intent-filter><action android:name="com.baic.bridge.NODE"/></intent-filter>
</service>
```

之后即可按 §7 收发。提供方崩溃恢复后 SDK 自动 reattach + 重订阅，业务零改动。

---

## 11. 稳定性与重连设计（移植 CabinLink 血泪教训）

| 场景 | 机制 | 来源 |
|------|------|------|
| 对端进程崩溃 | `linkToDeath` → 移除路由表条目 → 进行中的 request 立即回 `E_NOT_CONNECTED` | 移植 |
| 对端恢复重启 | 退避重连 → 重新握手（重建路由）→ 重订阅 → 业务零感知 | 移植 `CabinLink.Attachment` reattach |
| bind 返回 false（开机竞速） | **必须重试**，否则永久断连 | CabinLink 教训#6 |
| 连接抖动 | 指数退避（1→2→4…上限 30s），单线程 daemon scheduler | 移植 |
| 僵尸 Binder | 周期 ping（HELLO 心跳），无响应剔除 | 移植 HealthMonitor |
| 主线程 ANR | Watchdog 独立线程监控主 Looper（业务可选启用） | 移植 |
| 身份伪造 | 接收侧一律用 `Binder.getCallingUid()` 取内核值校验 source，**不信 payload 里的 source** | CabinLink 铁律 |
| ACL | bind 入口加自定义 `signature` 权限 + 包名白名单 | 移植 AclGuard |
| 并发 | 路由表/订阅表用 `ConcurrentHashMap`/`CopyOnWriteArrayList`；request 回调一次性语义用 `AtomicBoolean` CAS | CabinLink 铁律 |
| 重复投递 | msgId LRU 去重（重连补发幂等） | 新增 |
| RPC 悬挂 | 每个 request 带超时计时器，到点回 `E_TIMEOUT` 并清理 | 新增 |

> 持久化进程约束沿用 CabinLink 教训：`persistent` 只保 Application 主进程，NODE service 禁止放 `:子进程`。

---

## 12. 样板模块：多媒体（本期唯一交付）

完整跑通：连接 → 注册 → RPC（play/pause/next/setVolume）→ Event（state 推送）→ 重连恢复。

- `bridge-contract-media`：`MediaSchema` + `MediaClient`（消费方门面）+ `MediaProvider`（提供方门面基类）。
- `sample-media-provider`：实现 `MediaProvider`，响应 request，状态变更 `publish("media.state")`。
- `sample-voice-consumer`：`register("media")`，`MediaClient.play()`，`subscribe("media.state")`。

其余模块（navi/phone/user/car）照此模板复制，只换 Schema 常量与门面方法。

---

## 13. 发布方案

- **构建**：根 `build.gradle.kts` 引入 `maven-publish`；`bridge-transport/core/contract-* + bridge-bom` 各配 `publishing` 块。
- **坐标**：`com.baic.bridge:bridge-core:1.0.0`、`:bridge-contract-media:1.0.0`、`:bridge-bom:1.0.0`。
- **内部 Maven**：`publishing.repositories.maven { url = 公司私服; credentials }`，凭据走 `~/.gradle/gradle.properties`（不入库）。
- **本地 aar 兜底**：`./gradlew :bridge-core:assembleRelease` 产出 aar，供无私服环境直接以 `files(...)` 依赖。
- **版本**：BOM 统一管理，遵循语义化版本；传输层 AIDL 不变 → 主版本不变。

---

## 14. 目录结构（feat/bridge-sdk 分支）

```
cabinlink/                       # 仓库根（与旧 CabinLink 模块并存于同仓不同分支）
├── bridge-transport/            # AIDL 冻结区（IBridgeNode + BridgeEnvelope）
├── bridge-core/                 # base aar：发现/连接/重连/编解码/RPC/分发/ACL
├── bridge-contract-media/       # 样板模块 aar（本期）
├── bridge-bom/                  # BOM 版本管理
├── sample-media-provider/       # 提供方示例 apk
├── sample-voice-consumer/       # 消费方示例 apk
└── docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md  # 本文
```

> 旧 CabinLink 模块（link-*/contract-*）在 main 分支保留；本分支聚焦 Bridge SDK，是否清理旧模块由后续决定（遗留项）。

---

## 15. 遗留项 / 待确认

1. 公司内部 Maven 私服地址与凭据获取方式（影响 §13）。
2. 节点清单是否需要 OTA 动态下发（本期先用打包内置 + 业务覆盖）。
3. 多节点提供同一 request topic 的冲突策略是否需要更细（本期取首个 + 告警）。
4. 是否需要 Event 的可靠投递/补发（本期 fire-and-forget）。
5. 本分支是否最终清理旧 CabinLink 模块。
6. 用户中心模块的具体能力域（登录态/用户信息/账号切换）后续单独定义 Schema。

---

## 附：本设计已确认的决策（brainstorming 结论）

- 重做通信机制，**去中心化、无 broker、无 kernel APK**。
- 交付形态：**base aar + 每模块独立 aar + BOM**；集成 + `onCreate` 注册 schema 才收发。
- 通信语义：**RPC 为主 + Event 辅**，统一 **Topic + JSON 信封（通用头部）+ Schema + 版本控制**。
- 发现机制：**静态节点清单配置**（queryIntentServices 兜底，默认关闭）。
- 本期范围：**仅多媒体 1 个样板模块**跑通全链路。
- 稳定性/重连：**移植 CabinLink 已验证实现**。
- 发布：**内部 Maven，或本地 aar 兜底**。
