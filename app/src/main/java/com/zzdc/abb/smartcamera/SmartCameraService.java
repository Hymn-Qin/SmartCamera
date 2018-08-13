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

import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.controller.AvMediaRecorder;
import com.zzdc.abb.smartcamera.controller.MainActivity;

public class SmartCameraService extends Service {


    private AvMediaRecorder mAvMediaRecorder;

    private ApplicationSetting mApplicationSetting;

    ABBCallReceiver abbCallReceiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
//        stopForeground(true);
        super.onDestroy();
    }

    private void initData() {
        mAvMediaRecorder = AvMediaRecorder.getInstance();
        mApplicationSetting = ApplicationSetting.getInstance();

        IntentFilter filter = new IntentFilter();
        filter.addAction("com.foxconn.alert.camera.play");
        abbCallReceiver = new ABBCallReceiver();
        registerReceiver(abbCallReceiver, filter);
    }

    public class ABBCallReceiver extends BroadcastReceiver {

        private final String  TAG = MainActivity.OneKeyCallReciever.class.getSimpleName();
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: This method is called when the BroadcastReceiver is receiving
            // an Intent broadcast.
            Log.e(TAG, "onReceive " + intent.getAction());
            if (intent.getAction().equalsIgnoreCase("com.foxconn.alert.camera.play")) {
                String type = intent.getStringExtra("type");
                String message = intent.getStringExtra("message");
                Log.d("qxj", "get receive -- type = " + type + " message = " + message);

                if (mAvMediaRecorder == null) {
                    mAvMediaRecorder = AvMediaRecorder.getInstance();
                }

                if (type.equals("ALERT")) {
                    if (!AvMediaRecorder.AlertRecordRunning){
                        mAvMediaRecorder.startAlertRecord();
                    } else {
                        mAvMediaRecorder.resetStopTime(0);
                    }
                } else if (type.equals("Camera")) {
                    if (MainActivity.mainActivity == null) {
                        Intent intent1 = new Intent(context, MainActivity.class);
                        startActivity(intent1);
                    }
                    if (message.equals("false")) {
                        if (mApplicationSetting.getSystemMonitorOKSetting()) {
                            mApplicationSetting.setSystemMonitorOKSetting(false);
                            mApplicationSetting.setSystemMonitorSetting(false);
                            if (MainActivity.RecordRuning) {
                                mAvMediaRecorder.avMediaRecorderStop();
                                MainActivity.RecordRuning = false;
                            }
                        }

                    } else if (message.equals("true")) {

                        if (!mApplicationSetting.getSystemMonitorOKSetting()) {
                            mApplicationSetting.setSystemMonitorOKSetting(true);
                            mApplicationSetting.setSystemMonitorSetting(true);
                            if (!MainActivity.RecordRuning) {
                                mAvMediaRecorder.init();
                                mAvMediaRecorder.avMediaRecorderStart();
                                MainActivity.RecordRuning = true;
                            }
                        }
                    }
                    Log.d("qxj", "send com.foxconn.zzdc.broadcast.camera receive -- type = " + type + " message = " + message);
                    Intent intents = new Intent("com.foxconn.zzdc.broadcast.camera");
                    intents.putExtra("type", type);
                    intents.putExtra("result", message);
                    context.sendBroadcast(intents);
                }
            }

        }
    }
}
