# 多媒体模块接口文档（Bridge SDK · media 契约）

> 报文 = `BridgeEnvelope`（传输层冻结结构）+ JSON `payload`。本文每个示例**必含** `topic / source / schemaVersion / payload`；为体现 request↔response 语义，额外保留 `type / correlationId / code`，其余头部（`msgId / timestamp / needAck`）按需省略。
>
> 对应版本：`MediaSchema.VERSION = 1`。
> 来源：[MediaSchema.java](../contract/media/src/main/java/com/baic/bridge/contract/media/MediaSchema.java)、[MediaError.java](../contract/media/src/main/java/com/baic/bridge/contract/media/MediaError.java)、[BridgeEnvelope.java](../transport/src/main/java/com/baic/bridge/transport/BridgeEnvelope.java)

## 信封字段约定

| 字段 | 说明 |
|---|---|
| `type` | 1=REQUEST 2=RESPONSE 3=EVENT 5=ERROR |
| `topic` | 路由键，如 `media.play` |
| `schemaVersion` | 消息版本，media 当前 = **1**（`MediaSchema.VERSION`） |
| `source` | 发送方节点 id；**接收侧一律以 `Binder.getCallingUid()` 校验，不信此字段** |
| `correlationId` | REQUEST↔RESPONSE 关联键；EVENT 留空 |
| `code` | 响应错误码，**0=成功**，≥1000 为 media 业务码 |
| `payload` | JSON 字符串，字段见各接口 |

## 接口一览

| topic | 类型 | 说明 | 请求 payload | 响应 payload |
|---|---|---|---|---|
| `media.play` | RPC | 播放指定曲目 | `{trackId}` | `{playState,title}` |
| `media.pause` | RPC | 暂停 | `{}` | `{playState}` |
| `media.next` | RPC | 下一首 | `{}` | `{trackId,title,playState}` |
| `media.setVolume` | RPC | 设置音量 | `{volume}` | `{volume}` |
| `media.state` | Event | 播放状态变更推送（无响应） | — | `{playState,trackId,title,volume}` |

---

## 1. 播放 `media.play`

类型 RPC ｜ schemaVersion 1 ｜ 可能错误码 1002（播放器忙）、1003（片源不可用）

**请求**

```json
{
  "type": 1,
  "topic": "media.play",
  "schemaVersion": 1,
  "source": "com.baic.navi",
  "correlationId": "c-9f2a3b",
  "payload": "{\"trackId\":\"1001\"}"
}
```

**响应**

```json
{
  "type": 2,
  "topic": "media.play",
  "schemaVersion": 1,
  "source": "com.baic.media",
  "correlationId": "c-9f2a3b",
  "code": 0,
  "payload": "{\"playState\":1,\"title\":\"曲目 1001\"}"
}
```

请求 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `trackId` | String | — | 必填 | 曲目 id |

响应 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `playState` | int | 0:停 1:播 | — | 当前播放状态 |
| `title` | String | — | — | 当前曲目名 |

---

## 2. 暂停 `media.pause`

类型 RPC ｜ schemaVersion 1

**请求**

```json
{
  "type": 1,
  "topic": "media.pause",
  "schemaVersion": 1,
  "source": "com.baic.navi",
  "correlationId": "c-71d8e0",
  "payload": "{}"
}
```

> 请求 payload 为空，无业务参数。

**响应**

```json
{
  "type": 2,
  "topic": "media.pause",
  "schemaVersion": 1,
  "source": "com.baic.media",
  "correlationId": "c-71d8e0",
  "code": 0,
  "payload": "{\"playState\":0}"
}
```

响应 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `playState` | int | 0:停 1:播 | — | 暂停后固定为 0 |

---

## 3. 下一首 `media.next`

类型 RPC ｜ schemaVersion 1 ｜ 可能错误码 1003（片源不可用）

**请求**

```json
{
  "type": 1,
  "topic": "media.next",
  "schemaVersion": 1,
  "source": "com.baic.navi",
  "correlationId": "c-3c5f12",
  "payload": "{}"
}
```

> 请求 payload 为空，无业务参数。

**响应**

```json
{
  "type": 2,
  "topic": "media.next",
  "schemaVersion": 1,
  "source": "com.baic.media",
  "correlationId": "c-3c5f12",
  "code": 0,
  "payload": "{\"trackId\":\"1002\",\"title\":\"曲目 1002\",\"playState\":1}"
}
```

响应 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `trackId` | String | — | — | 切换后的曲目 id |
| `title` | String | — | — | 切换后的曲目名 |
| `playState` | int | 0:停 1:播 | — | 切换后播放状态 |

---

## 4. 设置音量 `media.setVolume`

类型 RPC ｜ schemaVersion 1

**请求**

```json
{
  "type": 1,
  "topic": "media.setVolume",
  "schemaVersion": 1,
  "source": "com.baic.navi",
  "correlationId": "c-88ab40",
  "payload": "{\"volume\":60}"
}
```

**响应**

```json
{
  "type": 2,
  "topic": "media.setVolume",
  "schemaVersion": 1,
  "source": "com.baic.media",
  "correlationId": "c-88ab40",
  "code": 0,
  "payload": "{\"volume\":60}"
}
```

请求 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `volume` | int | 0–100 | 必填 | 目标音量 |

响应 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `volume` | int | 0–100 | — | 回显实际生效音量 |

---

## 5. 播放状态事件 `media.state`（Event，单向，无响应）

由 media provider 主动发布，`type=3`、`correlationId` 留空、无应答。消费方通过 `MediaClient.subscribeState(...)` 订阅。

```json
{
  "type": 3,
  "topic": "media.state",
  "schemaVersion": 1,
  "source": "com.baic.media",
  "payload": "{\"playState\":1,\"trackId\":\"1002\",\"title\":\"曲目 1002\",\"volume\":60}"
}
```

事件 payload 字段

| payload 字段 | 类型 | 取值/枚举 | 默认值/必填项 | 说明 |
|---|---|---|---|---|
| `playState` | int | 0:停 1:播 | — | 播放状态 |
| `trackId` | String | — | — | 当前曲目 id |
| `title` | String | — | — | 当前曲目名 |
| `volume` | int | 0–100 | — | 当前音量 |

---

## 6. 错误响应示例（`code` ≠ 0）

业务失败仍走 `type=2`（RESPONSE），错误信息体现在 `code`；`payload` 可为空。以「片源不可用」为例：

```json
{
  "type": 2,
  "topic": "media.play",
  "schemaVersion": 1,
  "source": "com.baic.media",
  "correlationId": "c-9f2a3b",
  "code": 1003,
  "payload": "{}"
}
```

**media 错误码**（`MediaError`，区间 1000–1999）：

| code | 常量 | 含义 |
|---|---|---|
| 1001 | `E_DEVICE` | 播放器异常 |
| 1002 | `E_PLAYER_BUSY` | 播放器忙 |
| 1003 | `E_SOURCE` | 片源不可用 |

> 0–7 为 SDK 公共码（`BridgeErrors`，如 `E_BUSY=7` 提供方过载），不占模块区间。
