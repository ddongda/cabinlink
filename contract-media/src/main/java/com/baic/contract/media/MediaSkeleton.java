package com.baic.contract.media;

import android.os.Bundle;

import com.baic.cabinlink.runtime.CapabilitySkeleton;
import com.baic.cabinlink.runtime.Pipe;

/**
 * Media 提供方骨架（手写模式样板 = 未来 APT 的输出形态）。
 * 业务团队继承本类，只实现 5 个抽象方法；推送/快照/订阅/线程全部继承自基类。
 */
public abstract class MediaSkeleton extends CapabilitySkeleton {

    @Override public final String capabilityId() { return MediaSchema.CAPABILITY_ID; }
    @Override public final int    version()      { return MediaSchema.VERSION; }

    // ── 业务方法（在能力级串行线程执行，免锁）────────────────
    public abstract void play(ReplySink reply);
    public abstract void pause(ReplySink reply);
    public abstract void next(ReplySink reply);
    public abstract void prev(ReplySink reply);
    public abstract void setVolume(int volume, ReplySink reply);

    // ── 给业务用的属性/事件快捷方法 ──────────────────────────
    protected final void setPlayStateProp(int state) { properties().set(MediaSchema.P_PLAY_STATE, state); }
    protected final void setTitleProp(String title)  { properties().set(MediaSchema.P_TITLE, title); }
    protected final void setVolumeProp(int volume)   { properties().set(MediaSchema.P_VOLUME, volume); }
    protected final void emitTrackChanged(String title) {
        Bundle b = new Bundle(); b.putString(Pipe.K_VALUE, title);
        emit(MediaSchema.T_TRACK_CHANGED, b);
    }

    // ── op 分发（生成物形态）────────────────────────────────
    @Override protected final void onInvoke(int op, Bundle args, ReplySink reply) {
        switch (op) {
            case MediaSchema.OP_PLAY:       play(reply); break;
            case MediaSchema.OP_PAUSE:      pause(reply); break;
            case MediaSchema.OP_NEXT:       next(reply); break;
            case MediaSchema.OP_PREV:       prev(reply); break;
            case MediaSchema.OP_SET_VOLUME: setVolume(args.getInt(Pipe.K_VALUE, 0), reply); break;
            default:
                reply.fail(Pipe.E_UNKNOWN_OP, "op=" + op);
        }
    }
}
