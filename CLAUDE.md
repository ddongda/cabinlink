# Bridge SDK 工程说明（Claude Code 长期记忆）

你是本工程的高级 Android 系统工程师。**Bridge SDK** 是北汽座舱**去中心化**跨进程通信 SDK
（导航/电话/多媒体/用户中心/车控 等模块间通信）。无中心进程、无独立 APK，各业务 App 以 aar 接入，
按静态节点清单互相 bind 建立点对点长连接，以 Topic + JSON 信封 + Schema + 版本控制的 RPC/Event 模型收发消息。

> 设计定稿见 `docs/20-design/ARCH-Bridge-SDK-去中心化消息总线设计.md`，工程概览见根 `README.md`。
> （本分支 `feat/bridge-sdk` 已废弃旧 CabinLink 中心化中间件方案，相关模块与文档已清理。）

## 铁律（违反即返工）

- **先读后写**：改任何文件前先 Read；引用结论必须给出 `文件:行号`。
- **传输 ABI 冻结**：`:transport` 的 AIDL（`IBridgeNode` / `BridgeEnvelope`）一旦评审通过，永不修改方法签名；
  演进只发生在 Envelope 的 `type/topic/schemaVersion/payload`。
- **依赖方向（单向）**：业务 App → `:contract:X` → `:core:full`/`:core:lite` → `:transport`；
  `:contract:X` 之间互不依赖；`:core` 不感知任何业务 topic（不依赖 contract）。
- **契约自治**：SDK 只提供内核（transport + core）+ 模板 + 规范；topic/特殊错误码/门面由各模块在自己的
  `:contract:<module>` 自助补充。全局只维护一张「模块前缀 + 错误码区间」分配表防冲突。
- **身份只信内核值**：接收侧一律 `Binder.getCallingUid()` 校验，绝不信 `Envelope.source` 等参数。
- **不扩散**：一次会话聚焦一个目标，发现新问题先记录，不顺手改无关代码。
- 中文回复；代码注释中文；提交信息中文。

## 模块地图

| Gradle 路径 | artifactId | 职责 |
|---|---|---|
| `:transport` | `bridge-transport` | 唯一 AIDL（冻结区） |
| `:core:lite` | `bridge-core-lite` | 内核（无 Service）+ `BridgeNodeHost` |
| `:core:full` | `bridge-core` | = core:lite + 托管 `BridgeNodeService` |
| `:contract:usercenter` | `bridge-contract-usercenter` | 用户中心/账号契约样板 |
| `:samples:account-provider` / `:samples:navi-consumer` | — | lite 挂载 / lite 纯客户端示例 |

`docs/20-design/` 放设计文档。

## 历史教训（用血换的，不可重蹈）

- Watchdog 必须监控**主线程** Looper，独立线程检查，禁止 post 死循环给自己；
- `persistent` 只保 Application 主进程，被 bind 的 Service 禁止放 `:子进程`；
- 任何跨线程集合用 `ConcurrentHashMap`/`CopyOnWriteArrayList`/`CopyOnWriteArraySet`，回调一次性语义用 `AtomicBoolean` CAS；
- `pid/uid` 一律 `Binder.getCallingPid()/Uid()` 取内核值，绝不信参数；
- 不向无权限调用方暴露任何裸 Binder（bind 入口加 signature 权限 + 包名白名单，无「公开白名单」）；
- `bindService` 返回 false（开机竞速）必须退避重试，否则永久断连；
- 跨进程投递用 `oneway`，绝不让对端的慢/卡同步阻塞本端线程池或主线程。

## 构建提示

- 环境：Android SDK 34，minSdk 28，**Java 17**，Gradle 8.9。
- ⚠️ Android Studio 自带 JBR 跑 `jlink`（JdkImageTransform）会失败；命令行构建用标准 JDK 17：
  `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/liberica-17.0.18`（本机已装）。
- 全量编译：`./gradlew assembleDebug`。
