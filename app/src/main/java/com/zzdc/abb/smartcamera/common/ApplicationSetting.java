package com.zzdc.abb.smartcamera.common;

import android.content.Context;
import android.content.SharedPreferences;

public class ApplicationSetting {
    private static final String PREFERENCE = "com.zzdc.abb.smartcamera";
    private static final String MONITOR_STATE = "monitor_state";

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
}

