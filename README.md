# CabinLink 座舱服务总线

北汽座舱业务模块间通信中间件。架构见 `../CabinLink-座舱服务总线架构设计-V2.md`。

## 给 Claude Code 用户的三步上手
1. 读 `CLAUDE.md`（自动加载）→ 它会引导读 `docs/90-knowledge/INDEX.md`
2. 打开 `docs/00-workflow/CLAUDE-CODE-工作流.md`，找到当前阶段，复制对应 Prompt 发起会话
3. 会话收尾跑 `/sync-knowledge`

## 目录
- `docs/00-workflow/` 工作流手册（中枢）
- `docs/10-input/` → `20-design/` → `30-dev/` → `40-output/` 文档流水线
- `docs/90-knowledge/` 知识图谱 + 会话记忆（跨会话衔接的唯一依赖）
- `.claude/commands/` 自定义命令：/sync-knowledge、/adr
- 源码模块：按工作流 P3.1 起步创建（当前尚无）
