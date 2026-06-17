# CabinLink × Claude Code 分阶段工作流手册

**原则**：一次会话一个目标；每个 Prompt 自带「锚定→任务→验收→沉淀」四段；
阶段之间靠**知识图谱**衔接（而非靠超长上下文）。禁止一个 Prompt 要求产出多阶段内容。

```
阶段0 工程奠基 ──► 阶段1 输入文档 ──► 阶段2 设计文档 ──► 阶段3 源码开发 ──► 阶段4 测试验证 ──► 阶段5 输出文档
   (已完成)         (3个Prompt)        (2+N个Prompt)      (5个Prompt)        (2个Prompt)       (2个Prompt)
                                    每阶段收尾统一执行 /sync-knowledge（知识沉淀）
```

## Prompt 模板（所有阶段共用骨架）

```text
【锚定】先读 <文件列表>，本任务属于工作流阶段 <X.Y>。
【任务】<单一目标，明确产出物路径>
【边界】只做上述事项。发现的相关问题记入会话纪要遗留项，不展开。
【验收】<可执行的检查：命令 / 文件存在性 / 评审清单>，完成后贴出验收结果。
【沉淀】更新 docs/90-knowledge/ 中受影响的图谱文件 + 新增会话纪要。
```

---

## 阶段 0 · 工程奠基（已由本次会话完成）

产出：目录骨架、CLAUDE.md、本手册、知识图谱初始文件、文档模板、自定义命令。

---

## 阶段 1 · 输入文档建设（3 个独立会话）

### P1.1 需求规格
```text
【锚定】读 ../CabinLink-座舱服务总线架构设计-V2.md、../N80项目对外接口现状统计分析.md、
docs/10-input/REQ-需求规格.md（模板）。
【任务】把 V2 设计的第一部分目标 + N80 现状，落成正式需求规格：功能需求（FR-编号）、
非功能需求（NFR-编号，含量化指标）、用户故事（提供方/消费方/平台运维三视角）。
写入 docs/10-input/REQ-需求规格.md。
【边界】只写需求，不写方案；每条需求必须可验证（有判据）。
【验收】FR/NFR 全部有编号和判据；与 V2 设计 1.1 节量化目标逐条对得上。
【沉淀】/sync-knowledge
```

### P1.2 约束与非目标
```text
【锚定】读 docs/10-input/CONSTRAINT-约束与非目标.md（模板）、V2 设计 1.2 节。
【任务】落成约束文档：平台约束（Android 版本/签名/SELinux）、组织约束（各业务团队
不可改造项，如高德地图）、技术非目标。每条约束注明来源。
【边界】【验收】每条约束有来源引用。【沉淀】/sync-knowledge
```

### P1.3 参考资料索引（知识入口）
```text
【锚定】读 docs/10-input/REF-参考资料索引.md。
【任务】核对并补全索引中每个条目的路径与「看什么」摘要（每条 ≤3 行）；失效路径标注。
【边界】只建索引不搬运内容。【验收】所有路径 ls 可达。【沉淀】/sync-knowledge
```

---

## 阶段 2 · 设计文档建设（2 个固定会话 + 每能力 1 个会话）

### P2.1 架构定稿与 ADR 补录
```text
【锚定】读 ../CabinLink-座舱服务总线架构设计-V2.md、docs/20-design/adr/ADR-0000-模板.md、
ADR-0001（示例）。
【任务】为 V2 设计中已隐含的重大决策各补一份 ADR：
ADR-0002 Call/Event/Property 三原语；ADR-0003 控制面不碰业务数据；
ADR-0004 消费回调主线程+提供方串行executor；ADR-0005 遗留协议adapter桥接。
每份含：背景/选项对比/决策/代价/推翻条件。
【边界】只补录已做决策，不引入新决策。
【验收】每份 ADR 的「代价」段非空（没有代价的决策记录是无效记录）。
【沉淀】/sync-knowledge
```

### P2.2 开发技术文档定稿
```text
【锚定】读 docs/30-dev/ 三个文件（模板）、CLAUDE.md 历史教训段。
【任务】定稿编码规范（线程/并发/错误处理/日志规约，吸收一期教训清单）、
模块开发手册（新增一个 contract 域的标准步骤）、测试策略（单测/IPC集成/崩溃注入三层）。
【验收】规范中每条「禁止项」都附带一期对应事故的引用。【沉淀】/sync-knowledge
```

### P2.3-x 能力契约 SPEC（每能力一个会话，按需复制）
```text
【锚定】读 docs/20-design/contract/SPEC-能力契约模板.md、
../baic-openapi/business-aidl/（一期对应域的 AIDL，作语义参考）。
【任务】编写 baic.<域> 能力契约 SPEC：Call 表（op/参数/超时/错误码）、
Event 表（topic/载荷/频率）、Property 表（id/类型/初值/stale语义）。
【边界】一个会话只写一个能力。
【验收】op/topic/propertyId 无冲突（对照 capability-map.md 的全局分配表）；
每个 Call 有超时值和失败语义。
【沉淀】/sync-knowledge（必须更新 capability-map.md 的 id 分配表）
```

---

## 阶段 3 · 源码开发（5 个会话，严格按序——依赖方向决定顺序）

> 每个会话开头都加：`读 docs/90-knowledge/INDEX.md 与 module-map.md，确认依赖方向`。

### P3.1 link-pipe（唯一 AIDL，最先冻结）
```text
【任务】创建 Gradle 多模块工程骨架（settings.gradle.kts + link-pipe 模块），
实现 ICapabilityPipe/IPipeReply/IPipeCallback 三件套 AIDL（按 V2 设计 3.3 节）。
【验收】./gradlew :link-pipe:build 通过；AIDL 与 V2 设计 3.3 逐行一致。
【沉淀】module-map.md 登记模块；code-landmarks.md 登记「此文件冻结」地标。
```

### P3.2 link-kernel（移植一期稳定性内核）
```text
【锚定】读 ../baic-openapi/openapi-service/ 全部源码（P0 修复后版本）。
【任务】移植 Registry/DeathGuard/ObserverManager/PermissionGuard/Watchdog 到 link-kernel，
注册值从 IBinder 改为 ICapabilityPipe；新增 HealthMonitor（统一 ping，30s 周期，3 次失败判死）。
【边界】只移植+适配，不重写已验证逻辑。
【验收】编译通过；对照一期 P0 修复清单逐项确认未回退（watchdog 主线程/无子进程/并发集合/幂等）。
【沉淀】code-landmarks.md 登记各陷阱点的新位置。
```

### P3.3 link-runtime（双端运行库）
```text
【任务】实现 CabinLink 门面（require/publish）、PipeClient/PipeServer、
PropertyMirror、Reconnector（重放注册+订阅+镜像刷新）、DispatchPolicy、LinkError。
【验收】单测覆盖：重连重放、属性镜像快照合并、超时裁决、错误码映射。
【沉淀】module-map.md + code-landmarks.md。
```

### P3.4 contracts + 手写 Proxy/Skeleton（Phase 1 不上代码生成）
```text
【锚定】读对应 SPEC（P2.3 产物）。
【任务】实现 contract-car 的 Hvac 契约 + 手写 HvacProxy/HvacSkeleton/HvacSchema，
作为后续所有域的模式样板（pattern reference）。
【验收】Proxy/Skeleton 与 SPEC 的 op/topic/propertyId 表逐一对应；样板模式提炼进 DEV-模块开发手册。
【沉淀】capability-map.md 登记实现状态。
```

### P3.5 samples（双 App 验证）
```text
【任务】sample-car-provider + sample-voice-consumer 两个 App，
打通 Call（开空调）/ Property（温度镜像）/ Event 全链路 + 崩溃恢复演示。
【验收】真机/模拟器实测：调用延迟、崩溃恢复时间，对照 NFR 指标记录实测值。
【沉淀】session-log 记录实测数据（后续回归基线）。
```

---

## 阶段 4 · 测试验证（2 个会话）

### P4.1 自动化测试
```text
【任务】按 DEV-测试策略.md 三层补齐：runtime 单测、kernel 并发压测（1000次/s 注册查询）、
崩溃注入集成测试（杀 provider/杀 kernel/杀 consumer 三场景）。
【验收】全部通过 + 压测无锁竞争退化（贴数据）。
```

### P4.2 验收对照
```text
【任务】逐条核对 REQ-需求规格.md 的 FR/NFR，产出验收矩阵（通过/未达/证据链接）。
【验收】未达项必须有归因和计划，不允许「待定」。
```

---

## 阶段 5 · 输出文档建设（2 个会话）

### P5.1 接入指南（面向业务团队）
```text
【锚定】读 docs/40-output/API-接入指南模板.md + samples 实际代码。
【任务】产出提供方/消费方两份接入指南，所有代码片段必须从 samples 摘取（保证可运行）。
【验收】按指南从零接入一个新假想域，步骤无断点。
```

### P5.2 发布说明 + 知识图谱终审
```text
【任务】RELEASE 文档 + 全量跑一次 /sync-knowledge 终审（图谱与代码一致性抽查 5 处）。
```

---

## 横切机制

### /sync-knowledge（每阶段收尾必跑，已配置为自定义命令）
见 `.claude/commands/sync-knowledge.md`。作用：把本次会话产生的事实写回图谱，
让**下一个会话从图谱冷启动**，而不是依赖上一个会话的上下文残留。

### 上下文管理纪律
- 单会话预计超过一个阶段任务量 → 拆分，宁可多开会话；
- 跨会话衔接只靠三样东西：CLAUDE.md（规则）+ 90-knowledge/（事实）+ 当前阶段 Prompt（任务）；
- 大文件分析（如一期源码通读）放独立会话，结论写入图谱，正式开发会话只读图谱结论。

### 阶段门禁
每阶段最后一个会话结束时，人工确认「验收全绿」再进入下一阶段；跳阶段视为流程违规。
