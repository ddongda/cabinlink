# CabinLink — 北汽座舱服务总线

> 车机座舱多模块跨进程通信中间件，连接车控、电话、导航、多媒体等业务 App，基于 Android Binder / AIDL 构建，兼顾**稳定性**与**低延迟**。

---

## 目录

- [背景与定位](#背景与定位)
- [整体架构](#整体架构)
- [模块说明](#模块说明)
- [三大通信原语](#三大通信原语)
- [快速接入](#快速接入)
- [能力契约一览](#能力契约一览)
- [稳定性设计](#稳定性设计)
- [构建与运行](#构建与运行)
- [目录结构](#目录结构)

---

## 背景与定位

车机座舱中，**车控（空调/座椅/车窗）、电话、导航、多媒体、语音助手**等功能分属不同厂商 APK，各自独立部署、互相解耦。CabinLink 作为统一的**服务发现与能力路由总线**，解决以下核心问题：

| 问题 | CabinLink 解法 |
|------|---------------|
| 各模块直接 bindService，耦合高 | 通过内核（link-kernel）统一注册/发现，消费方只依赖契约 |
| 提供方崩溃后消费方无感知 | 死亡监听 + 自动 reattach，镜像标记 stale |
| 跨进程属性读写频繁 IPC | 本地属性镜像，读操作 0 IPC |
| 接口版本演进破坏兼容 | 传输层（AIDL）冻结，演进仅在 opcode/Bundle schema |
| 开机启动顺序不确定 | waitFor 机制，内核先于业务启动无需手动等待 |

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        业务 App 层                               │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  语音助手 App │  │  HMI 显示 App │  │  车控 App    │  ...     │
│  │ (消费方)     │  │ (消费方)     │  │ (提供方)     │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                   │
│  ┌──────▼─────────────────▼─────────────────▼───────┐          │
│  │              contract-X  契约层                   │          │
│  │  contract-car │ contract-phone │ contract-navi   │          │
│  │  contract-media  (Proxy / Skeleton / Schema)     │          │
│  └──────────────────────────┬────────────────────────┘          │
│                             │                                   │
│  ┌──────────────────────────▼────────────────────────┐          │
│  │              link-runtime  运行时框架               │          │
│  │  CabinLink门面 │ PipeProxy │ CapabilitySkeleton    │          │
│  │  PropertyMirror │ LinkResult │ 自动重连/重放        │          │
│  └──────────────────────────┬────────────────────────┘          │
└─────────────────────────────│───────────────────────────────────┘
                              │  AIDL（冻结区 ICapabilityPipe）
┌─────────────────────────────▼───────────────────────────────────┐
│                    link-pipe  传输层（AIDL 定义）                 │
│  ICapabilityPipe │ ILinkKernel │ ILinkWatcher                   │
│  IPipeCallback   │ IPipeReply                                   │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────────┐
│                    link-kernel  控制面 APK                        │
│  LinkKernelService（注册/发现/健康/ACL）                          │
│  KernelImpl │ AclGuard │ HealthMonitor │ Watchdog               │
└─────────────────────────────────────────────────────────────────┘
```

**依赖方向（严格单向）：**

```
业务 App  →  contract-X  →  link-runtime  →  link-pipe
                                               ↑
link-kernel  ─────────────────────────────────┘
```

- `link-kernel` 不依赖任何 contract，控制面与业务数据完全隔离
- `contract-X` 之间互不依赖
- `link-pipe` 是唯一的跨进程传输层，AIDL 接口一旦评审通过永不修改签名

---

## 模块说明

### `link-pipe` — 传输层（AIDL 定义）

全总线唯一的跨进程接口定义，属于**冻结区**，评审通过后方法签名不可变更。

| AIDL 文件 | 作用 |
|-----------|------|
| `ICapabilityPipe` | 数据面核心接口：invoke / subscribe / snapshot / ping / describe |
| `ILinkKernel` | 控制面接口：register / query / waitFor / unwatch |
| `ILinkWatcher` | 能力上线/下线异步通知 |
| `IPipeCallback` | Event / Property 推送回调 |
| `IPipeReply` | Call 异步回执（含错误码） |

---

### `link-runtime` — 运行时框架

业务 App 的唯一依赖库，封装所有通信细节。

| 类 | 职责 |
|----|------|
| `CabinLink` | 门面，提供 `publish()` / `require()` 两个核心方法 |
| `CapabilitySkeleton` | 提供方基类，处理 onInvoke 分发、属性推送、事件发送 |
| `PipeProxy` | 消费方基类，管理本地属性镜像、订阅、Call 调用 |
| `PropertyMirror` | 属性本地缓存，读操作 0 IPC，支持 watch 订阅 |
| `CapabilityDescriptor<T>` | 能力描述符，含 ID / 版本 / Proxy 工厂 |
| `LinkResult<T>` | 统一结果封装（ok / fail + 错误码） |
| `Reply<T>` | Call 异步回调接口 |

---

### `link-kernel` — 控制面 APK

独立进程运行，负责**注册表维护**和**能力生命周期管理**，不承载任何业务数据。

| 类 | 职责 |
|----|------|
| `LinkKernelService` | Service 入口，启动 Watchdog + HealthMonitor |
| `KernelImpl` | 注册表（ConcurrentHashMap）+ 观察者列表（CopyOnWriteArrayList） |
| `AclGuard` | 签名级访问控制，pid/uid 取自 Binder 内核值，防伪造 |
| `HealthMonitor` | 独立线程周期 ping 所有注册能力，自动剔除僵尸 Binder |
| `Watchdog` | 独立线程监控主线程 Looper，ANR 自检 |

---

### `contract-car` — 车控能力契约

以**空调（HVAC）**为样板，演示完整的 Proxy / Skeleton / Schema 三件套模式。

| 类 | 职责 |
|----|------|
| `Hvac` | 消费方接口（Call + Property 镜像读 + Event 订阅） |
| `HvacSchema` | 双端协议对照表（opCode / propId / topic 常量） |
| `HvacSkeleton` | 提供方骨架，业务方继承后只写业务逻辑 |
| `HvacProxy` | 消费方代理，由框架自动构造 |

**HVAC 协议定义：**

```
Call opcodes:    OP_SET_AC_POWER=1  |  OP_SET_TEMPERATURE=2
Property ids:    P_AC_ON=1  |  P_TEMPERATURE=2  |  P_FAN_SPEED=3
Event topics:    T_ALERT=1
Capability ID:   "baic.car.hvac"
```

---

### `contract-phone` / `contract-navi` / `contract-media`

与 contract-car 结构相同，契约定义如下：

| 契约 | Capability ID | 主要 Call | 主要 Property | 主要 Event |
|------|--------------|-----------|--------------|-----------|
| 电话 | `baic.phone` | answer / hangup / dial | callState / peerNumber | INCOMING_CALL / CALL_ENDED |
| 导航 | `baic.navi` | navigateTo / cancel | naviState / etaSeconds / remainMeters | TURN_HINT / ARRIVED |
| 多媒体 | `baic.media` | play / pause / next / prev / setVolume | playState / title / volume | TRACK_CHANGED |

---

### `sample-car` — 提供方示例

演示如何实现一个车控能力提供方（`HvacImpl`），以及如何通过 `CabinLink.publish()` 注册到总线。

### `sample-voice` — 消费方示例

演示如何通过 `CabinLink.require()` 获取能力，并使用 Call / Property / Event 三原语与提供方交互。

---

## 三大通信原语

### 1. Call — 远程调用（异步）

```java
// 消费方
hvac.setTemperature(26.0f, result -> {
    if (result.isOk()) { /* 成功 */ }
    else { /* result.code() / result.message() */ }
});
```

```java
// 提供方（HvacSkeleton 子类）
@Override
public void setTemperature(float celsius, ReplySink reply) {
    // 下发 CAN 报文...
    setTemperatureProp(celsius);  // 自动推送订阅者
    reply.ok();
}
```

### 2. Property — 属性镜像（本地读，0 IPC）

```java
// 读本地镜像（无 IPC 开销）
float temp = hvac.temperature();
boolean isOn = hvac.acOn();

// 订阅属性变化
hvac.onTemperature(newTemp -> {
    // 首次回调包含当前快照，后续为增量推送
});
```

### 3. Event — 事件订阅（异步推送）

```java
// 消费方订阅事件
hvac.onAlert(message -> {
    Log.w(TAG, "HVAC alert: " + message);
});
```

```java
// 提供方发送事件
emitAlert("压缩机过热，已保护性关闭");
```

---

## 快速接入

### 提供方（3 步）

**Step 1** — 继承 Skeleton，实现业务方法：

```java
public class HvacImpl extends HvacSkeleton {
    @Override
    public void setAcPower(boolean on, ReplySink reply) {
        CanBus.write(AC_POWER_CMD, on);   // 下发 CAN
        setAcOnProp(on);                   // 更新属性，框架自动推送
        reply.ok();
    }

    @Override
    public void setTemperature(float celsius, ReplySink reply) {
        CanBus.write(TEMP_CMD, celsius);
        setTemperatureProp(celsius);
        reply.ok();
    }
}
```

**Step 2** — 在 Application 中发布：

```java
public class CarApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CabinLink.of(this).publish(new HvacImpl());
    }
}
```

**Step 3** — AndroidManifest 声明 `BIND_KERNEL` Action（框架自动连接内核）。

---

### 消费方（1 步）

```java
CabinLink.of(this).require(Hvac.DESCRIPTOR, hvac -> {
    // Call
    hvac.setTemperature(24.0f, Reply.ignore());

    // Property（本地读）
    Log.d(TAG, "当前温度: " + hvac.temperature());

    // 订阅
    hvac.onTemperature(t -> updateUI(t));
    hvac.onAlert(msg -> showWarning(msg));
});
```

提供方崩溃恢复后，框架自动 reattach，消费方代码**零改动**。

---

## 能力契约一览

| 能力 | 提供方 App | 消费方 App | 状态 |
|------|-----------|-----------|------|
| 空调（HVAC） | 车控 App | 语音/HMI | ✅ 已实现 |
| 电话 | 电话 App | 语音/HMI | ✅ 契约已定义 |
| 导航 | 地图 App | 语音/HMI | ✅ 契约已定义 |
| 多媒体 | 音乐 App | 语音/HMI | ✅ 契约已定义 |
| 座椅 | 车控 App | 语音 | 规划中 |
| 车窗 | 车控 App | 语音 | 规划中 |

---

## 稳定性设计

| 场景 | 机制 |
|------|------|
| 提供方进程崩溃 | Binder DeathRecipient → 内核剔除注册 → 消费方代理标记 stale |
| 提供方恢复重启 | 重新 register → 内核通知 waitFor 观察者 → 消费方自动 reattach（镜像+订阅重建） |
| 内核进程崩溃 | 提供方/消费方监听内核 Binder 死亡，bind 失败也重试，自动重注册 |
| 开机启动乱序 | waitFor 机制：能力未上线时排队，上线后立即回调，无需轮询 |
| 僵尸 Binder | HealthMonitor 周期 ping，无响应自动剔除 |
| 主线程 ANR | Watchdog 独立线程监控主 Looper，超时上报 |
| 签名伪造 | AclGuard 取 Binder 内核 uid，不信任参数传入的身份 |
| 并发写注册表 | ConcurrentHashMap + CopyOnWriteArrayList 无锁读，串行写 |

---

## 构建与运行

**环境要求：** Android SDK 34+，minSdk 28（Android 9），Java 17，Gradle 8.x

```bash
# 全量编译
./gradlew assembleDebug

# 安装内核（需先启动）
./gradlew :link-kernel:installDebug

# 启动内核 Service
adb shell am start-foreground-service \
    -n com.baic.cabinlink.kernel/.LinkKernelService

# 验证 Service 运行状态
adb shell dumpsys activity services com.baic.cabinlink.kernel

# 安装示例 App
./gradlew :sample-car:installDebug :sample-voice:installDebug
```

---

## 目录结构

```
cabinlink/
├── link-pipe/          # 传输层 AIDL（冻结区，不可改签名）
├── link-runtime/       # 运行时框架（业务 App 的唯一依赖）
├── link-kernel/        # 控制面 APK（注册/发现/健康）
├── contract-car/       # 车控能力契约（HVAC 样板）
├── contract-phone/     # 电话能力契约
├── contract-navi/      # 导航能力契约
├── contract-media/     # 多媒体能力契约
├── sample-car/         # 提供方接入示例
├── sample-voice/       # 消费方接入示例
└── docs/               # 架构设计文档
```

---

## License

内部项目，版权归北汽新能源所有。
