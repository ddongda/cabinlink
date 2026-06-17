# 知识图谱入口（每次会话第一个读的文件）

> 本目录是工程的「外置记忆」。会话之间不依赖上下文残留，依赖这里的事实。
> 更新纪律：谁改了代码/文档，谁在收尾时跑 /sync-knowledge 写回这里。

## 图谱文件

| 文件 | 回答什么问题 | 当前状态 |
|---|---|---|
| [module-map.md](module-map.md) | 有哪些模块？谁依赖谁？每个模块的角色？ | 初始（仅规划态） |
| [capability-map.md](capability-map.md) | 有哪些能力？op/topic/propId 全局分配表？实现状态？ | 初始（仅 hvac 示例） |
| [code-landmarks.md](code-landmarks.md) | 关键类在哪？有哪些陷阱和不变量？冻结区在哪？ | 含一期全部教训 |
| [session-log/](session-log/) | 每次会话做了什么、实测数据、遗留项 | 模板就绪 |

## 工程当前位置

- **阶段**：最小骨架已完成（P3.1+P3.4+P3.5 压缩版，先于需求阶段做形态验证）
  - 6 模块全部编译通过（25 源文件 javac 零 error；AIDL 经 gradle 生成）
  - 已验证形态：万能管道 / Call+Event+Property 三原语 / 手写 Proxy-Skeleton 样板 / 双端范例
  - 最小版欠账：内核无 ACL/Watchdog/HealthMonitor（P3.2 移植一期时补）；无 Reconnector 对 provider 崩溃的 kernel 级补位测试
- **下一步**：真机/模拟器装 3 个 APK 实测 → 回到阶段 1 补需求文档
- **架构定稿**：`../../CabinLink-座舱服务总线架构设计-V2.md`（V2.0-draft，待 P2.1 评审定稿）
- **源码**：cabinlink/ 根下 6 个 Gradle 模块（见 module-map.md）

## 外部资产指针（详见 10-input/REF-参考资料索引.md）

- 一期工程（稳定性内核移植来源）：`../../baic-openapi/`
- L946 MapSDK（域 SDK 形态参考）：`../../L946_MapSDK_V1.0.20/`
- 北汽地图遗留协议（收编对象）：`../../BAIC_地图sdk/`
