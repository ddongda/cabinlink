# 模块开发手册（待 P2.2/P3.4 沉淀）

## 新增一个能力域的标准步骤（目标形态）
1. 查 capability-map.md 分配 id → 写 SPEC（P2.3 模板）
2. contracts/ 新建 contract-<域>（仅契约 + @Pod）
3. 手写/生成 Proxy + Skeleton + Schema（以 contract-car 为模式样板，P3.4 产出后链接到此）
4. 提供方继承 Skeleton 实现业务，publish 一行
5. samples 验证 → 更新 capability-map 实现状态

## 模式样板
（P3.4 完成后填入 HvacProxy/HvacSkeleton 的逐段讲解）
