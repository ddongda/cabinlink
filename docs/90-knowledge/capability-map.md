# 能力地图（能力↔提供方↔消费方 + id 全局分配表）

> 更新时机：P2.3 每写一份契约 SPEC、P3.4 每实现一个域。
> **id 分配纪律**：op/topic/propertyId 在能力内唯一即可，但本表是唯一权威分配记录，
> 写 SPEC 前必须先查此表防冲突，分配后立即登记。

## 能力注册表

| 能力 id | 契约模块 | 提供方 | 已知消费方 | SPEC | 实现 |
|---|---|---|---|---|---|
| baic.car.hvac | contract-car | 车控App(规划) | 语音 | 📋 P2.3-1 | 📋 P3.4 |
| baic.car.seat | contract-car | 车控App(规划) | 语音 | 📋 | 📋 |
| baic.navi | contract-navi | 自研导航 / adapter-amap | 语音/仪表 | 📋 | ✅ P3.4 |
| baic.media | contract-media | 多媒体App | 语音/电话 | 📋 | ✅ P3.4 |
| baic.phone | contract-phone | 电话App | 语音/多媒体 | 📋 | ✅ P3.4 |
| baic.voice | contract-voice | 语音App | 多媒体/导航 | 📋 | 📋 |

## id 分配表（示例：baic.car.hvac，P2.3-1 定稿后以 SPEC 为准）

| 类型 | id | 名称 | 备注 |
|---|---|---|---|
| Call | 1 | setAcPower | timeout 3000ms |
| Call | 2 | setTemperature | @Range(16,32) |
| Property | 1 | acOn | boolean |
| Property | 2 | temperature | float |
| Property | 3 | fanSpeed | int 0~7 |
| Event | 1 | onDiagnosticAlert | 低频 |

## id 分配表（baic.phone，P3.4 实现，以 PhoneSchema.java 为准）

| 类型 | id | 名称 | 备注 |
|---|---|---|---|
| Call | 1 | answer | 接听，无参 |
| Call | 2 | hangup | 挂断，无参 |
| Call | 3 | dial | 拨号，args K_VALUE String 号码 |
| Property | 1 | callState | int 0空闲/1响铃/2通话中 |
| Property | 2 | peerNumber | String 对端号码 |
| Event | 1 | incomingCall | data K_VALUE String 来电号码 |
| Event | 2 | callEnded | 通话结束，无 data |

## id 分配表（baic.navi，P3.4 实现，以 NaviSchema.java 为准）

| 类型 | id | 名称 | 备注 |
|---|---|---|---|
| Call | 1 | navigateToPoi | 按 POI 导航，args K_VALUE String |
| Call | 2 | navigateToCoord | 按坐标导航，args lat/lng double |
| Call | 3 | cancel | 取消导航，无参 |
| Property | 1 | naviState | int 0空闲/1导航中 |
| Property | 2 | etaSeconds | int ETA 秒 |
| Property | 3 | remainMeters | int 剩余距离米 |
| Event | 1 | turnHint | data K_VALUE String 转向提示 |
| Event | 2 | arrived | 到达目的地，无 data |

## id 分配表（baic.media，P3.4 实现，以 MediaSchema.java 为准）

| 类型 | id | 名称 | 备注 |
|---|---|---|---|
| Call | 1 | play | 播放，无参 |
| Call | 2 | pause | 暂停，无参 |
| Call | 3 | next | 下一曲，无参 |
| Call | 4 | prev | 上一曲，无参 |
| Call | 5 | setVolume | args K_VALUE int 0~100 提供方夹紧 |
| Property | 1 | playState | int 0停/1播/2暂停 |
| Property | 2 | title | String 当前曲目 |
| Property | 3 | volume | int 0~100 |
| Event | 1 | trackChanged | data K_VALUE String 曲目标题 |

## 版本协商记录

| 能力 | 当前 version | 不兼容变更历史 |
|---|---|---|
| （契约定稿后登记） | | |
