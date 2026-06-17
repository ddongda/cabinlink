# 代码地标（关键位置 / 陷阱 / 不变量 / 冻结区）

> 更新时机：每个 P3.x 会话收尾。格式：地标名 → 位置 → 为什么重要。
> 「陷阱」段是一期 baic-openapi 用真实 bug 换来的，移植时逐条对照，禁止回退。

## 冻结区（改动需架构评审）

| 区域 | 位置 | 冻结原因 |
|---|---|---|
| ICapabilityPipe 三件套 | link-pipe/src/main/aidl/com/baic/cabinlink/pipe/ | 全总线唯一传输 ABI（文件头有冻结标注） |
| LinkError 错误码保留段 101~199 | link-runtime/（待登记） | 总线级语义，业务不可占用 |

## 一期教训清单（移植/新写时逐条自查）

| # | 陷阱 | 一期事故 | 正确做法 |
|---|---|---|---|
| 1 | Watchdog 监控对象 | `new Handler(getLooper())` 监控了自己；死循环 post 给自己导致每 10s 自杀 | 打卡任务投 `Looper.getMainLooper()`；检查逻辑在独立线程 while+sleep |
| 2 | 进程模型 | Service 放 `:openapi` 子进程，persistent 失效 | persistent 只保主进程，Service 不设 android:process |
| 3 | pending 队列并发 | ConcurrentHashMap 的 value 用了裸 ArrayList | CopyOnWriteArrayList + 入队后双检 + AtomicBoolean 终态守卫 |
| 4 | 鉴权参数 | L946 把 pid/uid 当接口参数由客户端传入（D1 缺陷） | 一律 Binder.getCallingPid()/Uid() |
| 5 | 公开白名单 | baic.voice 整个 Binder 对无权限进程暴露 | 永不裸暴露 Binder/pipe；只读需求开专用只读 Call |
| 6 | 首连失败 | bindService 返回 false 不重试 → 开机竞速永久断连 | bound==false 也 scheduleRetry |
| 7 | 重启恢复 | 依赖 onOpenApiRestarted 回调（重启后 observer 已消失，永不触发） | SDK 在 onServiceConnected 时自动重放（V2 扩展为注册+订阅+镜像） |
| 8 | 线程池 | L946 max=Integer.MAX_VALUE + AbortPolicy | 有界 core=4/max=16/queue=256 + CallerRunsPolicy |
| 9 | Stub 单例 | L946 onBind 每次 new 导致状态分裂 | 进程级单例 Stub |
| 10 | 音量恢复 | 写死 setVolume(50) 丢失原始值 | 操作前记录原值，恢复用原值（一般化：所有「降级-恢复」操作都要快照原状态） |

## 不变量（任何改动不得破坏）

1. 控制面（link-kernel）的任何代码路径不接触业务 Bundle 内容；
2. 消费方回调默认主线程，提供方业务方法默认能力级串行；
3. Property 订阅的第一个回调必是全量快照；
4. 同一 PendingEntry 的 onBound/onTimeout 至多触发一次。

## 地标登记（P3.x 起逐步填充）

| 地标 | 位置 | 说明 |
|---|---|---|
| 三原语样板 | contract-car/.../HvacProxy.java、HvacSkeleton.java | 未来 APT 输出形态的手写参照，新域照此模式 |
| id 分配表 | contract-car/.../HvacSchema.java | 改此文件必须同步 capability-map.md |
| 属性自动推送 | link-runtime/.../CapabilitySkeleton.java Properties.set() | set 即推送+订阅补快照（不变量#3 实现点） |
| 超时一次性裁决 | link-runtime/.../PipeProxy.java call() | AtomicBoolean CAS（教训#3 实现点） |
| 重放入口 | link-runtime/.../CabinLink.java onServiceConnected | 发布重放+等待重放（教训#7 实现点） |
| bind false 重试 | link-runtime/.../CabinLink.java connect() | 教训#6 实现点 |
| 常驻 watcher | link-kernel/.../KernelImpl.java waitFor | 崩溃恢复后再次 onAvailable（reattach 依据） |
