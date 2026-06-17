# Bridge SDK 设计文档 · 去中心化座舱消息总线

> 状态：设计评审中（待用户确认）
> 分支：`feat/bridge-sdk`
> 日期：2026-06-17
> 作者：高级系统工程师（Claude）/ wei.wang

---

## 0. 一句话定位

在 **CabinLink** 基础上另起一套**去中心化**的座舱通信 SDK：**废弃 kernel/services 中心进程**，各业务 App（导航、电话、多媒体、用户中心…）通过 **base + 模块 aar** 接入，按 **静态节点清单**互相 bind 建立点对点长连接，以 **Topic + JSON 信封（通用头部）+ Schema + 消息版本控制** 的 **RPC-over-messaging** 模型收发消息。支持 **全量 / lite** 两种接入形态：全新模块用**全量 aar**（自带 Service），存量已有 service 的 App 用 **lite aar**（在已有 service 里挂载）。复用 CabinLink 已用线上事故换来的稳定性/重连/鉴权经验。

---

## 1. 背景与目标

### 1.1 与 CabinLink 的关系

CabinLink（main 分支）是**中心化**方案：依赖独立的 `link-kernel` APK 做注册/发现/健康检查。本设计（`feat/bridge-sdk` 分支）是一次**通信范式重做**：

| 维度 | CabinLink（旧） | Bridge SDK（本设计） |
|------|----------------|---------------------|
| 拓扑 | 中心化，kernel 做注册中心 | **去中心化**，无中心进程 |
| 部署 | 需常驻 kernel APK | **无独立 APK**，逻辑全在 SDK 内 |
| 发现 | kernel 注册表 + waitFor | **静态节点清单**（queryIntentServices 兜底，§4） |
| 通信原语 | Call / Property / Event | **RPC（request/response）+ Event（pub/sub）** |
| 编解码 | Bundle schema | **JSON payload + 通用头部** |
| 接入形态 | 单一 runtime | **全量 / lite** 两档（§10） |
| 交付 | link-runtime 单库 | **base aar + 每模块独立 aar（+BOM）** |

> 复用而非抛弃：连接管理、退避重连、Binder 死亡监听、并发集合、内核身份鉴权等**经过验证的实现**，从 `link-runtime` 移植到 `bridge-core`（详见 §12）。

### 1.2 设计目标

- **G1 低耦合**：业务方只依赖 `bridge-core(-lite)` + 自己关心的模块 aar；模块之间零编译期依赖；没注册某模块就收发不到，天然隔离。
- **G2 稳定性**：进程崩溃、bind 竞速、内核乱序启动、僵尸 Binder、ANR、身份伪造——逐一有对策（§12）。
- **G3 重连**：连接断开自动退避重连，恢复后自动重注册 + 重订阅，业务零感知。
- **G4 易接入 + 适配存量**：全新模块集成**全量 aar** 两步即用（自带 Service）；存量已有 service 的 App 用 **lite aar** 在已有 service 里挂载，不强加新 Service（§10）。
- **G5 可演进**：消息带版本号，跨版本兼容；传输层 AIDL 冻结，演进只发生在 topic/schema/payload。

### 1.3 本期范围

- ✅ 框架：`bridge-transport` + `bridge-core`（全量）+ `bridge-core-lite`（无 service 内核）。
- ✅ 样板契约 **2 个**：`bridge-contract-media`（多媒体，验证 RPC）+ `bridge-contract-usercenter`（用户中心/账号，验证 Event 跨模块订阅）。
- ✅ 契约脚手架：`bridge-contract-template` 模板 + 契约编写规范（§9.1–9.3），供各模块团队**自助补充**自己的 topic/特殊错误码/门面。
- ✅ 端到端示例：账号 provider 发布登录态 → 多媒体/导航消费方订阅账号状态 + 调多媒体 RPC，演示两种接入形态各一。
- ❌ 不做独立 broker/kernel APK；不做消息持久化/离线补发；不做跨车机通信。
- ❌ 导航/电话/车控完整契约后续按样板复制（§14）。

---

## 2. 整体架构与分层

```
┌──────────────────────────────────────────────────────────────┐
│  业务 App（多媒体 / 导航 / 电话 / 用户中心 / 语音 …）          │
│  · 选择接入形态：全量 aar（自带 Service）/ lite aar（挂已有）  │
│  · Application.onCreate 注册关心的模块 schema                  │
└───────────────────────────────┬──────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────┐
│  contract/media · contract/usercenter · contract/navi …（模块 aar）│
│  · Topic 常量表 + Schema(payload字段+schemaVersion) + 强类型门面 │
│  · 与 core 形态无关，全量/lite 通用                            │
└───────────────────────────────┬──────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────┐
│  core/full （全量 = lite + 托管 Service）                      │
│    └─ BridgeNodeService（自带、manifest 自动合并，被 bind 入口）│
│  core/lite （内核，无 Android Service 组件）                   │
│  ┌──────────────┬──────────────┬──────────────────────────┐  │
│  │ NodeRegistry │ ConnectionMgr│ RpcEngine                │  │
│  │ 节点清单/发现 │ 连接网格/重连 │ request 关联+超时         │  │
│  ├──────────────┼──────────────┼──────────────────────────┤  │
│  │ EnvelopeCodec│ Dispatcher   │ AclGuard                 │  │
│  │ 信封编解码    │ 订阅分发/去重 │ 包名白名单+Binder uid 校验│  │
│  ├──────────────┴──────────────┴──────────────────────────┤  │
│  │ BridgeNodeHost：供 lite 宿主在已有 Service 暴露 Stub      │  │
│  └──────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬──────────────────────────────┘
                                 │  AIDL（冻结区）
┌────────────────────────────────▼─────────────────────────────┐
│  transport   （唯一跨进程接口，签名冻结）                       │
│  IBridgeNode：deliver(Envelope) / attach(peer, id)             │
└──────────────────────────────────────────────────────────────┘

去中心化拓扑：每个集成 SDK 的 App 是一个对等 Node，按静态清单互相建连
              （全量=框架自带 Service 被 bind；
                lite=挂到宿主已有 Service，或退化为纯客户端 callback）。
```

### 2.1 模块清单与依赖方向

> 约定：**仓库目录按分组**（`contract/media`、`core/lite`…），**发布坐标保留 `bridge-` 前缀**（artifactId 在各模块脚本里显式声明，不用 Gradle 默认的目录名）。

| Gradle 路径 | artifactId（坐标） | 职责 | 发布 |
|------------|-------------------|------|------|
| `:transport` | `bridge-transport` | 唯一 AIDL（`IBridgeNode` + `BridgeEnvelope`），**冻结区** | ✅ |
| `:core:lite` | `bridge-core-lite` | 发现/连接/重连/编解码/RPC/分发/鉴权/`BridgeNodeHost`，**无 Service** | ✅ |
| `:core:full` | `bridge-core` | = `core-lite` + 自带 `BridgeNodeService`（托管接入） | ✅ |
| `:contract:template` | `bridge-contract-template` | 契约脚手架模板（供模块团队 copy） | ✅ |
| `:contract:media` | `bridge-contract-media` | 多媒体 Topic+Schema+门面（**本期样板**） | ✅ |
| `:contract:usercenter` | `bridge-contract-usercenter` | 用户中心/账号 Topic+Schema+门面（**本期样板**） | ✅ |
| `:contract:navi/phone/car` | `bridge-contract-navi/...` | 同结构，后续复制 | 规划 |
| `:bom` | `bridge-bom` | 统一版本管理 | ✅ |
| `:samples:account-provider` | — | 账号提供方（lite·挂已有 service 示例） | 不发布 |
| `:samples:media-provider` | — | 多媒体提供方（全量·自带 service 示例） | 不发布 |
| `:samples:navi-consumer` | — | 消费方（lite·纯客户端：订阅账号 + 调多媒体） | 不发布 |

**依赖方向（严格单向，继承 CabinLink 铁律）：**

```
业务 App → :contract:X → :core:full / :core:lite → :transport
```

- `:contract:X` 之间互不依赖；`:core` 不依赖任何 contract（不感知具体业务 topic）。
- `:core:full` 依赖 `:core:lite`；业务**二选一**：全新模块选 `bridge-core`（自带 Service），已有 service 选 `bridge-core-lite`（挂载）。
- `:transport` 是唯一跨进程层，AIDL 签名评审后永不修改。

---

## 3. 核心概念

| 概念 | 说明 |
|------|------|
| **Node** | 一个集成 SDK 的 App 进程，节点 id = 包名。 |
| **Topic** | 路由键，点分命名：`media.play`（请求）、`usercenter.account.state`（事件）。 |
| **Schema** | 模块协议：提供哪些 request topic、发布哪些 event topic、payload 字段与版本。注册 schema = 声明能力 + 打开收发开关。 |
| **Envelope** | 跨进程最小单元 = 通用头部 Header + JSON payload。 |
| **RPC** | request → response，correlationId 关联 + 超时。 |
| **Event** | publish → 所有订阅者，fire-and-forget。 |

---

## 4. 节点发现与连接

### 4.1 静态节点清单（主方案）

```json
// bridge_nodes.json（bridge-core assets，业务可覆盖；OTA 可下发）
{
  "version": 1,
  "nodes": [
    { "id": "com.baic.media",      "action": "com.baic.bridge.NODE" },
    { "id": "com.baic.usercenter", "action": "com.baic.usercenter.HOST",
      "component": "com.baic.usercenter/.HostService" }, // lite·挂已有 service：指向宿主组件
    { "id": "com.baic.navi",       "action": "com.baic.bridge.NODE" }
  ]
}
```

- 清单条目支持两种入口表达：
  - **全量/默认**：只给 `action=com.baic.bridge.NODE`，bind 框架自带的 `BridgeNodeService`。
  - **lite·挂已有 service**：给 `component`（指向宿主已有 Service）+ 自定义 `action`，bind 宿主组件（§10.2）。
- SDK 启动读清单，对清单内除自己外的每个节点建连。
- 新增模块只更新清单（可 OTA），无需改 SDK。

### 4.2 兜底：queryIntentServices（默认关闭）

`PackageManager.queryIntentServices(action=com.baic.bridge.NODE)` 查出已声明该 action 的 App，与清单取并集。本期**以静态清单为准**，兜底默认关，避免误连未授权 App（配合 §12 ACL）。

### 4.3 连接生命周期

```
SDK.init() → 读清单 → 逐节点建连
  → onServiceConnected：拿对端 IBridgeNode，linkToDeath，
     HELLO 握手交换「我注册了哪些 topic」→ 双方更新路由表
  → bind=false / onServiceDisconnected / binderDied
     → 指数退避重连（上限 30s）→ 恢复后重新握手 + 重订阅
```

握手报文是一条 `type=HELLO` 的 Envelope，携带本节点已注册的 request/event topic 列表，对端据此建路由表。

---

## 5. 传输层 AIDL（冻结区）

```aidl
// IBridgeNode.aidl —— 唯一跨进程接口，签名冻结
interface IBridgeNode {
    oneway void deliver(in BridgeEnvelope envelope);  // oneway：投递即返回，不阻塞调用线程
    void attach(IBridgeNode peer, String peerNodeId); // 注册反向通道（双向/lite回调），建连时同步确认一次
}
```

```aidl
// BridgeEnvelope.aidl —— Parcelable
parcelable BridgeEnvelope;
```

- **唯一**跨进程接口，方法签名评审通过后**永不修改**（继承 ABI 冻结铁律）。
- 演进只发生在 Envelope 的 `type`/`topic`/`schemaVersion`/payload。
- `attach` 是 lite 形态的关键：宿主无框架 Service 时，bind 对端后用 `attach` 把自己的回调 Binder 交过去，对端据此回推 response/event（标准 AIDL callback）。
- `deliver` 用 **`oneway`**：所有信封（请求/响应/事件/握手）投递即返回，**绝不同步阻塞调用线程**——跨进程对端再慢也不会拖垮本端 Binder 线程池或触发 ANR。请求-响应的"配对"由上层 `correlationId` + 超时计时器完成，而非 Binder 的同步返回值。

---

## 6. 消息信封与通用头部

```
BridgeEnvelope {
  // ── Header（通用头部，定制）──────────────────
  String  msgId;          // UUID，端到端幂等去重
  int     type;           // REQUEST | RESPONSE | EVENT | HELLO | ERROR
  String  topic;          // 路由键
  int     schemaVersion;  // 消息版本，跨版本协商（§9）
  String  source;         // 发送方节点 id；接收侧以 Binder uid 校验，不信此字段
  String  correlationId;  // REQUEST↔RESPONSE 关联；EVENT/HELLO 留空
  long    timestamp;      // 发送时刻 ms
  boolean needAck;        // QoS：是否需回执
  int     code;           // RESPONSE/ERROR 错误码（0=OK）
  // ── Payload ─────────────────────────────────
  String  payload;        // JSON 字符串，按 topic 的 schema 约定
}
```

- payload 用 JSON 字符串：跨版本字段增删容忍度高、可读、配合 schemaVersion 做兼容。
- Header 与 payload 分离：路由/去重/版本判断只读 Header，转发不反序列化 payload，开销低。
- msgId 去重：Dispatcher 维护有界 LRU（最近 1024 条），重连补发幂等。

---

## 7. 通信语义

### 7.1 RPC（主）

```java
// 消费方
bridge.request("media.play", "{\"trackId\":\"123\"}", new BridgeReply() {
    public void onSuccess(String payload) { }
    public void onError(int code, String msg) { }   // 超时/无提供方/业务失败
}, /*timeoutMs=*/3000);

// 提供方
bridge.onRequest("media.play", (req, resp) -> {
    if (player.play(req.get("trackId"))) resp.ok("{\"playState\":1}");
    else resp.fail(E_DEVICE, "播放器忙");
});
```

- RpcEngine 生成 correlationId，按路由表定向投给提供该 topic 的节点；超时回 `E_TIMEOUT`；无人提供回 `E_NO_PROVIDER`；多提供方默认投首个并告警。

**同步还是异步？默认异步（车机稳定性硬约束）。**

- `request(...)` **异步非阻塞**：立即返回，结果走 `BridgeReply` 回调（onSuccess / onError，含超时），不占用调用线程。底层 `deliver` 为 `oneway`（§5），request→response 是两次单向投递，靠 `correlationId` 配对。
- **为什么不做成同步**：跨进程对端可能正忙/正在重连/卡顿，同步阻塞主线程 = ANR，且会耗尽有限的 Binder 线程池（默认 16）。
- 回调线程：默认 SDK worker 线程；`BridgeReply` 可声明 `callbackOnMain` 切回主线程（UI 友好，内部 `Handler.post`）。
- **同步糖**（仅工作线程）：`requestSync(topic, payload, timeoutMs)` 内部用 latch 等异步结果再返回，便于顺序逻辑；**检测到主线程调用直接抛 `IllegalStateException`**，从 API 层杜绝主线程阻塞。

```java
// 工作线程内的顺序逻辑（禁止主线程调用）
BridgeResult r = bridge.requestSync("media.play", "{\"trackId\":\"123\"}", 3000);
if (r.isOk()) { /* r.payload() */ } else { /* r.code() / r.msg() */ }
```

### 7.2 Event（辅）

```java
bridge.publish("usercenter.account.state", "{\"loginState\":1,\"userId\":\"u8\"}");
bridge.subscribe("usercenter.account.state", payload -> refreshAccount(payload));
```

- publish 推给所有订阅该 topic 的已连接节点；重连恢复后 SDK 自动重订阅；默认 fire-and-forget。

### 7.3 错误码

| 码 | 含义 |
|----|------|
| 0 | OK |
| E_TIMEOUT | 请求超时 |
| E_NO_PROVIDER | 无节点提供该 topic |
| E_NOT_CONNECTED | 目标未连接 |
| E_VERSION | schemaVersion 不兼容 |
| E_ACL | 鉴权拒绝 |
| 1–999 | SDK 保留（含上列 + 预留） |
| ≥1000 | 各模块特殊错误码，按分配区间自定义（§9.2，模块团队在自己 contract 里定义） |

---

## 8. 去中心化路由

无 broker，路由表由握手维护：

1. 节点注册 schema → 声明「我提供 request: `media.play/...`；我发布 event: `usercenter.account.state`」。
2. 建连握手时把声明发给对端 → 对端记 `routeTable[topic] = {nodeId, channel}`。
3. `request(topic)` 查 routeTable 定向投递；`subscribe(topic)` 把订阅声明广播给所连节点，publish 时据此推送。

> **注册 schema 即声明 topic** 是去中心化路由的唯一信息来源，也对应「注册 + 集成 aar 才收发」。

---

## 9. Schema、契约自治与版本控制

模块 aar 内含 schema 常量（非运行时文件）：

```java
// :contract:usercenter（bridge-contract-usercenter）：用户中心/账号
public final class UsercenterSchema {
    public static final String MODULE = "usercenter";
    public static final int    VERSION = 1;

    // request：消费方主动拉取
    public static final String GET_ACCOUNT = "usercenter.getAccount";   // 拉当前账号（首屏）
    // event：用户中心 provider 发布
    public static final String ACCOUNT_STATE = "usercenter.account.state"; // 登录/登出/切换/资料变更

    // payload 字段常量（双端共用，防拼写漂移）
    public static final String K_LOGIN_STATE = "loginState"; // 0未登录 1已登录
    public static final String K_USER_ID     = "userId";     // 账号用户ID（payload 字段，非模块名）
    public static final String K_NICKNAME    = "nickname";
    public static final String K_AVATAR      = "avatar";
    public static final String K_VIP_LEVEL   = "vipLevel";
}
```

**版本协商**：Envelope.`schemaVersion` 标记本条版本；接收方「宽进严出」——高版本未知字段忽略、缺失字段取默认、不兼容回 `E_VERSION`；大版本不兼容时 topic 改名（`usercenter.account.state.v2`）而非破坏旧 topic。

### 9.1 职责边界：SDK 提供内核，模块自治契约

| 角色 | 交付物 | 维护方 |
|------|--------|--------|
| **SDK 团队** | `transport` + `core(-lite)` + 契约模板/脚手架 + 1 个样板契约 + 本规范 | 统一维护，稳定 |
| **各业务模块团队** | 自己的 `:contract:<module>`：topic、payload schema、特殊错误码、强类型门面 | 模块自治 |

- SDK **不感知**任何业务 topic/错误码（core 不依赖 contract），各模块在自己的 contract 里**自助补充**定制内容，互不影响。
- contract 独立发版：模块改自己的 topic/字段只发自己的 aar，不触动 SDK 与其它模块。

### 9.2 命名空间分配（防冲突，唯一需要全局协调的事）

去中心化下 topic 与错误码是分布式定义的，靠一张轻量**全局分配表**防撞车（文档维护，登记即生效）：

| 资源 | 规则 | 示例 |
|------|------|------|
| **模块前缀** | `<module>.` 全局唯一，topic 一律带前缀 | `media.` / `usercenter.` / `navi.` |
| **topic 命名** | `<module>.<action>`，小写点分 | `media.play` / `usercenter.account.state` |
| **错误码区间** | 0=OK；1–999 SDK 保留；每模块分配一段 ≥1000 | media 1000–1999；usercenter 2000–2999 |
| **schemaVersion** | 模块自管自己的版本号 | `UsercenterSchema.VERSION=1` |

> 全局分配表（模块前缀 + 错误码区间）由 SDK 团队维护一份登记文档；新模块接入前先登记前缀与区间——这是去中心化方案里**唯一的中心化协调点**。
>
> 约定：**模块标识全链路一致** —— Gradle 路径 `:contract:<module>` = artifactId `bridge-contract-<module>` = topic 前缀 `<module>.` = `Schema.MODULE`。

### 9.3 契约模板骨架（模块团队 copy 后自助补充）

```
contract/<module>/   (:contract:<module> → bridge-contract-<module>)
├── <Module>Schema.java   // MODULE / VERSION / topic 常量 / payload key 常量
├── <Module>Error.java    // 模块特殊错误码（在分配区间内，≥1000）
├── <Module>Client.java   // 消费方门面（可选：封装 request/subscribe 为强类型方法）
└── <Module>Provider.java // 提供方门面基类（可选：封装 onRequest/publish）
```

```java
// <Module>Error.java 示例（media 区间 1000–1999）
public final class MediaError {
    public static final int E_DEVICE = 1001; // 播放器异常
    public static final int E_BUSY   = 1002; // 播放器忙
    public static final int E_SOURCE = 1003; // 片源不可用
}
```

SDK 提供 1 个样板契约（media/usercenter）作范例 + `bridge-contract-template` 脚手架，模块团队照此补充自己的 topic/错误码/门面即可，无需改动 SDK。

---

## 10. 两种接入形态（全新模块 / 存量已有 service）

> 差异只在 **是否自带 Service**：全新模块用全量 aar（自带托管 Service）；已有 service 的 App 用 lite aar（在已有 service 里挂载，或退化为纯客户端）。contract 模块两者通用。

### 10.1 全量 aar —— 全新模块，自带 Service，框架全托管

适用：从零接入的新 App，既要提供能力也要消费。

```kotlin
implementation(platform("com.baic.bridge:bridge-bom:1.0.0"))
implementation("com.baic.bridge:bridge-core")            // 含 BridgeNodeService
implementation("com.baic.bridge:bridge-contract-media")
```

```java
public class MediaApp extends Application {
  public void onCreate() {
    super.onCreate();
    Bridge.init(this);                    // 读清单、建连、起重连
    Bridge.register(MediaSchema.MODULE);  // 声明提供 media.* 能力
    Bridge.onRequest(MediaSchema.PLAY, (req, resp) -> { /* ... */ });
  }
}
```

- `BridgeNodeService`（含 `<action com.baic.bridge.NODE>`）由 aar **manifest 自动合并**注入，业务**无需声明 Service**。
- 被别人 bind、双向通道、provider+consumer 全能力，最省心。

### 10.2 lite aar —— 存量已有 service 的 App，在已有 service 里挂载

适用：App **已有自己的对外 service / 通信入口**，引入 lite 在已有 service 里挂 Bridge 能力，不新增 Service 类、不增进程。lite 提供无 Service 的内核（含 `BridgeNodeHost`），由宿主决定如何挂载——有两种用法：

```kotlin
implementation("com.baic.bridge:bridge-core-lite")             // 无 Service，含 BridgeNodeHost
implementation("com.baic.bridge:bridge-contract-usercenter")
```

**用法 A：挂到宿主已有 Service（可提供能力 + 消费）** ← 主用法

① 宿主在已有 Service 的 intent-filter 增加一个 action（只加几行）：

```xml
<service android:name=".HostService" android:exported="true"
         android:permission="com.baic.bridge.permission.LINK">
    <intent-filter><action android:name="com.baic.usercenter.HOST"/></intent-filter>
    <!-- 复用同一 Service 承载 Bridge 通道，无需新增 Service 类 -->
</service>
```

② 宿主 onBind 按 action 返回 Bridge Stub：

```java
public class HostService extends Service {
  private BridgeNodeHost bridgeHost;   // 来自 bridge-core-lite
  public void onCreate() {
    super.onCreate();
    bridgeHost = Bridge.attachHost(this);          // 把 Bridge 内核挂到宿主进程
    bridgeHost.register(UsercenterSchema.MODULE);
    bridgeHost.onRequest(UsercenterSchema.GET_ACCOUNT, (req, resp) -> resp.ok(currentAccountJson()));
    // 账号变化时：bridgeHost.publish(UsercenterSchema.ACCOUNT_STATE, accountJson());
  }
  public IBinder onBind(Intent intent) {
    if (BridgeNodeHost.ACTION.equals(intent.getAction())) return bridgeHost.getBinder(); // Bridge 通道
    return mHostOwnBinder;                                                                // 宿主原有通道
  }
}
```

- 连接方按清单 `component=com.baic.usercenter/.HostService` + `action=com.baic.usercenter.HOST` bind 宿主 service，拿到 `bridgeHost.getBinder()`（即 `IBridgeNode.Stub`）。
- **不新增 Service 类、不增进程、复用宿主生命周期**，只改 intent-filter + onBind 几行。
- 重连/死亡监听由连接方框架负责，与全量形态一致。

**用法 B：纯客户端（只消费，不提供能力）**

若该 App 只想订阅/调用别人（如导航只"关注账号状态"），连已有 service 都不必挂，直接起纯客户端：

```java
public class NaviApp extends Application {
  public void onCreate() {
    super.onCreate();
    Bridge.initLite(this);                          // 只起客户端：主动 bind 目标 + attach 回调
    Bridge.register(UsercenterSchema.MODULE);
    Bridge.subscribe(UsercenterSchema.ACCOUNT_STATE, p -> updateAccountUi(p)); // 订阅账号状态
    Bridge.request(UsercenterSchema.GET_ACCOUNT, "{}", reply, 3000);           // 首屏主动拉一次
  }
}
```

- 零 manifest 改动、无 Service、不被别人发现；通过 `attach(callback)` 接收 response/event。
- 限制：不能作为 provider 被别人 `request`。要提供能力请用「用法 A」或全量形态。

### 10.3 两档对比

| 维度 | 全量 aar | lite aar |
|------|---------|---------|
| 依赖 | `bridge-core` | `bridge-core-lite` |
| Service | 自带（manifest 自动合并） | 复用宿主已有 service（用法A）/ 无（用法B） |
| 能否被 request（provider） | ✅ | 用法A ✅ / 用法B ❌ |
| 能否消费（request/subscribe） | ✅ | ✅ |
| manifest 改动 | 无 | 用法A：已有 service 加 1 个 action；用法B：无 |
| 典型对象 | 全新模块 | 已有 service 的账号/车控（A）；只订阅的导航/多媒体（B） |

---

## 11. 账号状态：跨模块 Event 订阅（需求①样板）

端到端链路，验证低耦合：

```
[用户中心 App]                    [多媒体 App]            [导航 App]
lite·挂已有 service               全量·自带 service       lite·纯客户端
com.baic.usercenter               com.baic.media          com.baic.navi
  provider:                       consumer+provider       consumer
  publish usercenter.account.state ─────┬───────────────────────┐
  onRequest usercenter.getAccount       ▼                       ▼
                              subscribe(ACCOUNT_STATE)  subscribe(ACCOUNT_STATE)
                              登录态变 → 切换在线歌单     登录态变 → 切换个人收藏地点
```

- **用户中心**（lite·挂已有 service，§10.2 用法A）：账号登录/登出/切换/资料变更时 `publish("usercenter.account.state")`；响应 `usercenter.getAccount` 供消费方首屏主动拉取。
- **多媒体 / 导航**：`register("usercenter")` + `subscribe("usercenter.account.state")`，登录态变化驱动各自业务；只依赖 `bridge-contract-usercenter` 的 schema 常量，**不依赖用户中心实现**（低耦合）。
- 首屏时序问题（消费方启动时账号事件已发过）由 `usercenter.getAccount` 主动拉取兜底，避免漏掉最后状态。

---

## 12. 稳定性与重连设计（移植 CabinLink 血泪教训）

| 场景 | 机制 | 来源 |
|------|------|------|
| 对端崩溃 | `linkToDeath` → 移路由表 → 进行中 request 回 `E_NOT_CONNECTED` | 移植 |
| 对端恢复 | 退避重连 → 重握手（重建路由）→ 重订阅 → 业务零感知 | 移植 |
| bind 返回 false（开机竞速） | **必须重试**，否则永久断连 | CabinLink 教训#6 |
| 连接抖动 | 指数退避（1→2→4…上限 30s），单线程 daemon scheduler | 移植 |
| 僵尸 Binder | HELLO 心跳 ping，无响应剔除 | 移植 HealthMonitor |
| 主线程 ANR | Watchdog 独立线程监控主 Looper（禁止 post 死循环给自己） | CabinLink 教训 |
| 身份伪造 | 接收侧一律 `Binder.getCallingUid()` 校验，**不信 payload.source** | CabinLink 铁律 |
| ACL | bind 入口加自定义 `signature` 权限 + 包名白名单；不向无权限方暴露裸 Binder | 移植 AclGuard |
| 并发 | 路由表/订阅表用 `ConcurrentHashMap`/`CopyOnWriteArrayList`；request 回调一次性用 `AtomicBoolean` CAS | CabinLink 铁律 |
| 重复投递 | msgId LRU 去重 | 新增 |
| RPC 悬挂 | 每 request 带超时计时器，到点回 `E_TIMEOUT` 清理 | 新增 |
| 进程约束 | `persistent` 只保 Application 主进程，NODE service 禁止放 `:子进程` | CabinLink 教训 |

> lite·纯客户端额外注意：无 service，其回调 Binder 随宿主进程存活；进程被回收后由 `attach` 重建，连接方需容忍 callback 失效（按 `E_NOT_CONNECTED` 处理）。

---

## 13. 发布方案

- **构建**：根脚本引入 `maven-publish`；`:transport / :core:lite / :core:full / :contract:* / :bom` 各配 `publishing`，并**显式声明 artifactId**（带 `bridge-` 前缀，不沿用 Gradle 默认目录名）。
- **坐标**：
  - `com.baic.bridge:bridge-transport:1.0.0`
  - `com.baic.bridge:bridge-core:1.0.0`、`:bridge-core-lite:1.0.0`
  - `com.baic.bridge:bridge-contract-media:1.0.0`、`:bridge-contract-usercenter:1.0.0`、`:bridge-contract-template:1.0.0`
  - `com.baic.bridge:bridge-bom:1.0.0`
- **内部 Maven**：`publishing.repositories.maven { url=公司私服; credentials }`，凭据走 `~/.gradle/gradle.properties`（不入库）。
- **本地 aar 兜底**：`./gradlew :core:full:assembleRelease` 产出 aar，无私服时以 `files(...)` 依赖。
- **版本**：BOM 统一；AIDL 不变 → 主版本不变。

---

## 14. 目录结构（feat/bridge-sdk 分支）

```
cabinlink/
├── transport/                   :transport            → bridge-transport（AIDL 冻结区）
├── core/
│   ├── lite/                    :core:lite            → bridge-core-lite（无 Service）+ BridgeNodeHost
│   └── full/                    :core:full            → bridge-core（= lite + BridgeNodeService 托管）
├── contract/
│   ├── template/                :contract:template    → bridge-contract-template（脚手架）
│   ├── media/                   :contract:media       → bridge-contract-media（多媒体样板）
│   └── usercenter/              :contract:usercenter  → bridge-contract-usercenter（账号样板）
├── bom/                         :bom                  → bridge-bom
├── samples/
│   ├── account-provider/        # 账号 provider（lite·挂已有 service 示例）
│   ├── media-provider/          # 多媒体 provider（全量·自带 service 示例）
│   └── navi-consumer/           # 消费方（lite·纯客户端：订阅账号+调多媒体）
└── docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md
```

> 仓库目录按分组（`core/`、`contract/`、`samples/`），Gradle 用 `:core:lite` 这类路径；**发布坐标在各模块脚本里显式设回 `bridge-` 前缀**。
> 旧 CabinLink 模块（link-*/contract-*）在 main 分支保留；本分支聚焦 Bridge SDK，旧模块是否清理见遗留项。

---

## 15. 遗留项 / 待确认

1. 公司内部 Maven 私服地址与凭据获取方式（§13）。
2. 节点清单是否需 OTA 动态下发（本期内置 + 业务覆盖）。
3. 多节点提供同一 request topic 的冲突策略是否需更细（本期取首个 + 告警）。
4. Event 是否需可靠投递/补发（本期 fire-and-forget + 消费方 getAccount 兜底）。
5. 本分支是否最终清理旧 CabinLink 模块。
6. 「模块前缀 + 错误码区间」全局分配表由谁维护、放哪（建议 SDK 团队在 `docs/20-design/` 维护一份登记表，§9.2）。

---

## 附：本设计已确认的决策

- 去中心化、无 broker、无 kernel APK。
- 交付：`transport`(AIDL冻结) + `core:full`(全量) / `core:lite`(无service) + 每模块 aar + BOM。
- 工程组织：**仓库目录分组**（`:core:lite`、`:contract:media`…）+ **坐标保留 `bridge-` 前缀**；模块标识全链路一致（路径=artifactId 后缀=topic 前缀=Schema.MODULE）。
- 接入形态：**全量 / lite** 两档——全新模块用全量 aar（自带 Service）；已有 service 的 App 用 lite aar（在已有 service 里挂载，或退化为纯客户端）。
- 通信：RPC 为主（默认异步，`deliver` oneway，提供 `requestSync` 同步糖且禁主线程）+ Event 辅，Topic + JSON 信封（通用头部）+ Schema + 版本控制。
- 发现：静态节点清单（清单条目可指向宿主 service 以支持 lite 挂载），queryIntentServices 兜底默认关。
- 契约自治：SDK 提供内核 + 模板 + 样板契约 + 编写规范；各模块在自己的 `:contract:<module>` **自助补充** topic/特殊错误码/门面；全局只维护一张「模块前缀 + 错误码区间」分配表防冲突。
- 本期样板：多媒体（RPC）+ 用户中心/账号（Event 跨模块订阅）。
- 账号字段：loginState/userId/nickname/avatar/vipLevel（已确认足够）。
- 稳定性/重连：移植 CabinLink 已验证实现。
- 发布：内部 Maven 或本地 aar 兜底。
