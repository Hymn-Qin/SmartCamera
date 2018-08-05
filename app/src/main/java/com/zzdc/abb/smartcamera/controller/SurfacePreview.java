package com.zzdc.abb.smartcamera.controller;

/**
 * Created by zhongjihao on 18-2-7.
 */

import android.app.Activity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.content.Context;

import com.zzdc.abb.smartcamera.common.ApplicationSetting;

public class SurfacePreview implements SurfaceHolder.Callback{
    private final static String TAG = "SurfacePreview";
//    private VideoGather.CameraOperateCallback mCallback;
    private PermissionNotify listener;
    private Activity mActivity;
    private ApplicationSetting mAplicationSetting = null;

    public interface PermissionNotify{
        boolean hasPermission();
    }

    public SurfacePreview(Activity activity,PermissionNotify listener){
//        mCallback = cb;
        mActivity = activity;
        this.listener = listener;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        Log.d(TAG, "******************* surfaceDestroyed() *******************");
        VideoGather.getInstance().doStopCamera();
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        Log.d(TAG, "******************* surfaceCreated() ***************");
        mAplicationSetting = ApplicationSetting.getInstance();
        if(mAplicationSetting.getSystemMonitorSetting()){
            VideoGather.getInstance().doOpenCamera();
            VideoGather.getInstance().doStartPreview(mActivity,arg0);
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {
        Log.d(TAG, "******************* surfaceChanged() ***************");
        mAplicationSetting = ApplicationSetting.getInstance();
        if(mAplicationSetting.getSystemMonitorSetting()){
            VideoGather.getInstance().doOpenCamera();
            VideoGather.getInstance().doStartPreview(mActivity,arg0);
        }

    }

}
