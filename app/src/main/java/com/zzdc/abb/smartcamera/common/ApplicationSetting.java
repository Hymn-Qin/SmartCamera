package com.zzdc.abb.smartcamera.common;

import android.content.Context;
import android.content.SharedPreferences;

public class ApplicationSetting {
    private static ApplicationSetting mAplicationSetting = new ApplicationSetting();
    private Context mContext;
    public static final String PREFRENCE = "com.zzdc.abb.smartcamera";
    public void SetContext(Context context){

        this.mContext =  context;

    }
    public static ApplicationSetting getInstance(){
        return mAplicationSetting;

    }

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    public  boolean getSystemMonitorSetting(){
        mSharedPreferences = mContext.getSharedPreferences(PREFRENCE, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        return mSharedPreferences.getBoolean("isOpenMonitor", false);
    }

    public void setSystemMonitorSetting(boolean aIsEnable){
        mSharedPreferences = mContext.getSharedPreferences(PREFRENCE, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        mEditor.putBoolean("isOpenMonitor", aIsEnable);
        mEditor.commit();
    }
}

