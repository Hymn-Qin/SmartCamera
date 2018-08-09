package com.zzdc.abb.smartcamera.controller;

import android.media.MediaFormat;
import android.util.Log;

import com.tutk.IOTC.AVFrame;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class AvMediaTransfer implements AudioEncoder.AudioEncoderListener, VideoEncoder.VideoEncoderListener ,MP4Extrator.ExtratorDataListenner{
    private static final String TAG = AvMediaTransfer.class.getSimpleName();
    private static final boolean DEBUG = false;

    private LinkedBlockingQueue<TutkFrame> mVideoQueue = new LinkedBlockingQueue<>();
    private LinkedBlockingQueue<TutkFrame> mAudioQueue = new LinkedBlockingQueue<>();
    private BufferPool<TutkFrame> mVideoBufPool = new BufferPool<>(TutkFrame.class, 3);
    private BufferPool<TutkFrame> mAudioBufPool = new BufferPool<>(TutkFrame.class, 3);

    private Thread mVideoDataSendThread;
    private Thread mAudioDataSendThread;


    private CopyOnWriteArrayList<AvTransferLister> mAvTransferListers = new CopyOnWriteArrayList<>();

    public void registerAvTransferListener(AvTransferLister listener) {
        LogTool.d(TAG,"transferlisterners count:"+mAvTransferListers.size());

        if (!mAvTransferListers.contains(listener)) {
            LogTool.d(TAG,"transferlisterners count: add");
            mAvTransferListers.add(listener);
        }
    }

    public void unRegisterAvTransferListener(AvTransferLister listener) {
        LogTool.d(TAG,"transferlisterners count: remove");
        mAvTransferListers.remove(listener);
    }

    @Override
    public void onExtratorVideoDataReady(TutkFrame aFrame) {
        aFrame.increaseRef();
        mVideoQueue.offer(aFrame);
    }

    @Override
    public void onExtratorAudioDataReady(TutkFrame aFrame) {
        aFrame.increaseRef();
        mAudioQueue.offer(aFrame);
    }

    public interface AvTransferLister {
        public void sendVideoTutkFrame(TutkFrame tutkFrame);
        public void sendAudioTutkFrame(TutkFrame tutkFrame);
    }


    public void startAvMediaTransfer(){
        mAudioQueue.clear();
        mVideoQueue.clear();
        mVideoDataSendThread = new Thread(new VideoSendThread());
        mVideoDataSendThread.start();
        mAudioDataSendThread = new Thread(new AudioSendThread());
        mAudioBufPool.setDebug(DEBUG);
        mVideoBufPool.setDebug(DEBUG);
        mAudioDataSendThread.start();
    }

    public void stopAvMediaTransfer() {
        mAudioDataSendThread.interrupt();
        mVideoDataSendThread.interrupt();
        mVideoQueue.clear();
        mAudioQueue.clear();
    }

    @Override
    public void onAudioEncoded(EncoderBuffer buf) {
        int dataLen = buf.getBufferInfo().size + 7;
        TutkFrame frame = mAudioBufPool.getBuf(dataLen);
        frame.setDataLen(dataLen);
        addADTStoPacket(frame.getData(), dataLen);
        buf.getByteBuffer().get(frame.getData(),7, buf.getBufferInfo().size);
        buf.getByteBuffer().position(0);
        frame.getFrameInfo().codec_id = AVFrame.MEDIA_CODEC_AUDIO_MP3;
//        frame.getFrameInfo().timestamp = "1970-01-01 08:00:00";

        try {
            mAudioQueue.put(frame);
        } catch (InterruptedException e) {
            LogTool.w(TAG,"onAudioEncoded ", e);
        }
    }

    @Override
    public void onAudioFormatChanged(MediaFormat format) {

    }

    @Override
    public void onVideoEncoded(EncoderBuffer buf) {
        ByteBuffer tmpOutBuf = buf.getByteBuffer();

        TutkFrame frame = null;
        int type = tmpOutBuf.get(4) & 0x1F;
        if (type == 5 && Constant.PPS != null && Constant.PPS.length > 0) {
            int dataLen = Constant.PPS.length + buf.getBufferInfo().size;
            frame = mVideoBufPool.getBuf(dataLen);
            frame.setDataLen(dataLen);
            System.arraycopy(Constant.PPS, 0, frame.getData(), 0, Constant.PPS.length);
            try {
                tmpOutBuf.get(frame.getData(), Constant.PPS.length, buf.getBufferInfo().size);
            } catch (Exception e) {
                Log.e(TAG, "Copy PPS and data with exception.", e);
            }
//            frame.getFrameInfo().flags = AVFrame.IPC_FRAME_FLAG_IFRAME;
        } else {
            int dataLen = buf.getBufferInfo().size;
            frame = mVideoBufPool.getBuf(dataLen);
            frame.setDataLen(dataLen);
            try {
                tmpOutBuf.get(frame.getData(), 0, buf.getBufferInfo().size);
            } catch (Exception e) {
                Log.e(TAG, "Copy data with exception.", e);
            }
        }
        tmpOutBuf.position(0);

        frame.getFrameInfo().codec_id = AVFrame.MEDIA_CODEC_VIDEO_H264;
//        frame.getFrameInfo().mType = "H264";
//        frame.getFrameInfo().timestamp = "1970-01-01 08:00:00";

        try {
            mVideoQueue.put(frame);
        } catch (Exception e) {
            Log.e(TAG, "onVideoEncoded with exception.", e);
        }
    }

    @Override
    public void onVideoFormatChanged(MediaFormat format) {

    }

    class VideoSendThread implements Runnable {

        @Override
        public void run() {
            while (true) {
                TutkFrame tmpFrame = null;
                try {
                    tmpFrame = mVideoQueue.take();
                    if (tmpFrame != null) {
                        synchronized (mAvTransferListers) {
                            for (AvTransferLister avTransferLister : mAvTransferListers) {
                                avTransferLister.sendVideoTutkFrame(tmpFrame);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "VideoSendThread exception. ", e);
                } finally {
                    if (tmpFrame != null) {
                        tmpFrame.decreaseRef();
                    }
                }
            }
        }
    }

    class AudioSendThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                TutkFrame tmpFrame = null;
                try {
                    tmpFrame = mAudioQueue.take();
                    if (tmpFrame != null) {
                        synchronized (mAvTransferListers) {
                            debug("mAvTransferListers .size = " + mAvTransferListers.size());
                            for (AvTransferLister avTransferLister : mAvTransferListers) {
                                avTransferLister.sendAudioTutkFrame(tmpFrame);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "AudioSendThread exception ", e);
                } finally {
                    if (tmpFrame != null) {
                        tmpFrame.decreaseRef();
                    }
                }
            }
        }
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 8; // 16 KHz
        int chanCfg = 2; // CPE

        /*int avpriv_mpeg4audio_sample_rates[] = {
            96000,   // 0
            88200,   // 1
            64000,   // 2
            48000,   // 3
            44100,   // 4
            32000,   // 5
            24000,   // 6
            22050,   // 7
            16000,   // 8
            12000,   // 9
            11025,   // 10
            8000,    // 11
            7350     // 12
        };
        channel_configuration: 表示声道数chanCfg
        0: Defined in AOT Specifc Config
        1: 1 channel: front-center
        2: 2 channels: front-left, front-right
        3: 3 channels: front-center, front-left, front-right
        4: 4 channels: front-center, front-left, front-right, back-center
        5: 5 channels: front-center, front-left, front-right, back-left, back-right
        6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
        7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
        8-15: Reserved
        */

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
        Log.d(TAG,"packetLen = " + packetLen + " packet " + packet.length);

    }

    private void debug(String msg) {
        if (DEBUG || MainActivity.TRANSFER_DEBUG) LogTool.d(TAG, msg);
    }
}
