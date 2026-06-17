// ═══ 传输层·冻结区 ═══ 信封 = 通用头部 Header + JSON payload（Bridge SDK §6）。
package com.baic.bridge.transport;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 跨进程传输的最小单元。
 * 设计要点：Header 与 payload 分离——路由/去重/版本判断只读 Header，转发不反序列化 payload。
 * payload 用 JSON 字符串而非 Bundle：跨版本字段增删容忍度高、可读、配合 schemaVersion 做兼容。
 */
public final class BridgeEnvelope implements Parcelable {

    // ── type 取值 ──
    public static final int TYPE_REQUEST  = 1;
    public static final int TYPE_RESPONSE = 2;
    public static final int TYPE_EVENT    = 3;
    public static final int TYPE_HELLO    = 4;
    public static final int TYPE_ERROR    = 5;

    // ── Header（通用头部）──
    public String  msgId;          // UUID，端到端幂等去重
    public int     type;           // TYPE_*
    public String  topic;          // 路由键，如 media.play / usercenter.account.state
    public int     schemaVersion;  // 消息版本，跨版本协商
    public String  source;         // 发送方节点 id；接收侧以 Binder uid 校验，不信此字段
    public String  correlationId;  // REQUEST↔RESPONSE 关联；EVENT/HELLO 留空
    public long    timestamp;      // 发送时刻 ms
    public boolean needAck;        // QoS：是否需回执
    public int     code;           // RESPONSE/ERROR 错误码（0=OK）

    // ── Payload ──
    public String  payload;        // JSON 字符串，按 topic 的 schema 约定（HELLO 时为节点能力声明）

    public BridgeEnvelope() {}

    protected BridgeEnvelope(Parcel in) {
        msgId         = in.readString();
        type          = in.readInt();
        topic         = in.readString();
        schemaVersion = in.readInt();
        source        = in.readString();
        correlationId = in.readString();
        timestamp     = in.readLong();
        needAck       = in.readInt() != 0;
        code          = in.readInt();
        payload       = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(msgId);
        dest.writeInt(type);
        dest.writeString(topic);
        dest.writeInt(schemaVersion);
        dest.writeString(source);
        dest.writeString(correlationId);
        dest.writeLong(timestamp);
        dest.writeInt(needAck ? 1 : 0);
        dest.writeInt(code);
        dest.writeString(payload);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<BridgeEnvelope> CREATOR = new Creator<BridgeEnvelope>() {
        @Override public BridgeEnvelope createFromParcel(Parcel in) { return new BridgeEnvelope(in); }
        @Override public BridgeEnvelope[] newArray(int size) { return new BridgeEnvelope[size]; }
    };

    @Override
    public String toString() {
        return "Envelope{type=" + type + ", topic=" + topic + ", corr=" + correlationId
                + ", code=" + code + ", src=" + source + "}";
    }
}
