# 编码规范（待 P2.2 定稿；骨架含一期教训硬约束）

## 线程与并发（硬约束，违例 = 评审打回）
1. 跨线程集合：ConcurrentHashMap / CopyOnWriteArrayList（教训#3）
2. 一次性回调：AtomicBoolean.compareAndSet 终态守卫（教训#3）
3. 线程池：必须有界 + CallerRunsPolicy（教训#8）
4. Watchdog 类监控代码：见 code-landmarks 教训#1，写前必读
5. 时间戳：恢复/对比用 SystemClock.uptimeMillis()，不用墙钟

## 安全（硬约束）
6. pid/uid 只取 Binder.getCallingPid()/Uid()（教训#4）
7. 禁止裸暴露 Binder/pipe 给未鉴权方（教训#5）

## 通用
8. 「降级-恢复」操作必须先快照原状态（教训#10）
9. 注释只写代码无法表达的约束/不变量；禁止过程性注释
10. （P2.2 补充：命名 / 日志 TAG 规约 / Bundle key 常量化 …）
