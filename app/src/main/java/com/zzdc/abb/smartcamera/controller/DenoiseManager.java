package com.zzdc.abb.smartcamera.controller;

import android.media.AudioManager;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;

import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

public class DenoiseManager {

    private int mSessionId;
    private AcousticEchoCanceler mAcousticEchoCanceler;
    AudioManager mAudioManager;

    private  static Boolean isOSVersionSuppot(){
        return Build.VERSION.SDK_INT >= 16;
    }
    public  static boolean isDevSupportDenoise(){
        if (!isOSVersionSuppot())
            return false;
        return AcousticEchoCanceler.isAvailable();
    }

    public DenoiseManager(int aSessionId){
        mSessionId = aSessionId;
    }
    public boolean initAEC(){
        if (!isDevSupportDenoise())
            return false;
        if (mAcousticEchoCanceler != null)
            return false;

        mAcousticEchoCanceler = AcousticEchoCanceler.create(mSessionId);
        mAcousticEchoCanceler.setEnabled(true);
        return mAcousticEchoCanceler.getEnabled();
//        AudioManager audioManager = (AudioManager) SmartCameraApplication.getContext().getSystemService(Context.AUDIO_SERVICE);


    }

    public boolean setAECEnable(boolean aIsEnable){
        if (null == mAcousticEchoCanceler)
            return false;
        mAcousticEchoCanceler.setEnabled(aIsEnable);
        return mAcousticEchoCanceler.getEnabled();
    }

    public boolean release(){
        if (null == mAcousticEchoCanceler)
            return false;
        mAcousticEchoCanceler.setEnabled(false);
        mAcousticEchoCanceler.release();
        return true;
    }


}
