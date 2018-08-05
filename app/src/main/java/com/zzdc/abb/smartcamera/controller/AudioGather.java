package com.zzdc.abb.smartcamera.controller;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.util.ArrayList;

public class AudioGather {
    private static final String TAG = "AudioGather";
    private static final boolean DEBUG = false;

    private static AudioGather mAudioGather = new AudioGather();
    private AudioRecord audioRecord;
    private int mChannelCount;
    private int mSampleRate;
    private int mPcmFormat;
    private int mBufferSize;
    private byte[] audioBuf;
    private int mAudioRecordSessionId;
    private DenoiseManager mDenoiseManager;
    private int AudioSourceType = MediaRecorder.AudioSource.VOICE_COMMUNICATION;

    private Thread workThread;
    private volatile boolean loop = false;
    public volatile boolean AudioGatherRuning = false;

    private BufferPool<AudioBuf> mBufPool = null;

    public static class AudioBuf extends BufferPool.Buf {
        private byte[] data;
        private static int mSize = 0;

        public AudioBuf() {
            data = new byte[mSize];
        }

        public byte[] getData() {
            return data;
        }

        public static void setSize(int s) {
            mSize = s;
        }
    }
    public static AudioGather getInstance() {
        return mAudioGather;
    }

    private AudioGather() {

    }
    public void SetAudioSourceTypeForCall() {
        AudioSourceType = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    }
    public void SetAudioSourceTypeForMonitor() {
        AudioSourceType = MediaRecorder.AudioSource.MIC;
    }

    public void prepareAudioRecord() {
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        //音频采样率，44100是目前的标准，但是某些设备仍然支持22050,16000,11025,8000,4000
        int[] sampleRates = {16000, 8000, 4000};
        //int[] sampleRates = {16000, 11025, 8000, 4000};
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        try {
            for (int sampleRate : sampleRates) {
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                int minBufSize = 1 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                audioRecord = new AudioRecord(AudioSourceType, sampleRate, channelConfig, audioFormat, minBufSize);

                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    audioRecord = null;
                    LogTool.d(TAG, "Audio record can't support sampleRate(" + sampleRate+")");
                    continue;
                }
/*                if(DenoiseManager.isDevSupportDenoise()){
                    Log.d(TAG,"支持降噪");
                    audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, min_buffer_size);
                    mAudioRecordSessionId = audioRecord.getAudioSessionId();
                    mDenoiseManager = new DenoiseManager(mAudioRecordSessionId);
                    mDenoiseManager.setAECEnable(true);
                    AudioManager audioManager = (AudioManager) SmartCameraApplication.getContext().getSystemService(Context.AUDIO_SERVICE);
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    audioManager.setSpeakerphoneOn(true);
                }else {
                    audioRecord = new AudioRecord(AudioSourceType, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_8BIT, min_buffer_size);
                }*/
                //     SessionId = audioRecord.getAudioSessionId();
            //    Log.d(TAG,"不支持降噪" );

                mSampleRate = sampleRate;
                mChannelCount = channelConfig == AudioFormat.CHANNEL_CONFIGURATION_STEREO ? 2 : 1;
                mPcmFormat = (audioFormat == AudioFormat.ENCODING_PCM_8BIT) ? 8 : 16;
                mBufferSize = minBufSize;
                AudioBuf.setSize(mBufferSize);
                mBufPool = new BufferPool<>(AudioBuf.class, 3);
                mBufPool.setDebug(DEBUG);
                LogTool.d(TAG, "Audio record started,  SampleRate = " + mSampleRate
                        + ", ChannelCount = " + mChannelCount
                        + ", PcmFormat = " + mPcmFormat
                        + ", BufferSize = " + mBufferSize);
                break;
            }
        } catch (final Exception e) {
            Log.e(TAG, "AudioThread#run", e);
        }
    }

    public int getChannelCount() {
        return mChannelCount;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getPcmFormat() {
        return mPcmFormat;
    }

    /**
     * 开始录音
     */
    public void startRecord() {
        if(loop)
            return;
        workThread = new Thread() {
            @Override
            public void run() {
                if (audioRecord != null) {
                    audioRecord.startRecording();
                }
                while (loop && !Thread.interrupted()) {
                    //读取音频数据到buf
                    int size = 0;
                    AudioBuf audioRawBuf = mBufPool.getBuf();
                    if (audioRecord != null){
                        size = audioRecord.read(audioRawBuf.getData(),0,audioRawBuf.getData().length);
                    }
                    debug("AudioRecord read. size = " +size + ",  SampleRate = " + mSampleRate
                            + ", ChannelCount = " + mChannelCount
                            + ", PcmFormat = " + mPcmFormat
                            + ", BufferSize = " + mBufferSize);

                    if (size > 0) {
                        for (AudioRawDataListener listener: mAudioRawDataListeners) {
                            listener.onAudioRawDataReady(audioRawBuf);
                        }
                    }
                    audioRawBuf.decreaseRef();
                }
                LogTool.d(TAG, "Audio record stoped...");
            }
        };

        loop = true;
        workThread.start();
        AudioGatherRuning = true;
    }

    public void stopRecord() {
        Log.d(TAG, "run: ===zhongjihao====停止录音======");
        if(audioRecord != null)
            audioRecord.stop();
        loop = false;
        if(workThread != null)
            workThread.interrupt();
        AudioGatherRuning = false;
    }

    public void release() {
        if(audioRecord != null)
            audioRecord.release();
        audioRecord = null;
        if(mDenoiseManager !=null)
            mDenoiseManager.release();
    }

    ArrayList<AudioRawDataListener> mAudioRawDataListeners = new ArrayList<>();
    public void registerAudioRawDataListener(AudioRawDataListener listener) {
        if (!mAudioRawDataListeners.contains(listener)) {
            mAudioRawDataListeners.add(listener);
        }
    }

    public void unregisterAudioRawDataListener (AudioRawDataListener listener) {
        if (mAudioRawDataListeners.contains(listener)) {
            mAudioRawDataListeners.remove(listener);
        }
    }

    public interface AudioRawDataListener {
        public void onAudioRawDataReady(AudioBuf buf);
    }

    private void debug(String msg) {
        if (DEBUG) {
            LogTool.d(TAG, msg);
        }
    }
}
