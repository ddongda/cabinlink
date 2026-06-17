package com.baic.sample.car;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

/** 提供方调试页：模拟车端自身状态变化（CAN 侧）+ 发告警事件 */
public class MainActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_car);
        ((TextView) findViewById(R.id.tv_status)).setText(
                "提供方 baic.car.hvac 已发布\n下方按钮模拟 CAN 侧自发变化（订阅方将收到推送）");

        ((Button) findViewById(R.id.btn_can_temp)).setOnClickListener(v ->
                CarApp.HVAC.simulateCanTemperature(18f));
        ((Button) findViewById(R.id.btn_alert)).setOnClickListener(v ->
                CarApp.HVAC.simulateAlert("滤芯寿命不足，请更换"));
    }
}
