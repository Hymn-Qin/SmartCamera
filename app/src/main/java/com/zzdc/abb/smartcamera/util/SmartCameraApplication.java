package com.zzdc.abb.smartcamera.util;

import android.app.Application;
import android.content.Context;

public class SmartCameraApplication extends Application {
    private static  Context mContext;

    public void onCreate(){
        super.onCreate();
        mContext = getApplicationContext();
    }

    public static Context getContext(){
        return mContext;
    }
}
