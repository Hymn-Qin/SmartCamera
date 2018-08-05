package com.zzdc.abb.smartcamera.controller;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioPlayer {

    private static final String TAG = AudioPlayer.class.getSimpleName();
    private AudioTrack mAudioTrack;
    private int mFrequency;
    private int mChannel;
    private int mSampleBit;
    private AudioManager mAudioManager;
    private  int  AudioTrackType = AudioManager.STREAM_MUSIC;


    public AudioPlayer(int aFrequency, int aChannel, int aSampleBit) {

        mFrequency = aFrequency;
        mChannel = aChannel;
        mSampleBit = aSampleBit;
    }

    public void SetAudioTrackTypeForCall() {
        AudioTrackType = AudioManager.STREAM_VOICE_CALL;
        mAudioManager = (AudioManager) MainActivity.getContext().getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        mAudioManager.setSpeakerphoneOn(true);
    }
    public void SetAudioTrackTypeForMonitor() {
        AudioTrackType = AudioManager.STREAM_MUSIC;
        mAudioManager = (AudioManager) MainActivity.getContext().getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
}

    public void prepare(){

        Log.d(TAG,"aFrequency " + mFrequency + " mChannel "+ mChannel +" mSampleBit " + mSampleBit);
        int tmpMinBufSize = getMinBufferSize();
        mAudioTrack = new AudioTrack(AudioTrackType, mFrequency, mChannel,
                mSampleBit, tmpMinBufSize, AudioTrack.MODE_STREAM);
        mAudioTrack.play();
    }

    public void playAudioTrack(byte[] data, int aOffset, int aLength){
        if (data == null || data.length ==0){
            return;
        }
        try{
            mAudioTrack.write(data, aOffset,aLength);
        }catch (Exception e){
            Log.d(TAG,"playAudioTrack Exception" + e.toString());
        }

    }
    public  void  pause(){
        try{
            mAudioTrack.pause();
        }catch (Exception e){
            Log.d(TAG,"mAudioTrack.pause Exception" + e.toString());
        }

    }
    public void stop(){
        try{
            mAudioTrack.stop();
        }catch (Exception e){
            Log.d(TAG,"mAudioTrack.stop" + e.toString());
        }
    }

    public void release(){
        if (mAudioTrack != null){
            mAudioTrack.stop();
            mAudioTrack.release();
        }

    }


    public int getMinBufferSize() {
        return AudioTrack.getMinBufferSize(mFrequency,
                mChannel, mSampleBit);
    }
}
