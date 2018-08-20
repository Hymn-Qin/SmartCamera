package com.zzdc.abb.smartcamera;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.zzdc.abb.smartcamera.FaceFeature.FaceConfig;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.controller.AlertMediaMuxer;
import com.zzdc.abb.smartcamera.controller.AvMediaRecorder;
import com.zzdc.abb.smartcamera.controller.MainActivity;

public class SmartCameraService extends Service {

    private static final String TAG = "SmartCameraServiceQXJ";

    private AvMediaRecorder mAvMediaRecorder;

    private ApplicationSetting mApplicationSetting;

    private ABBCallReceiver abbCallReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
//        startForeground(1, new Notification());
        super.onCreate();
    }

    @SuppressLint("WrongConstant")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initData();
        flags = START_STICKY;
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(abbCallReceiver);
//        stopForeground(true);

        super.onDestroy();
    }

    private void initData() {
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.foxconn.alert.camera.play");
        abbCallReceiver = new ABBCallReceiver();
        registerReceiver(abbCallReceiver, filter);

        mAvMediaRecorder = AvMediaRecorder.getInstance();
        mApplicationSetting = ApplicationSetting.getInstance();
    }

    public class ABBCallReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // an Intent broadcast.
            Log.e(TAG, "onReceive " + intent.getAction());
            if (intent.getAction().equalsIgnoreCase("com.foxconn.alert.camera.play")) {
                String type = intent.getStringExtra("type");
                String message = intent.getStringExtra("message");
                Log.d(TAG, "get receive -- type = " + type + " message = " + message);
                if (type.equals("ALERT")) {
                    if (mApplicationSetting.getSystemMonitorOKSetting()) {
                        if (!AlertMediaMuxer.AlertRecordRunning) {
                            Log.d(TAG, "警告视频开始");
                            AlertMediaMuxer.AlertRecordRunning = true;
                            mAvMediaRecorder.startAlertRecord();
                        } else {
                            Log.d(TAG, "警告视频已经开始 延长录制时间");
                            mAvMediaRecorder.resetStopTime(0);
                        }
                    }

                } else
                    if (type.equals("Camera")) {

                    if (MainActivity.mainActivity == null) {
                        Intent ac = new Intent(context, MainActivity.class);
                        context.startActivity(ac);
                    }
                    if (mAvMediaRecorder == null) {
                        mAvMediaRecorder = AvMediaRecorder.getInstance();
                    }
                    if (message.equals("false")) {  
                        if (mApplicationSetting.getSystemMonitorOKSetting()) {
                            mApplicationSetting.setSystemMonitorOKSetting(false);
                            mApplicationSetting.setSystemMonitorSetting(false);
                            if (MainActivity.mainActivity != null && MainActivity.RecordRuning) {
                                mAvMediaRecorder.avMediaRecorderStop();
                                MainActivity.RecordRuning = false;
                            }
                        }

                    } else if (message.equals("true")) {

                        if (!mApplicationSetting.getSystemMonitorOKSetting()) {
                            mApplicationSetting.setSystemMonitorOKSetting(true);
                            mApplicationSetting.setSystemMonitorSetting(true);
                            if (MainActivity.mainActivity != null && !MainActivity.RecordRuning) {
                                mAvMediaRecorder.init();
                                mAvMediaRecorder.avMediaRecorderStart();
                                MainActivity.RecordRuning = true;
                            }
                        }
                    }
                    Log.d(TAG, "send com.foxconn.zzdc.broadcast.camera receive -- type = " + type + " message = " + message);
                    Intent intents = new Intent("com.foxconn.zzdc.broadcast.camera");
                    intents.putExtra("type", type);
                    if (mApplicationSetting.getSystemMonitorOKSetting()) {
                        intents.putExtra("result", "true");
                    } else {
                        intents.putExtra("result", "false");
                    }
                    context.sendBroadcast(intents);
                }
            }

        }
    }
}
