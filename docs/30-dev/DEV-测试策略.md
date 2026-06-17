# 测试策略（待 P2.2 定稿）

三层：
1. **单测**（link-runtime 为主）：重连重放、镜像快照合并、超时裁决、错误码映射、幂等守卫
2. **IPC 集成**（samples 双 App）：Call/Event/Property 全链路、版本协商降级
3. **崩溃注入**：杀 provider / 杀 kernel / 杀 consumer 三场景 × 恢复时间断言（≤500ms）
   + 并发压测：1000 次/s 注册+查询（一期遗留 P1 工作项）

基线：P3.5 实测数据记入 session-log，P4.1 回归对照。
