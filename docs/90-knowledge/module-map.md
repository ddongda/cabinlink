# 模块地图（谁依赖谁）

> 更新时机：新增/调整模块时（P3.x 各会话收尾）。状态：📋 规划 / 🚧 开发中 / ✅ 完成。

## 依赖图（箭头 = 依赖方向，编译期由 Gradle 强制）

```
业务App(provider/consumer) ──► link-runtime ──► link-pipe（冻结区）
        │                          ▲
        └──► contract-X ───────────┘（contract 之间禁止互相依赖）
link-kernel ──► link-pipe（kernel 不依赖任何 contract）
adapters/adapter-amap ──► link-runtime + contract-navi（桥接提供方=普通业务App）
```

## 模块清单

| 模块 | 角色 | 类型 | 状态 | 关键约束 |
|---|---|---|---|---|
| link-pipe | 唯一 AIDL：数据面三件套+控制面两件套 | Library | ✅ 骨架 | **冻结区**：评审后永不改方法签名 |
| link-kernel | 控制面 APK：注册/发现/死亡感知（ACL/健康/Watchdog 待 P3.2 移植） | APK | 🚧 最小版 | 不依赖 contract；不碰业务数据 |
| link-runtime | 双端运行库：门面/Skeleton基类/Proxy基类/镜像/重连/错误 | AAR | 🚧 最小版 | 业务唯一直接依赖 |
| link-annotations | 契约注解（纯注解零依赖） | Library | 📋 | |
| link-codegen | APT（Phase 2 才建） | — | 📋 | M3 里程碑 |
| contract-common | 跨域共享 @Pod 数据类 | Library | 📋 | 仅纯数据 |
| contract-car | hvac 契约+手写Proxy/Skeleton（模式样板） | Library | ✅ 骨架 |
| contract-navi / -media / -phone / -voice | 各域契约 | Library | 📋 | 互不依赖 |
| sample-car / sample-voice | 提供方/消费方范例 | APK | ✅ 骨架 | |
| adapters/adapter-amap | 高德协议桥接 | APK | 📋 | M4 里程碑 |

## 一期资产迁移对照

| 一期（baic-openapi） | 去向 | 迁移状态 |
|---|---|---|
| openapi-service 全部（P0 修复版） | link-kernel | 📋 P3.2 |
| openapi-sdk 重连/缓存/重放 | link-runtime | 📋 P3.3 |
| business-aidl 四域 | contracts/ 四份 SPEC→契约 | 📋 P2.3/P3.4 |
| ServiceKey/ServiceCallback | 被生成(手写) Proxy 取代 | — |
