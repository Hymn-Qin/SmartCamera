package com.zzdc.abb.smartcamera.common;

import android.content.Context;
import android.content.SharedPreferences;

import com.zzdc.abb.smartcamera.util.LogTool;

public class ApplicationSetting {
    private static final String TAG = ApplicationSetting.class.getSimpleName();
    private static final String PREFERENCE = "com.zzdc.abb.smartcamera";

    //camera的开关状态
    private static final String IS_TO_START = "isOK";
    //camera的姿态
    private static final String POSE = "POSE";

    private static final String IS_TO_START_CONTRAST = "CONTRAST";
    private static ApplicationSetting mAplicationSetting = new ApplicationSetting();
    private Context mContext;

    public static ApplicationSetting getInstance(){
        return mAplicationSetting;
    }

    public void SetContext(Context context){
        this.mContext =  context;
    }

    public  boolean getSystemMonitorOKSetting(){
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        boolean monitorStatus = preference.getBoolean(IS_TO_START, true);
        LogTool.d(TAG,"Get carema status from server : "+monitorStatus);
        return monitorStatus;
    }

    public void setSystemMonitorOKSetting(boolean aIsEnable){
        LogTool.d(TAG,"User set carema status to server : "+aIsEnable);
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

    public  boolean getSystemContrastSetting(){
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        boolean monitorStatus = preference.getBoolean(IS_TO_START_CONTRAST, true);
        LogTool.d(TAG,"Get Contrast status from server : "+monitorStatus);
        return monitorStatus;
    }

    public void setSystemContrastSetting(boolean aIsEnable){
        LogTool.d(TAG,"User set Contrast status to server : "+aIsEnable);
        SharedPreferences preference = mContext.getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = preference.edit();
        edit.putBoolean(IS_TO_START_CONTRAST, aIsEnable);
        edit.apply();
    }
}

