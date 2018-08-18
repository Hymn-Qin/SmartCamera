package com.zzdc.abb.smartcamera.controller;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import com.zzdc.abb.smartcamera.FaceFeature.FaceConfig;
import com.zzdc.abb.smartcamera.FaceFeature.FaceDatabase;
import com.zzdc.abb.smartcamera.FaceFeature.FeatureContrastManager;
import com.zzdc.abb.smartcamera.FaceFeature.Utils;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class AvMediaRecorder {
    private static final String TAG = AvMediaRecorder.class.getSimpleName();
    private static AvMediaRecorder mRecorder = new AvMediaRecorder();

    private AudioGather mAudioGather;
    private VideoGather mVideoGather;
    private AvMediaMuxer mMuxer;
    private AlertMediaMuxer alertMuxer;
    private Activity mActivity;
    private SurfaceHolder mHolder;
    private AudioEncoder mAudioEncoder;
    private VideoEncoder mVideoEncoder;
    private PCMAudioDataTransfer mPCMAudioDataTransfer;
    private static final int START_RECORD = 200;
    private static final int STOP_RECORD = 201;
    private static final int WAIT_TO_START = 203;
    private static final int CHECK_STORAGE = 204;
    private static int SD_MEM_THRESHOLD = 300; //SD card mem threshold
    public boolean ABBassistantRuning = true;
	private SDCardBroadcastReceiver mReceiver;
	private boolean mIsMonitor = false; //monitor status ，true is working；false ，not work；

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case START_RECORD:
                    if (mIsMonitor){
                        startRecord();
                    }
                    break;
                case STOP_RECORD:
                    stopRecord();
                    break;
                case WAIT_TO_START:
                    if(mIsMonitor){
                        waitForStart();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public class SDCardBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)){
                LogTool.d(TAG,"Intent.ACTION_MEDIA_EJECT");
                stopRecord();
                MediaStorageManager.getInstance().stop();
            } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED)){
                Log.d(TAG,"Intent.ACTION_MEDIA_MOUNTED");
                MediaStorageManager.getInstance().start();
                startRecord();
            }
        }
    }

    public void setmActivity(Activity mActivity) {
        this.mActivity = mActivity;
    }

    public void setmHolder(SurfaceHolder mHolder) {
        this.mHolder = mHolder;
    }

    private AvMediaRecorder() {
        mAudioGather = AudioGather.getInstance();
        mAudioEncoder = AudioEncoder.getInstance();

        mVideoGather = VideoGather.getInstance();
        mVideoEncoder = VideoEncoder.getInstance();

        mPCMAudioDataTransfer = PCMAudioDataTransfer.getInstance();

    }

    public void init() {
        //提取人脸数据
        Utils.startGetFeature("qin");
        if (!mAudioGather.AudioGatherRuning) {
            mAudioGather.SetAudioSourceTypeForMonitor();
            mAudioGather.prepareAudioRecord();
        }

        mAudioEncoder.init();
        mAudioGather.registerAudioRawDataListener(mPCMAudioDataTransfer);
        if (!mVideoGather.mIsPreviewing) {
            mVideoGather.doOpenCamera();
            mVideoGather.doStartPreview(mActivity, mHolder);
        }
        setVideoParameter(mVideoGather.getPreWidth(), mVideoGather.getPreHeight(), mVideoGather.getFrameRate());
        mVideoEncoder.init();

        mReceiver = new SDCardBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addDataScheme("file");
        SmartCameraApplication.getContext().registerReceiver(mReceiver, filter);
    }

    public void avMediaRecorderStart() {
        if (!mAudioGather.AudioGatherRuning) {
            mAudioGather.startRecord();
        }
        if (!mVideoEncoder.isRunning()) {
            mVideoEncoder.start();
        }
        if (!mAudioEncoder.isRunning()) {
            mAudioEncoder.start();
        }
        //开始人脸识别
//        startVideoFaceContrast();

        if(MediaStorageManager.getInstance().isReady()){
            startRecord();
        }else {
            Log.d(TAG,"MediaStorageManager not ready");
        }
        mIsMonitor = true;
    }

    public void avMediaRecorderStop() {
        Log.d(TAG, "avMediaRecorderStop");
        mIsMonitor = false;
        if (mMuxer != null) {
            stopRecord();
            stopAlertRecord();
            mHandler.removeCallbacksAndMessages(null);
        }
        SmartCameraApplication.getContext().unregisterReceiver(mReceiver);
        mVideoEncoder.stop();

        new Handler().postDelayed(new Runnable() {
            public void run() {
                mVideoGather.doStopCamera();
                mVideoGather.unregisterVideoRawDataListener(mVideoEncoder);
            }
        }, 300);
        mAudioEncoder.stop();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (!ABBassistantRuning) {
                    mAudioGather.stopRecord();
                    mAudioGather.unregisterAudioRawDataListener(mPCMAudioDataTransfer);
                    mAudioGather.release();

                }
                Log.d(TAG, "mAudioGather stop transfer.");

            }
        }, 300);


    }


    private int mWidth;
    private int mHeight;
    private int mFrame;

    public void setVideoParameter(int width, int height, int frame) {
        mWidth = width;
        mHeight = height;
        mFrame = frame;
    }

    public static AvMediaRecorder getInstance() {
        return mRecorder;
    }

    private void startRecord(){
        Log.d(TAG ,"startRecord");
        if(!MediaStorageManager.getInstance().isReady()){
            Log.d(TAG,"MediaStorageManager hasn't ready");
            return;
        }
        mMuxer = new AvMediaMuxer();
        waitForStart();
    }

    private void waitForStart(){
        if (MediaStorageManager.getInstance().isReady()) {
            if(mMuxer == null){
                return;
            }
            if(!mMuxer.startMediaMuxer()){
                mHandler.sendEmptyMessageDelayed(WAIT_TO_START, 1000);
            } else {
                mHandler.sendEmptyMessageDelayed(STOP_RECORD, 5 * 60 * 1000);
            }
        }
    }

    private void stopRecord(){
        Log.d(TAG ," stopRecord ");
        if(mMuxer!= null){
            mMuxer.stopMediaMuxer();
            mMuxer = null;
        }

        if(!MediaStorageManager.getInstance().isReady()){
            Log.d(TAG,"MediaStorageManager hasn't ready");
            return;
        }
/*        MediaScannerConnection.scanFile(SmartCameraApplication.getContext(), new String[]{mSDCardBussiness.getSDCardVideoRootPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.d(TAG,"onScanCompleted path = " + path + " uri = " + uri);
            }
        });*/
        mHandler.sendEmptyMessageDelayed(START_RECORD, 3000);
    }



    public void startAlertRecord() {
        Log.d(TAG ,"startRecord");
        if(!MediaStorageManager.getInstance().isReady()){
            Log.d(TAG,"MediaStorageManager hasn't ready");
            return;
        }
        Log.d("qxj", "startAlertRecord---");
        alertMuxer = AlertMediaMuxer.getInstance();
        waitAlertForStart();
    }

    private void stopAlertRecord() {
        Log.d("qxj", " stopAlertRecord ");
        if (alertMuxer != null) {
            alertMuxer.stopMediaMuxer();
            alertMuxer = null;
        }
    }

    private Handler handler;
    private Runnable runnable;

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
                }, 500);
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

    public void resetStopTime(int type) {
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
            switch (type) {
                case 0:
                    handler.postDelayed(runnable, 30 * 1000);
                    break;
                case 1:
                    handler.postDelayed(runnable, 30 * 1000);
                    break;
            }
        }
    }

    //初始化 视频人脸识别
    private void startVideoFaceContrast() {
        Log.d("qxj", "开始人脸识别");
        List<FaceDatabase> faceDatabaseList = Utils.getAllFaceData();
        List<FaceDatabase> focusDatabaseList = Utils.getFocusFaceData();
        FeatureContrastManager feature = FeatureContrastManager.getInstance();
        feature.setSwitchContrast(true);

        if (faceDatabaseList != null && faceDatabaseList.size() > 0) {
            Log.d("qxj", "获取到数据库中的人脸数据不为空");
            feature.setFamilyFace(faceDatabaseList);
        }

        if (focusDatabaseList != null && focusDatabaseList.size() > 0) {
            feature.setFamilyFocusFace(focusDatabaseList);
        }
    }

    private void stopVideoFaceContrast() {
        FeatureContrastManager feature = FeatureContrastManager.getInstance();
        feature.setSwitchContrast(false);
    }
}
