package com.baic.sample.voice;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.baic.cabinlink.runtime.CabinLink;
import com.baic.cabinlink.runtime.Reply;
import com.baic.contract.car.Hvac;

/**
 * 消费方范例（语音 App 视角）—— 这就是消费方要写的【全部】代码。
 * 演示三原语：Call（控空调）/ Property（镜像读+订阅）/ Event（告警）。
 * 提供方崩溃恢复后自动 reattach，本页零处理。
 */
public class MainActivity extends Activity {

    private Hvac mHvac;                        // require 回调后赋值，终身可用
    private TextView mLog;
    private final StringBuilder mBuf = new StringBuilder();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice);
        mLog = findViewById(R.id.tv_log);
        log("等待 baic.car.hvac 上线…");

        // ── 获取能力（一行；onReady 主线程回调一次）──────────
        CabinLink.of(this).require(Hvac.DESCRIPTOR, hvac -> {
            mHvac = hvac;
            log("[OK] baic.car.hvac 已挂接");

            // Property 订阅：首回调必为快照，之后增量推送
            hvac.onTemperature(t -> log("[Prop] 温度 → " + t + "℃"
                    + (hvac.isStale() ? " (stale)" : "")));
            hvac.onAcOn(on -> log("[Prop] 空调 → " + (on ? "ON" : "OFF")));

            // Event 订阅
            hvac.onAlert(msg -> log("[Event] ⚠ " + msg));
        });

        // ── 模拟语音指令 ─────────────────────────────────────
        ((Button) findViewById(R.id.btn_ac_on)).setOnClickListener(v -> {
            if (notReady()) return;
            mHvac.setAcPower(!mHvac.acOn(), Reply.ignore());   // 读镜像决定开/关
        });
        ((Button) findViewById(R.id.btn_24)).setOnClickListener(v -> {
            if (notReady()) return;
            mHvac.setTemperature(24f, r ->                      // Call 带回执（主线程）
                    log(r.isOk() ? "[Call] \"调到24度\" 已下发" : "[Call] 失败 " + r));
        });
        ((Button) findViewById(R.id.btn_read)).setOnClickListener(v -> {
            if (notReady()) return;
            // 读本地镜像：0 IPC
            log("[Mirror] ac=" + mHvac.acOn() + " temp=" + mHvac.temperature()
                    + "℃ fan=" + mHvac.fanSpeed() + " stale=" + mHvac.isStale());
        });
    }

    private boolean notReady() {
        if (mHvac == null) { log("[ERR] 能力未就绪"); return true; }
        return false;
    }

    private void log(String s) {
        mBuf.insert(0, s + "\n");
        if (mBuf.length() > 4000) mBuf.setLength(4000);
        mLog.setText(mBuf);
    }
}
