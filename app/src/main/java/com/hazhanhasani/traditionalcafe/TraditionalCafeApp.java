package com.hazhanhasani.traditionalcafe;

import android.app.Application;

public class TraditionalCafeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        OfflineSyncManager.register(this);
    }
}
