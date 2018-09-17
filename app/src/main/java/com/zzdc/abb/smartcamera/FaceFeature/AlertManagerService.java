package com.zzdc.abb.smartcamera.FaceFeature;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.controller.AvMediaMuxer;
import com.zzdc.abb.smartcamera.controller.MainActivity;
import com.zzdc.abb.smartcamera.controller.MediaStorageManager;

import static com.zzdc.abb.smartcamera.controller.MainActivity.cameraRuning;

public class AlertManagerService extends Service {

    private static final String TAG = AlertManagerService.class.getSimpleName();

    private static final String ACTION_CAMERA_ALERT = "com.foxconn.alert.camera.play";

    private ApplicationSetting mAplicationSetting = ApplicationSetting.getInstance();

    private Handler handler;
    private Runnable runnable;
    private AvMediaMuxer alertMuxer;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @SuppressLint("WrongConstant")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int flag = Service.START_STICKY;
        return super.onStartCommand(intent, flag, startId);
    }


    @Override
    public void onCreate() {
        initData();
        super.onCreate();
    }

    private void initData() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CAMERA_ALERT);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        AlertCallReciever alertCallReciever = new AlertCallReciever();
        registerReceiver(alertCallReciever, filter);
    }

    public void startAlertRecord() {
        Log.d(TAG, "startAlertRecord");
        if (!MediaStorageManager.getInstance().isReady()) {
            Log.d(TAG, "MediaStorageManager hasn't ready");
            return;
        }
        if (alertMuxer != null && alertMuxer.mMuxering) {
            Log.d(TAG, "AlertRecordStarting resetStopTime---");
            resetStopTime(3000);
            return;
        }
        Log.d(TAG, "startAlertRecord---");
        alertMuxer = new AvMediaMuxer("Alert");
        waitAlertForStart();
    }

    private void stopAlertRecord() {
        Log.d(TAG, " stopAlertRecord ");
        if (alertMuxer != null) {
            Log.d(TAG, "stopAlertRecord---");
            alertMuxer.stopMediaMuxer();
            alertMuxer = null;
        }
    }

    private void waitAlertForStart() {
        if (MediaStorageManager.getInstance().isReady()) {

            if (alertMuxer == null) {
                return;
            }
            if (!alertMuxer.startMediaMuxer()) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        waitAlertForStart();
                    }
                }, 1000);
            } else {
                handler = new Handler();
                runnable = new Runnable() {
                    @Override
                    public void run() {
                        stopAlertRecord();
                    }
                };
                handler.postDelayed(runnable, 30 * 1000);
            }
        }

    }

    public void resetStopTime(long time) {
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
            handler = null;
            runnable = null;
            handler = new Handler();
            runnable = new Runnable() {
                @Override
                public void run() {
                    stopAlertRecord();
                }
            };
            handler.postDelayed(runnable, time);

        }
    }

    public class AlertCallReciever extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_CAMERA_ALERT)) {
                String type = intent.getStringExtra("type");
                String message = intent.getStringExtra("message");
                Log.d(TAG, "get receive -- type = " + type + " message = " + message);
                if (type.equals("ALERT")) {
                    if (mAplicationSetting.getSystemMonitorOKSetting()) {
                        if (cameraRuning) {
                            startAlertRecord();
                        }
                    }
                }
            } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)){
                stopAlertRecord();
            }
        }
    }
}
