package com.baic.sample.car;

import android.app.Application;

import com.baic.cabinlink.runtime.CabinLink;

public class CarApp extends Application {
    public static final HvacImpl HVAC = new HvacImpl();

    @Override public void onCreate() {
        super.onCreate();
        CabinLink.of(this).publish(HVAC);   // 发布：就这一行（内核未起则自动缓存重放）
    }
}
