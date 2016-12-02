package com.example.smartcardapp;

import android.app.Application;
import android.content.Context;

/**
 * Created by Neo on 2016/12/2 002.
 */

public class SampleApplication extends Application {
    private static final String TAG = SampleApplication.class.getSimpleName();
    private static SampleApplication sContext;

    public static Context getContext() {
        return sContext.getApplicationContext();
    }

    public static Application getApplication() {
        return sContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = this;
    }
}
