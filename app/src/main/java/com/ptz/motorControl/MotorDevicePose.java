package com.ptz.motorControl;

import android.content.Context;
import android.content.SharedPreferences;

import com.zzdc.abb.smartcamera.common.ApplicationSetting;

public class MotorDevicePose {
    private static MotorDevicePose motorDevicePose = new MotorDevicePose();
    private Context mContext;
    public static final String POSE = "POSE";

    public void SetContext(Context context){

        this.mContext =  context;

    }
    public static MotorDevicePose getInstance(){
        return motorDevicePose;

    }

    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    public  boolean getDevicePose(){
        mSharedPreferences = mContext.getSharedPreferences(POSE, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        return mSharedPreferences.getBoolean("POSE", false);
    }

    public void setDevicePose(boolean pose){
        mSharedPreferences = mContext.getSharedPreferences(POSE, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
        mEditor.putBoolean("POSE", pose);
        mEditor.commit();
    }
}
