package com.baic.usercenter;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 用按钮模拟登录/切换/登出，触发账号状态发布。 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 48;
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("用户中心 · 账号 Provider（lite 挂已有 Service）");
        root.addView(title);

        Button login = new Button(this);
        login.setText("登录 user_8888 / 小北");
        login.setOnClickListener(v -> UserCenterApp.updateAccount(1, "user_8888", "小北"));
        root.addView(login);

        Button switchAcc = new Button(this);
        switchAcc.setText("切换账号 user_6666 / 老司机");
        switchAcc.setOnClickListener(v -> UserCenterApp.updateAccount(1, "user_6666", "老司机"));
        root.addView(switchAcc);

        Button logout = new Button(this);
        logout.setText("登出");
        logout.setOnClickListener(v -> UserCenterApp.updateAccount(0, null, null));
        root.addView(logout);

        setContentView(root);
    }
}
