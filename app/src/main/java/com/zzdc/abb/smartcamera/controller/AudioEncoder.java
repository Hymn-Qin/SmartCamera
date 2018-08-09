package com.zzdc.abb.smartcamera.controller;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.util.Log;

import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioEncoder implements AudioGather.AudioRawDataListener{
    private static final String TAG = AudioEncoder.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String AUDIO_MIME_TYPE =MediaFormat.MIMETYPE_AUDIO_AAC;
    private final int TIMEOUT_USEC = 10000;
    public static MediaFormat AUDIO_FORMAT;

    private boolean mEncoding = false;
    private MediaCodec mEncoder;
    private MediaCodecInfo mCodecInfo;
    private MediaFormat mFormat;
    private long mStartTime;

    private LinkedBlockingQueue<AudioGather.AudioBuf> mAudioRawDataQueue = new LinkedBlockingQueue<>();
    //TODO remove
    private BufferPool<EncoderBuffer> mEncodeBufPool = new BufferPool<>(EncoderBuffer.class, 3);;

    private Thread mInputThread;
    public volatile boolean audioEncoderLoop = false;
    private LinkedBlockingQueue<Message> mInputIndexQueue = new LinkedBlockingQueue<>();

    private AudioEncoder() {}
    private static AudioEncoder mInstance = new AudioEncoder();
    public static AudioEncoder getInstance() {
        return mInstance;
    }

    private HandlerThread mEncoderThread = new HandlerThread("Audio encode thread");
    {
        mEncoderThread.start();
    }
    private Handler mEncodeHandler = new Handler(mEncoderThread.getLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            try {
                mEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            } catch (IOException e) {
                throw new RuntimeException("Create audio encoder failed!", e);
            }
        }
    };

    class InputThread extends Thread {


        @Override
        public void run() {
            super.run();
            while (audioEncoderLoop && !Thread.interrupted()){
                try {
                    int i = mInputIndexQueue.take().arg1;
                    if (i >= 0){
                        AudioGather.AudioBuf tmpBuffer = mAudioRawDataQueue.take();
                        debug("Handle onInputBufferAvailable, buffer index = " + i);
                        ByteBuffer inputBuffer = mEncoder.getInputBuffer(i);
                        inputBuffer.put(tmpBuffer.getData());
                        tmpBuffer.decreaseRef();

                        long pts = System.currentTimeMillis() * 1000 - mStartTime;
                        if (!mEncoding) {
                            Log.d(TAG, "Send Audio Encoder with flag BUFFER_FLAG_END_OF_STREAM");
                            mEncoder.queueInputBuffer(i, 0, tmpBuffer.getData().length, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            mAudioRawDataQueue.clear();
                        }else{
                            mEncoder.queueInputBuffer(i, 0, tmpBuffer.getData().length, pts, 0);
                        }
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            unregisterAudioRawDataListener();
            if(mEncoder!=null){
                mEncoder.reset();
            }
            mInputIndexQueue.clear();
            mAudioRawDataQueue.clear();
            Log.d(TAG, "Encoder input thread stop. InputIndex_Que size ="+mInputIndexQueue.size());
        }
    }


//    private HandlerThread mInputThread = new HandlerThread("Audio encode input thread");
//    {
//        mInputThread.start();
//    }
//    private Handler mInputHandler = new Handler(mInputThread.getLooper()) {
//        @Override
//        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
//            MediaCodec mediaCodec = (MediaCodec)msg.obj;
//            int i = msg.arg1;
//            try {
//                AudioGather.AudioBuf audioRawBuf = mAudioRawDataQueue.take();
//                debug("Handle onInputBufferAvailable, buffer index = " + i);
//                ByteBuffer buffer = mediaCodec.getInputBuffer(i);
//                buffer.put(audioRawBuf.getData());
//                audioRawBuf.decreaseRef();
//                long pts = System.currentTimeMillis() * 1000 - mStartTime;
//                if (!mEncoding) {
//                    Log.d(TAG, "Send Audio Encoder with flag BUFFER_FLAG_END_OF_STREAM");
//                    mediaCodec.queueInputBuffer(i, 0, audioRawBuf.getData().length, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                    mAudioRawDataQueue.clear();
//                } else {
//                    mediaCodec.queueInputBuffer(i, 0, audioRawBuf.getData().length, pts, 0);//TODO check the flag
//                }
//            } catch (InterruptedException e) {
//                LogTool.w(TAG, "Take Audio raw data from queue with exception. ", e);
//            }
//        }
//    };

    private void unregisterAudioRawDataListener(){
        mAudioGather.unregisterAudioRawDataListener(this);
    }

    private MediaCodec.Callback mEncodeCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
            debug("onInputBufferAvailable, buffer index = " + i);
            Message msg = new Message();
            msg.obj = mediaCodec;
            msg.arg1 = i;
//            mInputHandler.sendMessage(msg);
            mInputIndexQueue.offer(msg);
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
            debug("onOutputBufferAvailable, buffer index = " + i);
            if( !audioEncoderLoop)
                return;
            ByteBuffer buffer = mediaCodec.getOutputBuffer(i);
            if (mAudioEncoderListeners.size() > 0) {
                EncoderBuffer buf = mEncodeBufPool.getBuf(buffer.remaining());
                buf.put(buffer, bufferInfo);
                for (AudioEncoderListener listener : mAudioEncoderListeners) {
                    listener.onAudioEncoded(buf);
                }
                buf.decreaseRef();
            }
            mediaCodec.releaseOutputBuffer(i, false); //TODO check the render value
        };

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            LogTool.w(TAG,"onError", e);
            //TODO
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            LogTool.d(TAG,"onOutputFormatChanged");
            AUDIO_FORMAT = mediaFormat;
            if (mAudioEncoderListeners.size() > 0) {
                for (AudioEncoderListener listener : mAudioEncoderListeners) {
                    listener.onAudioFormatChanged(mediaFormat);
                }
            }
        }
    };

    AudioGather mAudioGather = null;

    public void init(){
        LogTool.d(TAG, "init");
        mAudioGather = AudioGather.getInstance();
        int sampleRate = mAudioGather.getSampleRate();
        int pcmFormat =  mAudioGather.getPcmFormat();
        int chanelCount = mAudioGather.getChannelCount();

        mEncodeBufPool.setDebug(DEBUG);
        mCodecInfo = selectCodec(AUDIO_MIME_TYPE);
        if (mCodecInfo == null) {
            throw(new RuntimeException("\"Unable to find an appropriate codec for \" + AUDIO_MIME_TYPE"));
        }

        mFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, sampleRate, chanelCount);
        mFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        //TODO check the bit rate
        int bitRate = sampleRate * pcmFormat * chanelCount;
        mFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
        mFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, chanelCount);
        mFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate);
        Log.d(TAG, "Audio format: " + mFormat.toString());

        mEncodeHandler.sendEmptyMessage(1);
    }

    public void start() {
        LogTool.d(TAG, "start");
        if (mEncoder == null) {
            for (int i=0; i<10; i++) {
                LogTool.w(TAG, "Sleep 1 second to wait the audio encoder created. i = " + i);
                try {
                    Thread.sleep(1000);
                    if (mEncoder != null) {
                        break;
                    }
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "Exception when sleeping", e);
                }
            }
        }

        if (mEncoder == null) {
            throw new RuntimeException("Audio encoder is null.");
        }

        mEncoder.reset();
        mEncoder.setCallback(mEncodeCallback);
        mEncoder.configure(mFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mEncoding = true;
        mAudioGather.registerAudioRawDataListener(this);
        mEncoder.start();
        mStartTime = System.currentTimeMillis() * 1000;
        mInputThread = new InputThread();
        audioEncoderLoop = true;
        mInputThread.start();
    }

    public void stop() {
        LogTool.d(TAG, "stop");
//        mAudioGather.unregisterAudioRawDataListener(this);
//        if (mEncoder != null){
//            mEncoder.reset();
//        }
        mEncoding = false;
        mInputIndexQueue.clear();
        audioEncoderLoop = false;
    }

    public boolean isRunning() {
        return mEncoding;
    }

    private CopyOnWriteArrayList<AudioEncoderListener> mAudioEncoderListeners = new CopyOnWriteArrayList<>();
    public void registerEncoderListener(AudioEncoderListener listener) {
        if (!mAudioEncoderListeners.contains(listener)) {
            mAudioEncoderListeners.add(listener);
        }
    }

    public void unRegisterEncoderListener (AudioEncoderListener listener) {
        mAudioEncoderListeners.remove(listener);
    }

    public interface AudioEncoderListener {
        void onAudioEncoded(EncoderBuffer buf);
        void onAudioFormatChanged(MediaFormat format);
    }

    @Override
    public void onAudioRawDataReady(AudioGather.AudioBuf buf) {
        buf.increaseRef();
        mAudioRawDataQueue.offer(buf);
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private void debug(String msg) {
        if (DEBUG || MainActivity.AUDIO_ENCODE_DEBUG) LogTool.d(TAG, msg);
    }
}