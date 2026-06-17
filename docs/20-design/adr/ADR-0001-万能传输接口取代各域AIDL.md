# ADR-0001：万能传输接口（ICapabilityPipe）取代各域 AIDL

- **状态**：已采纳（V2 设计评审前为 draft）
- **日期**：2026-06-12
- **关联**：V2 设计 §3.3、§0.2 洞察 1

## 背景
一期每域一套 AIDL：新增方法即动传输 ABI（transaction id 漂移风险）、内核无法统一
健康检查、196 条遗留接口收编需逐域定义 AIDL，演进成本随域数线性增长。

## 选项
### 选项 A：唯一万能接口 opcode+Bundle，类型安全由双端同源生成代码恢复（采纳）
- 优点：传输 ABI 一次冻结；统一 ping/监控/dumpsys；演进=数据变更
- 缺点：失去 AIDL 编译期跨进程类型检查；Bundle 编组有少量开销
### 选项 B：延续每域 AIDL + 严格追加纪律（被否）
- 否决理由：纪律不可机器强制；内核仍无统一健康口；收编长尾成本高
### 选项 C：JSON 字符串协议（北汽地图现状）（被否）
- 否决理由：无任何编译期保障，正是要收编的对象

## 决策
全总线唯一 `ICapabilityPipe`（invoke/subscribe/snapshot/ping/describe），冻结；
强类型由契约（注解）生成的 Proxy/Skeleton/Schema 双端同源保证，describe() 版本协商兜底。

## 代价
- 类型错配的发现时机从编译期后移到「连接协商期」（仍早于运行期调用）；
- 需要投入并维护代码生成器（Phase 1 以手写模式过渡）；
- 调试时 Bundle 内容不如 AIDL 方法签名直观（以 dumpsys + Schema 表补偿）。

## 推翻条件
- 实测 Bundle 编组在热路径（≥100Hz 事件）造成可感知卡顿且无法用 @Pod Parcelable 解决；
- Android 平台未来提供官方稳定 AIDL versioning 且车机 OS 可用。
