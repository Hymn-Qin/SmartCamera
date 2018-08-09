package com.zzdc.abb.smartcamera.controller;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.zzdc.abb.smartcamera.FaceFeature.FeatureContrastManager;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressLint("NewApi")
public class VideoGather {
    private static final String TAG = VideoGather.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int PREVIEW_WIDTH = 1920;
    private static final int PREVIEW_HEIGHT = 1080;
    private int preWidth = PREVIEW_WIDTH;
    private int preHeight = PREVIEW_HEIGHT;
    private int mSetWidth = PREVIEW_WIDTH;
    private int mSetHeight = PREVIEW_HEIGHT;
    private int frameRate = 20;

    private static final String KEY_QC_ZSL = "zsl";
    private static VideoGather mVideoGather = new VideoGather();

    // 定义系统所用的照相机
    private Camera mCamera;

    public boolean mIsPreviewing = false;
    private CameraPreviewCallback mCameraPreviewCallback;
    private final  String KEY_QC_SATURATION = "saturation";
    private final  String KEY_QC_CONTRAST = "contrast";
    private final  String KEY_QC_SHARPNESS = "sharpness";
    private final  String  KEY_QC_AE_BRACKET_HDR = "ae-bracket-hdr";
    public final String KEY_SNAPCAM_HDR_MODE = "hdr-mode";
    public final String KEY_SNAPCAM_HDR_NEED_1X = "hdr-need-1x";
    public final String KEY_QC_AUTO_EXPOSURE = "auto-exposure";

//    private CameraOperateCallback cameraCb;
    private Context mContext;

    private NightModeControl mNightModeControl;
    private boolean NightModeEnable = false;
    private static final int NightModeOn = 1;
    private static final int NightModeOff = 0;
    private int NightModeState = NightModeOff;
    private NightModeThread mthread;
    private static final int OFF = 0;
    private static final int ON = 1;
    private static final int AUTO = 2;
    private int NightMode = AUTO;

    private int NightModeEnable_limmit= 2;
    private int NightModedisable_limmit= 10;
    private int NightModeCheckCount= 3; // 4 seconds
    private int NightModeEnableCount = 0;
    private int NightModeDisableCount = 0;
    private boolean NightModeRuning = false;

    private BufferPool<VideoRawBuf> mBufPool = null;
    private VideoRawBuf mVideoRawBuf = null;

    public static class VideoRawBuf extends BufferPool.Buf {
        private byte[] data;
        private static int mSize = 0;

        public VideoRawBuf() {
            data = new byte[mSize];
        }

        public byte[] getData() {
            return data;
        }

        public static void setSize(int s) {
            mSize = s;
        }
    }

    private VideoGather() {
    }

//    public interface CameraOperateCallback {
//        public void cameraHasOpened();
//        public void cameraHasPreview(int width,int height,int fps);
//    }
    public int getPreWidth(){
        return preWidth;
    }
    public int getPreHeight(){
        return preHeight;
    }
    public int getFrameRate(){
        return frameRate;
    }

    private CopyOnWriteArrayList<VideoRawDataListener> mVideoRawDataListeners = new CopyOnWriteArrayList<>();
    public interface VideoRawDataListener {
        public void onVideoRawDataReady(VideoRawBuf buf);
    }

    public void registerVideoRawDataListener (VideoRawDataListener listener) {
        if (!mVideoRawDataListeners.contains(listener)) {
            mVideoRawDataListeners.add(listener);
        }
    }

    public void unregisterVideoRawDataListener (VideoRawDataListener listener) {
        mVideoRawDataListeners.remove(listener);
    }

    public static VideoGather getInstance() {
        return mVideoGather;
    }

    public void doOpenCamera() {
        Log.d(TAG, "====Camera open....");
        //cameraCb = callback;
        if(mCamera != null)
            return;
        if (mCamera == null) {
            try {
                Log.d(TAG, "No front-facing camera found; opening default");
                final int CAMERA_HAL_API_VERSION_1_0 = 0x100;
                final int REAR_CAMERA = 0;
                Method openMethod = Class.forName("android.hardware.Camera").getMethod(
                        "openLegacy", int.class, int.class);
                mCamera = (android.hardware.Camera) openMethod.invoke(
                        null, REAR_CAMERA, CAMERA_HAL_API_VERSION_1_0);
            } catch (Exception e) {
                Log.e(TAG, "Open camera fail ed. ", e);
                return;
            }
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }
        Log.d(TAG, "=====Camera open over....");
      //  cameraCb.cameraHasOpened();
    }

    public void doStartPreview(Activity activity,SurfaceHolder surfaceHolder) {
        if (mIsPreviewing) {
            return;
        }
        mContext = activity;
        setCameraDisplayOrientation(activity, Camera.CameraInfo.CAMERA_FACING_BACK);
        setCameraParameter();

        mNightModeControl = new NightModeControl(mContext);
        NightModeThread mthread =new NightModeThread();
        NightModeRuning = true;
        mthread.start();

        try {
            // 通过SurfaceView显示取景画面
            mCamera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            Log.w(TAG, "", e);
        }

        mCamera.startPreview();
        mCamera.cancelAutoFocus();
        mIsPreviewing = true;
        Log.d(TAG, "===Camera Preview Started... preWidth " + preWidth + " preHeight " + preHeight + " frameRate " + frameRate);
      //  cameraCb.cameraHasPreview(preWidth,preHeight,frameRate);
    }

    public void doStopCamera() {
        Log.d(TAG, "=======doStopCamera");
        // 如果camera不为null，释放摄像头
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(null);
            if (mIsPreviewing)
                mCamera.stopPreview();
            mIsPreviewing = false;
            mCamera.release();
            mCamera = null;
            NightModeRuning = false;
            mNightModeControl.unrigsterListener();
        }
        if(mthread != null){
            mthread.stop();
        }
        mContext = null;
    }

    public void setPreviewSize(int width, int high) {
        mSetWidth = width;
        mSetHeight = high;
        //TODO Stop preview and restart.
    }

    private void generatePreviewSize(List<Camera.Size> preSize) {
        boolean found = false;
        for (Camera.Size size: preSize) {
            if (size.width == mSetWidth && size.height == mSetHeight) {
                found = true;
                break;
            }
        }
        if (found) {
            preWidth = mSetWidth;
            preHeight = mSetHeight;
            LogTool.d(TAG, "Set preview size (width=" + mSetWidth + ", height=" + mSetHeight + ").");
        } else {
            preWidth = PREVIEW_WIDTH;
            preHeight = PREVIEW_HEIGHT;
            LogTool.w(TAG, "Doesn't fond preview size (width=" + mSetWidth + ", height=" + mSetHeight + ")."
                    + " Set default preview size (width="+PREVIEW_WIDTH+ ",  height="+PREVIEW_HEIGHT+").");
        }
    }

    private void setCameraParameter() {
        if (!mIsPreviewing && mCamera != null) {
            Camera.Parameters paramters = mCamera.getParameters();
            paramters.setPreviewFormat(ImageFormat.NV21);
            paramters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            paramters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            paramters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

            generatePreviewSize(paramters.getSupportedPreviewSizes());
            paramters.setPreviewSize(preWidth, preHeight);

            paramters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            //paramters.setPictureSize(640,480);
            paramters.set(KEY_QC_ZSL,"on");
            paramters.set(KEY_QC_SATURATION,5);
            paramters.set(KEY_QC_CONTRAST,5);
            paramters.set(KEY_QC_SHARPNESS,12);
            paramters.set(KEY_QC_AE_BRACKET_HDR,"Off");
            paramters.set(KEY_SNAPCAM_HDR_MODE,"hdr-mode-multiframe");
            paramters.set(KEY_SNAPCAM_HDR_NEED_1X,"true"); //increase the sharpness in night mode.
            paramters.set(KEY_QC_AUTO_EXPOSURE,"frame-average");

            paramters.setPreviewFpsRange(frameRate*1000,frameRate*1000);

            VideoRawBuf.setSize(bufLength(ImageFormat.NV21));
            mBufPool = new BufferPool<>(VideoRawBuf.class, 3);
            mBufPool.setDebug(DEBUG);
            mVideoRawBuf = mBufPool.getBuf();
            mCamera.addCallbackBuffer(mVideoRawBuf.getData());

            mCamera.setPreviewCallbackWithBuffer(new CameraPreviewCallback());
            mCamera.setParameters(paramters);
        }
    }

    private int bufLength(int format) {
        return preWidth * preHeight * ImageFormat.getBitsPerPixel(format) / 8;
    }

    private void setCameraDisplayOrientation(Activity activity,int cameraId) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result = (info.orientation - degrees + 360 - 90) % 360;
        mCamera.setDisplayOrientation(result);
    }

    class CameraPreviewCallback implements Camera.PreviewCallback {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if(data != null){
                debug("onPreviewFrame: mVideoRawDataListeners size = "+mVideoRawDataListeners.size());
                if (data == mVideoRawBuf.getData()) {
                    //添加人脸识别
                    FeatureContrastManager featureContrastManager = FeatureContrastManager.getInstance();
                    featureContrastManager.startContrastFeature(data, preWidth, preHeight);

                    for (VideoRawDataListener listener: mVideoRawDataListeners) {
                        listener.onVideoRawDataReady(mVideoRawBuf);
                    }
                    mVideoRawBuf.decreaseRef();
                } else {
                    LogTool.w(TAG, "onPreviewFrame returned buffer error. data = " + data + "buffer.data = " + mVideoRawBuf.getData());
                }
            }
            mVideoRawBuf = mBufPool.getBuf();
            if (mVideoRawBuf != null) {
                camera.addCallbackBuffer(mVideoRawBuf.getData());
            }
        }
    }

    class NightModeThread extends Thread {
        @Override
        public void run() {
            while (NightModeRuning) {
                try {
                    sleep(1000);
                } catch (Exception e) {
                    LogTool.w(TAG, "", e);
                }
                if(NightMode == OFF){
                    NightModeEnable = false;
                } else if(NightMode == ON){
                    NightModeEnable = true;
                } else if(NightMode == AUTO) {
                    if (mNightModeControl.Current_Light_value < NightModeEnable_limmit) {
                        NightModeEnableCount = NightModeEnableCount + 1 ;
             //           Log.d("keming","NightModeEnableCount ="+ NightModeEnableCount);
                        if(NightModeEnableCount < 0)
                        {
                            NightModeEnableCount = 0;
                        } else if (NightModeEnableCount > NightModeCheckCount)
                        {
                            NightModeEnableCount = NightModeCheckCount;
                            NightModeEnable = true;
                        }
                        NightModeDisableCount = 0;
                    } else if (mNightModeControl.Current_Light_value > NightModedisable_limmit) {
                        NightModeDisableCount = NightModeDisableCount + 1;
                   //     Log.d("keming","NightModeDisableCount ="+ NightModeDisableCount);
                        if(NightModeDisableCount < 0)
                        {
                            NightModeDisableCount = 0;
                        } else if (NightModeDisableCount > NightModeCheckCount)
                        {
                            NightModeDisableCount = NightModeCheckCount;
                            NightModeEnable = false;
                        }

                        NightModeEnableCount = 0;
                    }
                }

                if(NightModeState == NightModeOff){
                    if(NightModeEnable){
                        Camera.Parameters params = mCamera.getParameters();
                        List<String> ColorEffects = params.getSupportedColorEffects();
                        if (ColorEffects.contains(
                                Camera.Parameters.EFFECT_MONO)) {
                            params.setColorEffect(Camera.Parameters.EFFECT_MONO);
                        }
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        mCamera.setParameters(params);
                        NightModeState = NightModeOn;
                    }
                }else if(NightModeState == NightModeOn){
                    if(NightModeEnable == false){
                        Camera.Parameters params = mCamera.getParameters();
                        List<String> ColorEffects = params.getSupportedColorEffects();
                        if (ColorEffects.contains(
                                Camera.Parameters.EFFECT_NONE)) {
                            params.setColorEffect(Camera.Parameters.EFFECT_NONE);
                        }
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        mCamera.setParameters(params);
                        NightModeState = NightModeOff;
                    }
                }
            }
        }
    }

    private void debug(String msg) {
        if (DEBUG || MainActivity.VIDEO_GATHER_DEBUG) LogTool.d(TAG, msg);
    }
}