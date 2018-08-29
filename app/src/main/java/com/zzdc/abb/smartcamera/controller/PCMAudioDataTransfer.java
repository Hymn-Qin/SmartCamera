package com.zzdc.abb.smartcamera.controller;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.foxconn.abbassistant.AssistantAudioInterface;
import com.foxconn.abbassistant.AudioData;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

//import com.foxconn.abbassistant.AssistantAudioInterface;
//import com.foxconn.abbassistant.AudioData;

/**
 * 文件名: PCMAudioDataTransfer
 * 功  能：向其他应用提供原始的pcm声音数据
 * 作  者：李治鹏
 * 版  本：1.0
 */
public class PCMAudioDataTransfer implements AudioGather.AudioRawDataListener {
    private static final String TAG = PCMAudioDataTransfer.class.getSimpleName();
    private static final PCMAudioDataTransfer mInstance = new PCMAudioDataTransfer();

    private AssistantAudioInterface mAssistantAudioInterface;
    private final int DEFAULT_MODE = 0;
    private final int MONITOR_MODE = 1;
    //关闭语音
    public static final int TELEPHONY_ON_MODE = 4;
    //打开语音
    public static final int TELEPHONY_OFF_MODE = 5;
    private boolean isReady = false;
    private AudioData mAudioData = new AudioData();
    private PCMAudioDataTransfer(){

        init();
    }

    public static PCMAudioDataTransfer getInstance(){
        return mInstance;
    }

    private void init(){

        attemptToBindService();

    }
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG,"onServiceConnected");
            mAssistantAudioInterface =  AssistantAudioInterface.Stub.asInterface(service);
            isReady = true;
            changeVoiceMode(MONITOR_MODE);

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isReady = false;
            release();
        }
    };

    private void release(){
        SmartCameraApplication.getContext().unbindService(mServiceConnection);
    }

    public void changeVoiceMode(int aMode){
        Log.d(TAG,"mode="+aMode+",ready = "+isReady);
        try {
            if (!isReady) {
                attemptToBindService();
                return;
            }
            Log.d(TAG,"ready = "+isReady);
            mAssistantAudioInterface.getAudioMode(aMode);
        }catch (Exception e){
            e.printStackTrace();
            Log.d(TAG,"mAssistantAudioInterface.getAudioMode " + e.toString());
        }
    }



    private void sendPCMAudioData(AudioGather.AudioBuf buf){
        try {

            if (!isReady) {
                attemptToBindService();
                return;
            }

            if (isReady) {
                mAudioData.setPcm(buf.getData());
                mAssistantAudioInterface.getByte(mAudioData);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAudioRawDataReady(AudioGather.AudioBuf buf) {
        try {
//            Log.d(TAG,"onAudioRawDataReady " + data.length);
//            mDataQueue.put(data);
            sendPCMAudioData(buf);
        } catch (Exception e) {
            Log.d(TAG,"mDataQueue.put InterruptedException " + e.toString());
        }
    }

    private void attemptToBindService() {
        Intent tmpIntent = new Intent();
        tmpIntent.setPackage("com.foxconn.abbassistant");
        tmpIntent.setAction("com.foxconn.assistantaudio");
        SmartCameraApplication.getContext().bindService(tmpIntent,
                mServiceConnection,
                SmartCameraApplication.getContext().BIND_AUTO_CREATE);
    }
}
