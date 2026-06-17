package com.baic.media;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 多媒体 Provider 提示页（被动响应消费方的 media.play/pause/next）。 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 48;
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("多媒体 · Provider（全量·自带 Service）");
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("等待消费方调用 media.play / pause / next，并广播 media.state。\n当前状态: " + MediaApp.stateJson());
        root.addView(hint);

        setContentView(root);
    }
}
