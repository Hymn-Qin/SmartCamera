package com.zzdc.abb.smartcamera.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ApplicationSetting {
    private static final String PREFERENCE = "com.zzdc.abb.smartcamera";
    private static final String MONITOR_STATE = "monitor_state";

    //是否可以打开监控
    private static final String IS_TO_START = "isOK";
    //设备姿态 正立 倒立
    public static final String POSE = "POSE";
    private static ApplicationSetting mAplicationSetting = new ApplicationSetting();
    private Context mContext;

    public static ApplicationSetting getInstance(){
        return mAplicationSetting;
    }

    public void SetContext(Context context){
        this.mContext =  context;
    }

    public  boolean getSystemMonitorSetting(){
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        return preference.getBoolean(MONITOR_STATE, true);
    }

    public void setSystemMonitorSetting(boolean aIsEnable){
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = preference.edit();
        edit.putBoolean(MONITOR_STATE, aIsEnable);
        edit.apply();
    }

    public  boolean getSystemMonitorOKSetting(){
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        return preference.getBoolean(IS_TO_START, true);
    }

    public void setSystemMonitorOKSetting(boolean aIsEnable){
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = preference.edit();
        edit.putBoolean(IS_TO_START, aIsEnable);
        edit.apply();
    }

    public  boolean getDevicePose(){
        SharedPreferences preference = mContext.getSharedPreferences(POSE, Context.MODE_PRIVATE);
        return preference.getBoolean(POSE, true);
    }

    public void setDevicePose(boolean pose){
        SharedPreferences preference = mContext.getSharedPreferences(POSE, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = preference.edit();
        edit.putBoolean(POSE, pose);
        edit.apply();
    }
}

