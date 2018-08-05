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

import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import java.io.File;
import java.util.ArrayList;


public class AvMediaRecorder {
    private static final String TAG = AvMediaRecorder.class.getSimpleName();
    private static AvMediaRecorder mRecorder = new AvMediaRecorder();

    private AudioGather mAudioGather;
    private VideoGather mVideoGather;
    private AvMediaMuxer mMuxer;
    private Activity mActivity;
//    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private AudioEncoder mAudioEncoder;
    private VideoEncoder mVideoEncoder;
    private AvMediaTransfer mAvMeidaTransfer;
    private PCMAudioDataTransfer mPCMAudioDataTransfer;
    private SDCardBussiness mSDCardBussiness;
    private static final int START_RECORD = 200;
    private static final int STOP_RECORD = 201;
    private static final int WAIT_TO_START = 203;
    private static final int CHECK_STORAGE = 204;
    private static String VIDEO_ROOT_PATH ;
    private static int SD_MEM_THRESHOLD = 300; //SD card mem threshold
    public boolean ABBassistantRuning = true;
	private SDCardBroadcastReceiver mReceiver;


    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case START_RECORD:
                    startRecord();
                    break;
                case STOP_RECORD:
                    stopRecord();
                    break;
                case WAIT_TO_START:
                    waitForStart();
                    break;
                case CHECK_STORAGE:
                    deleteEarlyestFileIfNeed();
                    break;
                default:
                    break;
            }
        }
    };

    public class SDCardBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_REMOVED)){
                Log.d(TAG,"Intent.ACTION_MEDIA_REMOVED");
                stopRecord();

            } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED)){
                Log.d(TAG,"Intent.ACTION_MEDIA_MOUNTED");
                VIDEO_ROOT_PATH = mSDCardBussiness.getSDCardVideoRootPath() + "/" + "DCIM/";
                deleteEarlyestFileIfNeed();
                Log.d(TAG,"SDCardAvailable make Record");
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

        mSDCardBussiness = SDCardBussiness.getInstance();
//        mMuxer = AvMediaMuxer.newInstance();

        mAvMeidaTransfer = AvMediaTransfer.getInstance();
        mPCMAudioDataTransfer = PCMAudioDataTransfer.getInstance();

    }

    public void init() {
        if(!mAudioGather.AudioGatherRuning) {
            mAudioGather.SetAudioSourceTypeForMonitor();
            mAudioGather.prepareAudioRecord();
        }

        mAudioEncoder.init();
        mAudioGather.registerAudioRawDataListener(mPCMAudioDataTransfer);
        if(!mVideoGather.mIsPreviewing){
            mVideoGather.doOpenCamera();
            mVideoGather.doStartPreview(mActivity,mHolder);
        }
        setVideoParameter(mVideoGather.getPreWidth(),mVideoGather.getPreHeight(),mVideoGather.getFrameRate());
        mVideoEncoder.init();

        mReceiver = new SDCardBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addDataScheme("file");
        SmartCameraApplication.getContext().registerReceiver(mReceiver, filter);

//        if(mSDCardBussiness.isSDCardAvailable() ){
//            VIDEO_ROOT_PATH = mSDCardBussiness.getSDCardVideoRootPath() + "/" + "DCIM/";
//
//            deleteEarlyestFileIfNeed();
//            Log.d(TAG,"SDCardAvailable make Record");
//            startRecord();
//
//        }else {
//            Log.d(TAG,"SDCard not Available");
//        }

        if(Constant.TUTK_DEVICE_UID.length() == 20){
            //Transfer
            Log.d(TAG,"有效uid，绑定发送器");
            mVideoEncoder.registerEncoderListener(mAvMeidaTransfer);
            mAudioEncoder.registerEncoderListener(mAvMeidaTransfer);
        }else{
            Log.d(TAG,"无效uid，不绑定发送器");
        }
    }

    public void avMediaRecorderStart(){
        if(!mAudioGather.AudioGatherRuning){
            mAudioGather.startRecord();
        }
        if(!mVideoEncoder.isRunning()) {
            mVideoEncoder.start();
        }
        if(!mAudioEncoder.isRunning()) {
            mAudioEncoder.start();
        }
        if(mSDCardBussiness.isSDCardAvailable() ){
            VIDEO_ROOT_PATH = mSDCardBussiness.getSDCardVideoRootPath() + "/" + "DCIM/";

            deleteEarlyestFileIfNeed();
            Log.d(TAG,"SDCardAvailable make Record");
            startRecord();

        }else {
            Log.d(TAG,"SDCard not Available");
        }

        //有效uid
        if (Constant.TUTK_DEVICE_UID.length() == 20){
            Log.d(TAG,"有效uid，激活发送器");
            mAvMeidaTransfer.startAvMediaTransfer();
        }else{
            Log.d(TAG,"无效uid,不激活发送器");
        }

    }

    public void avMediaRecorderStop(){
        Log.d(TAG,"avMediaRecorderStop");
        SmartCameraApplication.getContext().unregisterReceiver(mReceiver);
        mVideoEncoder.stop();
        mVideoEncoder.unRegisterEncoderListener(mAvMeidaTransfer);
        new Handler().postDelayed(new Runnable(){
            public void run() {
                mVideoGather.doStopCamera();
                mVideoGather.unregisterVideoRawDataListener(mVideoEncoder);
            }
        }, 300);
        mAudioEncoder.stop();
        mAudioEncoder.unRegisterEncoderListener(mAvMeidaTransfer);
        new Handler().postDelayed(new Runnable(){
            public void run() {
            if(!ABBassistantRuning){
                mAudioGather.stopRecord();
                mAudioGather.unregisterAudioRawDataListener(mPCMAudioDataTransfer);
                mAudioGather.release();

            }
             mAudioGather.unregisterAudioRawDataListener(mAudioEncoder);
            Log.d(TAG,"mAudioGather stop transfer.");

            }
        }, 300);

        if(mMuxer != null){
            mMuxer.release();
        }
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
        if(!mSDCardBussiness.isSDCardAvailable() ){
            Log.d(TAG,"SD card removed ");
            return;
        }
        deleteEarlyestFileIfNeed();
        mMuxer = AvMediaMuxer.newInstance();
        mMuxer.initMediaMuxer();
        mAudioEncoder.registerEncoderListener(mMuxer);
        mVideoEncoder.registerEncoderListener(mMuxer);
        waitForStart();

    }

    private void waitForStart(){
        long tmpAvailableSpace = mSDCardBussiness.getAvailableSpace();
        if (tmpAvailableSpace < SD_MEM_THRESHOLD){
            Log.d(TAG,"AvailableSpace < 300M");
            deleteEarlyestFileIfNeed();

        }

        if(mMuxer == null){
            return;
        }
        if(!mMuxer.startMediaMuxer()){
            mHandler.sendEmptyMessageDelayed(WAIT_TO_START, 1000);
        } else {
            mHandler.sendEmptyMessageDelayed(STOP_RECORD, 5 * 60 * 1000);
        }
    }

    private void stopRecord(){
        Log.d(TAG ," stopRecord ");
        if(mMuxer!= null){
            mAudioEncoder.unRegisterEncoderListener(mMuxer);
            mVideoEncoder.unRegisterEncoderListener(mMuxer);
            mMuxer.stopMediaMuxer();
            mMuxer.release();
            mMuxer = null;
        }

        if(!mSDCardBussiness.isSDCardAvailable()){
            Log.d(TAG,"SD card has removed ");
            return;
        }
        MediaScannerConnection.scanFile(SmartCameraApplication.getContext(), new String[]{mSDCardBussiness.getSDCardVideoRootPath()}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
                Log.d(TAG,"onScanCompleted path = " + path + " uri = " + uri);
            }
        });
        mHandler.sendEmptyMessageDelayed(START_RECORD, 3000);

    }


    private void deleteEarlyestFileIfNeed(){

        long tmpAvailableSpace = mSDCardBussiness.getAvailableSpace();
        Log.d(TAG,"SDCard AvailableSpace = " + tmpAvailableSpace);
        if(tmpAvailableSpace <= SD_MEM_THRESHOLD){
            deleteEarlyestFromFloder(VIDEO_ROOT_PATH);
            mHandler.sendEmptyMessageDelayed(CHECK_STORAGE, 30 * 1000);
        }
    }

    private boolean deleteEarlyestFromFloder(String path) {
        boolean success = false;
        try {
            ArrayList<File> videos = new ArrayList<File>();
            getFiles(videos, path, ".mp4");
            File earlyestSavedVideo = videos.get(0);
            if (earlyestSavedVideo.exists()) {
                for (int i = 1; i < videos.size(); i++) {
                    File nextFile = videos.get(i);
                    if (nextFile.lastModified() < earlyestSavedVideo.lastModified()) {
                        earlyestSavedVideo = nextFile;
                    }
                }

                Log.d(TAG, "earlyest video = " + earlyestSavedVideo.getAbsolutePath());
                success = earlyestSavedVideo.delete();
            }
        } catch (Exception e) {
            Log.d(TAG,"deleteEarlyestFromFloder exception " + e.toString());
            e.printStackTrace();
        }
        return success;
    }

    private void getFiles(ArrayList<File> fileList, String path, String aExtension) {
        File[] allFiles = new File(path).listFiles();
        for (int i = 0; i < allFiles.length; i++) {
            File file = allFiles[i];
            if (file.isFile()) {
                if (file.getPath().substring(file.getPath().length() - aExtension.length()).equals(aExtension))
                    fileList.add(file);
            } else if (!file.getAbsolutePath().contains(".thumnail")) {
                getFiles(fileList, file.getAbsolutePath(), aExtension);
            }
        }
    }

}
