# 参考资料索引（外部知识入口，P1.3 会话核对）

> 纪律：只建索引不搬运。每条注明「看什么」，让后续会话精准取材。

## 1. 一期工程 baic-openapi（稳定性内核移植来源）

| 路径 | 看什么 |
|---|---|
| `../baic-openapi/openapi-service/` | P0 修复后的内核：Watchdog/Registry/DeathGuard/PermissionGuard，P3.2 移植对象 |
| `../baic-openapi/openapi-sdk/` | 重连/缓存/重放逻辑，P3.3 移植对象 |
| `../baic-openapi/business-aidl/` | 四域接口语义，P2.3 写 SPEC 的参考 |
| `../BAIC-OpenAPI架构设计.md` | 一期架构 V1.2（含修订史） |

## 2. L946 MapSDK（域 SDK 形态参考）

| 路径 | 看什么 |
|---|---|
| `../L946_MapSDK_V1.0.20/mapsdk_sourcecode_v1.0.20/.../mapsdk/` | IMapSDKManager 入口形态、IMapAPICallback 错误模型、IMapEventListener 事件分离、commonRequest 逃生口、IExtendableInterface 前向兼容（99 类） |
| `../L946_MapSDK_V1.0.20/Mapsdk_v1.0.20接口汇总.txt` | 增量接口演进方式（看版本怎么加字段） |
| `../L946中间件逆向分析报告.md` | 11 个缺陷清单（code-landmarks 的来源） |

## 3. 北汽地图遗留协议（adapter-amap 收编对象）

| 路径 | 看什么 |
|---|---|
| `../BAIC_地图sdk/北汽导航对外AIDL接口说明文档.docx` | JSON 协议模板（protocolId+requestCode），adapter 翻译的输入格式 |
| `../BAIC_地图sdk/北汽导航对外广播协议接口说明文档.docx` | AUTONAVI 广播 ACTION/KEY_TYPE、包名 com.baic.icc.bmap、唤起限制的规避方案 |

## 4. 现状统计

| 路径 | 看什么 |
|---|---|
| `../N80项目对外接口现状统计分析.md` | 196 条接口 18 种形式的分布，收编优先级依据 |

## 5. 架构定稿

| 路径 | 看什么 |
|---|---|
| `../CabinLink-座舱服务总线架构设计-V2.md` | 本工程的架构唯一权威（V2.0-draft） |
