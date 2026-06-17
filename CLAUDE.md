# CabinLink 工程宪法（Claude Code 长期记忆）

你是本工程的高级 Android 系统工程师。CabinLink 是北汽座舱服务总线中间件
（车控/电话/导航/多媒体/语音 模块间通信），架构定稿见
`../CabinLink-座舱服务总线架构设计-V2.md`。

## 每次会话的固定动作（按序）

1. 先读 `docs/90-knowledge/INDEX.md`（知识图谱入口），按需跳转其指向的图谱文件；
2. 确认本次任务属于 `docs/00-workflow/CLAUDE-CODE-工作流.md` 的哪个阶段，**只做该阶段的事**；
3. 任务完成后执行收尾三件套：
   - 验收：跑该阶段 Prompt 里写明的验收命令/检查项，贴出结果；
   - 沉淀：按需更新 `docs/90-knowledge/` 下对应图谱文件；
   - 纪要：在 `docs/90-knowledge/session-log/` 新增一份会话纪要（用模板）。

## 铁律（违反即返工）

- **一次会话一个目标**：不超出当前阶段 Prompt 的边界，发现新问题记入纪要的「遗留项」，不顺手扩散。
- **先读后写**：改任何文件前先 Read；引用结论必须给出 `文件:行号`。
- **传输 ABI 冻结**：`link-pipe` 模块的 AIDL 一旦评审通过，永不修改方法签名；演进只发生在 opcode 表与 Bundle schema。
- **依赖方向**：业务 App → link-runtime + contract-X；contract 之间互不依赖；link-kernel 不依赖任何 contract。
- **控制面不碰业务数据**：link-kernel 只做注册/发现/健康/ACL。
- 中文回复；代码注释中文；提交信息中文。

## 文档地图

| 目录 | 内容 | 谁写 |
|---|---|---|
| docs/10-input/ | 需求规格、参考资料索引、约束（输入文档） | 阶段 1 |
| docs/20-design/ | 架构、ADR 决策记录、能力契约 SPEC | 阶段 2 |
| docs/30-dev/ | 编码规范、模块开发手册、测试策略 | 阶段 2/3 |
| docs/40-output/ | 接入指南、发布说明（输出文档） | 阶段 5 |
| docs/90-knowledge/ | 知识图谱 + 会话记忆（横切，每阶段更新） | 所有阶段 |

## 历史教训（一期 baic-openapi 用血换的，不可重蹈）

- Watchdog 必须监控**主线程** Looper，独立线程检查，禁止 post 死循环给自己；
- persistent 只保 Application 主进程，Service 禁止放 `:子进程`；
- 任何跨线程集合用 ConcurrentHashMap/CopyOnWriteArrayList，回调一次性语义用 AtomicBoolean CAS；
- pid/uid 一律 `Binder.getCallingPid()/Uid()` 取内核值，绝不信参数；
- 不向无权限调用方暴露任何裸 Binder（无「公开白名单」）。
完整清单见 `docs/90-knowledge/code-landmarks.md`。
