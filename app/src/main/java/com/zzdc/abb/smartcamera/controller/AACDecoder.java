package com.zzdc.abb.smartcamera.controller;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import com.zzdc.abb.smartcamera.TutkBussiness.TutkSession;
import com.zzdc.abb.smartcamera.util.ByteToHexTool;
import com.zzdc.abb.smartcamera.util.WriteToFileTool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class AACDecoder implements TutkSession.RemoteAudioDataMonitor{
    private static final String TAG = AACDecoder.class.getSimpleName();

    private MediaCodec mAudioDecorder;

    //声道数
    private static final int KEY_CHANNEL_COUNT = 1;
    //采样率
    private static final int KEY_SAMPLE_RATE = 16000;
    //用来记录解码失败的帧数
    private int count = 0;
    private boolean AudiotrackRuning = false;
    private boolean AudioDecodeRuning = false;


    public AudioPlayer mAudioPlayer;
    private Thread mAudioDecodeThread;
    private String filePath = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator +"test.aac";
    private DataInputStream mInputStream;

    private long mFailcount = 0;
    private LinkedBlockingQueue<byte[]> mAudioQueue = new LinkedBlockingQueue<>();
    private boolean isTestLocalFile = false;
    private boolean isDataReady = false;
    private Thread mRenderThread;
    public void SetAudioTrackTypeForCall(){
        mAudioPlayer = new AudioPlayer(KEY_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        mAudioPlayer.SetAudioTrackTypeForCall();

    }
    public void  SetAudioTrackTypeForMonitor(){
        mAudioPlayer.SetAudioTrackTypeForMonitor();

    }

    public void init(){

        mAudioPlayer.prepare();
        try {

            //需要解码数据的类型
            String mine = "audio/mp4a-latm";
            //初始化解码器
            mAudioDecorder = MediaCodec.createDecoderByType("audio/mp4a-latm");
            MediaFormat mediaFormat = new MediaFormat();
            //MediaFormat用于描述音视频数据的相关参数
            mediaFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, KEY_CHANNEL_COUNT);
            mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, KEY_SAMPLE_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            byte[] data = new byte[]{(byte) 0x14, (byte) 0x08};
            ByteBuffer csd_0 = ByteBuffer.wrap(data);
            mediaFormat.setByteBuffer("csd-0", csd_0);
            //解码器配置
            mAudioDecorder.configure(mediaFormat, null, null, 0);


        } catch (Exception e) {
            Log.d(TAG, "Exception " + e.toString());
            e.printStackTrace();
        }
        if(isTestLocalFile){
            prepare();
        }
        mAudioDecorder.start();
        mAudioDecodeThread = new Thread(new AudioDecodeThread());
        AudioDecodeRuning = true;
        mAudioDecodeThread.start();
        mRenderThread = new Thread(new RenderThread());
        AudiotrackRuning = true;
        mRenderThread.start();

    }
    public void deinit(){
        AudiotrackRuning = false;
        AudioDecodeRuning = false;
       // stop();

    }



    @Override
    public void onAudioDataReceive(byte[] data, int size) {

        byte[] copy = new byte[size];

        try{
            System.arraycopy(data, 0, copy, 0, size);
            Log.d(TAG,"onAudioDataReceive " + copy.length);
            mAudioQueue.put(copy);
        }catch (Exception e){
            Log.d(TAG,"queue.offer Exception " + e.toString());
        }

    }

    class RenderThread implements Runnable {

        /**
         * When an object implementing interface <code>Runnable</code> is used
         * to create a thread, starting the thread causes the object's
         * <code>run</code> method to be called in that separately executing
         * thread.
         * <p>
         * The general contract of the method <code>run</code> is that it may
         * take any action whatsoever.
         *
         * @see Thread#run()
         */
        @Override
        public void run() {
            doRender();
        }

        private void doRender(){
            while(AudiotrackRuning){
                try{
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    int outputBufferIndex = mAudioDecorder.dequeueOutputBuffer(info,0);
//                    Log.d(TAG,"outputBufferIndex = " + outputBufferIndex);
                    if (outputBufferIndex > 0){
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            Log.i("TAG", "audio encoder: codec config buffer");
                            mAudioDecorder.releaseOutputBuffer(outputBufferIndex, false);
                            continue;
                        }

                        ByteBuffer outBuffer = mAudioDecorder.getOutputBuffer(outputBufferIndex);
                        byte[] outData = new byte[info.size];
                        outBuffer.get(outData);
                        outBuffer.clear();
                        mAudioDecorder.releaseOutputBuffer(outputBufferIndex,false);
                        mAudioPlayer.playAudioTrack(outData,0,info.size);
                        Log.d(TAG,"mAudioPlayer playAudioTrack " +info.size);
                    }
                }catch (Exception e){
                    Log.d(TAG,"Exception " + e.toString());
                }

            }
            try{

                if (mAudioPlayer != null){
                    mAudioPlayer.stop();
                    mAudioPlayer.release();
                    mAudioPlayer = null;
                    Log.d(TAG,"mAudioPlayer stop");
                }

            }catch (Exception e){
                Log.d(TAG, "stop EXCEPTION " + e.toString());
            }
        }
    }

    class AudioDecodeThread implements Runnable {

        @Override
        public void run() {
            while(AudioDecodeRuning){
                decode();
            }
            try{
                if (mAudioDecorder != null){
                    mAudioDecorder.stop();
                    mAudioDecorder = null;
                    Log.d(TAG,"mAudioDecorder stop");
                }
            }catch (Exception e){
                Log.d(TAG, "Decoder stop EXCEPTION " + e.toString());
            }

        }

        private void decode(){


            long timeoutUs = 10000;
            byte[] streamBuffer = null;

            //本地文件
            if (isTestLocalFile){

                try {
                    streamBuffer = getBytes(mInputStream);
                } catch (Exception e) {
                    Log.d(TAG,"getBytes Exception " + e.toString());
                    e.printStackTrace();
                }
            }
            int bytes_cnt = 0;
            while (AudioDecodeRuning) {

                if (!isTestLocalFile){
                    if (mAudioQueue.size()<=0) {
                        try {
                            Thread.sleep(10);
                        } catch (Exception e) {
                            Log.d(TAG,"getBytes Exception " + e.toString());
                            e.printStackTrace();
                        }
                        continue;
                    }
                    Log.d(TAG,"get data from web");
                    streamBuffer = mAudioQueue.poll();
                }
                Log.d(TAG,"decode streamBuffer " + streamBuffer.length);
                bytes_cnt = streamBuffer.length;
                if (bytes_cnt <= 0) {
                    isDataReady = false;
                    break;
                }else{
                    isDataReady = true;
                }

                int startIndex = 0;
                int remaining = bytes_cnt;

//                while(isDataReady){
                    if (remaining == 0 || startIndex >= remaining) {
                        isDataReady = false;
                        break;
                    }

                    int nextFrameStart = 0;
                    if (isTestLocalFile){
                        nextFrameStart = findHead(streamBuffer, startIndex + 2, remaining);
                    }else {
                        nextFrameStart = findHead(streamBuffer, startIndex, remaining);
                    }
                    Log.d(TAG, "nextFrameStart = " + nextFrameStart);

                    if (nextFrameStart == -1) {
                        nextFrameStart = remaining;
                        isDataReady = false;
                        break;
                    }

                    try{

                        int inIndex = mAudioDecorder.dequeueInputBuffer(timeoutUs);
                        Log.d(TAG,"inIndex = " + inIndex);

                        if (inIndex >= 0) {
                            ByteBuffer byteBuffer = mAudioDecorder.getInputBuffer(inIndex);
                            byteBuffer.clear();
                            if (isTestLocalFile){
                                byteBuffer.put(streamBuffer, startIndex, nextFrameStart - startIndex);
                                mAudioDecorder.queueInputBuffer(inIndex, 0, nextFrameStart - startIndex, 0, 0);
                                startIndex = nextFrameStart;
//                                continue;
                            }else {
                                Log.d(TAG,"WEB DATA decoder ");
                              //  byte[] copy = new byte[streamBuffer.length];
                                byteBuffer.put(streamBuffer, 0 , streamBuffer.length);
                                mAudioDecorder.queueInputBuffer(inIndex, 0, streamBuffer.length  , 0, 0);
//                              //  break;
                            }
                        }

                    }catch (Exception e){
                        Log.d(TAG,"decoder Exception " + e.toString());
                    }
//                }
            }

        }

        public byte[] getBytes(InputStream is) throws IOException {
            int len;
            int size = 1024;
            byte[] buf;
            if (is instanceof ByteArrayInputStream) {
                size = is.available();
                buf = new byte[size];
                len = is.read(buf, 0, size);
            } else {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                buf = new byte[size];
                while ((len = is.read(buf, 0, size)) != -1)
                    bos.write(buf, 0, len);
                buf = bos.toByteArray();
            }
            Log.d(TAG, "bbbb");
            return buf;
        }


    }

    public void stop(){
        try{
            if (mAudioDecorder != null){
                mAudioDecorder.stop();
                mAudioDecorder = null;
                Log.d(TAG,"mAudioDecorder stop");
            }

            if (mAudioPlayer != null){
                mAudioPlayer.stop();
                mAudioPlayer.release();
                mAudioPlayer = null;
                Log.d(TAG,"mAudioPlayer stop");
            }

        }catch (Exception e){
            Log.d(TAG, "stop EXCEPTION " + e.toString());
        }

    }

    private void prepare(){
        Log.d(TAG,"filePath " + filePath);
        File tmpFile = new File(filePath);
        if (tmpFile.exists()){
            try {
                mInputStream = new DataInputStream(new FileInputStream(tmpFile));

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 判断aac帧头
     */
    private boolean isHead(byte[] data, int offset) {
        boolean result = false;
        if (data[offset] == (byte) 0xFF && data[offset + 1] == (byte) 0xF9
                //&& data[offset + 3] == (byte) 0x80
                ) {
            result = true;
        }
        return result;
    }

    private int findHead(byte[] data, int startIndex, int max) {
        int i;
        for (i = startIndex; i <= max - 3; i++) {
            //发现帧头
            if (isHead(data, i))
                break;
        }
        //检测到最大值，未发现帧头
        if (i == max) {
            i = -1;
        }
        return i;
    }



}
