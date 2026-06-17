package com.baic.navi;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 显示账号/媒体消息，并提供"拉取账号""播放媒体"两个 RPC 触发按钮。 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 48;
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("导航 · Consumer（lite 纯客户端）");
        root.addView(title);

        final TextView tv = new TextView(this);
        tv.setText(NaviApp.last);
        root.addView(tv);

        Button pull = new Button(this);
        pull.setText("主动拉取账号 (usercenter.getAccount)");
        pull.setOnClickListener(v -> NaviApp.pullAccount());
        root.addView(pull);

        Button play = new Button(this);
        play.setText("播放媒体 (media.play)");
        play.setOnClickListener(v -> NaviApp.playMedia());
        root.addView(play);

        NaviApp.ui = text -> runOnUiThread(() -> tv.setText(text));
        setContentView(root);
    }
}
