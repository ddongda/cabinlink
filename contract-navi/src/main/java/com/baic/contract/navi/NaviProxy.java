package com.baic.contract.navi;

import android.os.Bundle;

import com.baic.cabinlink.runtime.LinkResult;
import com.baic.cabinlink.runtime.Pipe;
import com.baic.cabinlink.runtime.PipeProxy;
import com.baic.cabinlink.runtime.Reply;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

/**
 * Navi 消费方代理（手写模式样板 = 未来 APT 的输出形态）。
 * 全部样板已由 PipeProxy 托管，这里只剩"契约成员 → op/topic/propId"的映射。
 */
final class NaviProxy extends PipeProxy implements Navi {

    NaviProxy() {
        // 声明需要的属性与事件主题（attach 时自动订阅，reattach 自动重放）
        subscribe(Pipe.propTopic(NaviSchema.P_NAVI_STATE),
                  Pipe.propTopic(NaviSchema.P_ETA_SECONDS),
                  Pipe.propTopic(NaviSchema.P_REMAIN_METERS),
                  NaviSchema.T_TURN_HINT,
                  NaviSchema.T_ARRIVED);
    }

    // ── Call ───────────────────────────────────────────────
    @Override public void navigateTo(String poi, Reply<Void> reply) {
        Bundle args = new Bundle(); args.putString(Pipe.K_VALUE, poi);
        call(NaviSchema.OP_NAVIGATE_POI, args, asVoid(reply));
    }

    @Override public void navigateTo(double lat, double lng, Reply<Void> reply) {
        Bundle args = new Bundle();
        args.putDouble(NaviSchema.K_LAT, lat);
        args.putDouble(NaviSchema.K_LNG, lng);
        call(NaviSchema.OP_NAVIGATE_COORD, args, asVoid(reply));
    }

    @Override public void cancel(Reply<Void> reply) {
        call(NaviSchema.OP_CANCEL, new Bundle(), asVoid(reply));
    }

    // ── Property ───────────────────────────────────────────
    @Override public int     naviState()    { return mirror().getInt(NaviSchema.P_NAVI_STATE, NaviSchema.STATE_IDLE); }
    @Override public int     etaSeconds()   { return mirror().getInt(NaviSchema.P_ETA_SECONDS, 0); }
    @Override public int     remainMeters() { return mirror().getInt(NaviSchema.P_REMAIN_METERS, 0); }
    @Override public boolean isStale()      { return mirror().isStale(); }

    @Override public void onNaviState(IntConsumer w) {
        mirror().watch((id, v) -> { if (id == NaviSchema.P_NAVI_STATE) w.accept((Integer) v); });
    }
    @Override public void onEtaSeconds(IntConsumer w) {
        mirror().watch((id, v) -> { if (id == NaviSchema.P_ETA_SECONDS) w.accept((Integer) v); });
    }
    @Override public void onRemainMeters(IntConsumer w) {
        mirror().watch((id, v) -> { if (id == NaviSchema.P_REMAIN_METERS) w.accept((Integer) v); });
    }

    // ── Event ──────────────────────────────────────────────
    private final CopyOnWriteArrayList<TurnHintWatcher> mTurnHintWatchers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ArrivedWatcher>  mArrivedWatchers  = new CopyOnWriteArrayList<>();
    @Override public void onTurnHint(TurnHintWatcher w) { mTurnHintWatchers.add(w); }
    @Override public void onArrived(ArrivedWatcher w)   { mArrivedWatchers.add(w); }

    @Override protected void onEvent(int topic, Bundle data) {   // 已在主线程
        switch (topic) {
            case NaviSchema.T_TURN_HINT: {
                String hint = data.getString(Pipe.K_VALUE, "");
                for (TurnHintWatcher w : mTurnHintWatchers) w.on(hint);
                break;
            }
            case NaviSchema.T_ARRIVED: {
                for (ArrivedWatcher w : mArrivedWatchers) w.on();
                break;
            }
            default: /* 未知 topic 忽略 */ break;
        }
    }

    private static Reply<Bundle> asVoid(Reply<Void> r) {
        return res -> { if (r != null) r.onResult(res.isOk()
                ? LinkResult.ok(null)
                : LinkResult.fail(res.code, res.message)); };
    }
}
