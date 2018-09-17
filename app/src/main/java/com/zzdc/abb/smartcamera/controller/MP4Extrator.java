package com.zzdc.abb.smartcamera.controller;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.tutk.IOTC.AVFrame;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CopyOnWriteArrayList;


public class MP4Extrator {
    private static final  String TAG = MP4Extrator.class.getSimpleName();
    private static final boolean DEBUG = true;

    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;
    private boolean mIsWorking = false;
    private boolean isFirstPlayVideo = true;
    private String mWantedFilePath ;
    private String nextFilePath;
    private long mUserGivenTime;
    private long mVideoStartTime;        //视频文件时间
    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private static final int START_FIRST_FILE = 9;
    private static final int START_NEXT_FILE = 10;

    private Thread mExtratorThread;
    private BufferPool<TutkFrame> mVideoBufPool = new BufferPool<>(TutkFrame.class, 3);
    private BufferPool<TutkFrame> mAudioBufPool = new BufferPool<>(TutkFrame.class, 3);
    private CopyOnWriteArrayList<ExtratorDataListenner> mListenrs = new CopyOnWriteArrayList<>();

//    private byte[] PPS = new byte[]{
//                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x67,
//                (byte)0x42, (byte)0x80, (byte)0x1E, (byte)0xDA, (byte)0x02,
//                (byte)0x80, (byte)0xF6, (byte)0x94, (byte)0x82, (byte)0x81,
//                (byte)0x01, (byte)0x03, (byte)0x68, (byte)0x50, (byte)0x9A,
//                (byte)0x80, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
//                (byte)0x68, (byte)0xCE, (byte)0x06, (byte)0xE2};

    public MP4Extrator(String aFilePath, long aUserGivenTime, long aVideoStartTime){
        mWantedFilePath = aFilePath;
        mUserGivenTime = aUserGivenTime;
        mVideoStartTime = aVideoStartTime;
    }

    public void init(){
        LogTool.d(TAG,"mWantedFilePath = " + mWantedFilePath);
        File tmpFile = new File(mWantedFilePath);
        if (!tmpFile.exists() || tmpFile.length() == 0) {
            LogTool.w(TAG,"File not exist");
            mIsWorking = false;
        } else {
            if (null == mVideoExtractor) {
                mVideoExtractor = new MediaExtractor();
            }
            if (null == mAudioExtractor) {
                mAudioExtractor = new MediaExtractor();
            }
            try {
                mAudioExtractor.setDataSource(mWantedFilePath);
                mVideoExtractor.setDataSource(mWantedFilePath);
            } catch (IOException e) {
                LogTool.w(TAG, "Set data source for extractor with exception", e);
            }
            int trackCount = mVideoExtractor.getTrackCount();
            for (int i = 0; i < trackCount; i++){
                MediaFormat tmpFormat = mVideoExtractor.getTrackFormat(i);
                String mineType = tmpFormat.getString(MediaFormat.KEY_MIME);
                if (mineType.startsWith("video/")) {
                    mVideoTrackIndex = i;
                } else if (mineType.startsWith("audio/")) {
                    mAudioTrackIndex = i;
                }
            }

            if (mVideoTrackIndex == -1 || mAudioTrackIndex == -1) {
                LogTool.w(TAG, "Doesn't found audio/video track. video track = " + mVideoTrackIndex + ", audio track = " + mAudioTrackIndex);
            }

            mVideoExtractor.selectTrack(mVideoTrackIndex);
            mAudioExtractor.selectTrack(mAudioTrackIndex);

            LogTool.d(TAG,"MP4Extrator init success!!!");
            if (isFirstPlayVideo) {
                myHandler.sendEmptyMessage(START_FIRST_FILE);
                isFirstPlayVideo = false;
            }
        }
    }

    private Handler myHandler;
    public void start() {
        mExtratorThread = new Thread("Extractor Thread"){
            @Override
            public void run() {
                super.run();

                Looper.prepare();
                myHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        switch (msg.what) {
                            case START_FIRST_FILE:
                                LogTool.d(TAG,"Begin to play history file first time!!!");
                                playingVideo();
                                break;
                            case START_NEXT_FILE:
                                LogTool.d(TAG,"Begin to play next history file start!!!");
                                nextFilePath = MediaStorageManager.getInstance().nextHistoryFile(mWantedFilePath);
                                LogTool.d(TAG,"The next history file = "+nextFilePath);
                                if (null == nextFilePath) {
                                    break;
                                }
                                mWantedFilePath = nextFilePath;
                                mVideoStartTime = MediaStorageManager.getInstance().getHistoryMediaStartTime(mWantedFilePath);
                                mUserGivenTime = mVideoStartTime;
                                init();
                                playingVideo();
                                break;
                            default:
                                break;
                        }
                    }
                };
                Looper.loop();
            }
        };
        mExtratorThread.start();
    }

    private void playingVideo() {
        mIsWorking = true;
        LogTool.d(TAG, "File start time = " + mVideoStartTime + ", user given time = " + mUserGivenTime);
        long firstSampleTime = mVideoExtractor.getSampleTime();
        long offset = Math.max(0, mUserGivenTime - mVideoStartTime);
        long seekTime = Math.max(0,firstSampleTime + offset);
        LogTool.d(TAG,"First sample time = " + firstSampleTime + ", offset = " + offset + ", seek time  = " + seekTime);
        mVideoExtractor.seekTo(seekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        mAudioExtractor.seekTo(seekTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

        long beginExtractTime = System.currentTimeMillis();

        ByteBuffer audioByteBuffer = ByteBuffer.allocate(500 * 1024);
        ByteBuffer videoByteBuffer = ByteBuffer.allocate(500 * 1024);
        while(mIsWorking) {
            try {
                long sampleTime = mVideoExtractor.getSampleTime();
                long time = System.currentTimeMillis();
                long extractDuration = time - beginExtractTime;
                long sampleDuration = (sampleTime - seekTime)/1000;
                if (sampleDuration > extractDuration) {
                    try {
                        Thread.sleep(sampleDuration - extractDuration);
                    } catch (InterruptedException e) {
                        LogTool.w(TAG, "Interrupted, ", e);
                        break;
                    }
                }

                int videoSampleSize = mVideoExtractor.readSampleData(videoByteBuffer, 0);
                if (videoSampleSize < 0) {
                    mIsWorking = false;
                    mVideoExtractor.release();
                    mAudioExtractor.release();
                    mVideoExtractor = null;
                    mAudioExtractor = null;
                    myHandler.sendEmptyMessageDelayed(START_NEXT_FILE, 1000);
                    break;
                }
                long tmpRecodingTime = mVideoStartTime + (sampleTime - firstSampleTime);

                TutkFrame videoFrame = null;
//                    int type = videoByteBuffer.get(4)& 0x1F;
//                    if (type == 7 || type == 8) {
//                        byte[] tmpPPS = new byte[videoSampleSize];
//                        videoByteBuffer.get(tmpPPS, 0, videoSampleSize);
//                        PPS = tmpPPS;
//                        debug("PPS frame, PPS: " + ByteToHexTool.bytesToHex(tmpPPS));
//                    } else if (type == 5) {
//                        if (PPS != null && PPS.length > 0) {
//                            debug("LEON1-Key frame, PPS length = " + PPS.length);
//                            videoFrame = mVideoBufPool.getBuf(PPS.length + videoSampleSize);
//                            videoFrame.setDataLen(PPS.length + videoSampleSize);
//                            System.arraycopy(PPS, 0, videoFrame.getData(), 0, PPS.length);
//                            videoByteBuffer.get(videoFrame.getData(), PPS.length, videoSampleSize);
//                        } else {
//                            debug("LEON1-Key frame, PPS is empty");
//                            videoFrame = mVideoBufPool.getBuf(videoSampleSize);
//                            videoFrame.setDataLen(videoSampleSize);
//                            videoByteBuffer.get(videoFrame.getData(), 0, videoSampleSize);
//                        }
//                    } else {
//                        debug("LEON1-Normal frame");
//                        videoFrame = mVideoBufPool.getBuf(videoSampleSize);
//                        videoFrame.setDataLen(videoSampleSize);
//                        videoByteBuffer.get(videoFrame.getData(), 0, videoSampleSize);
//                    }

                videoFrame = mVideoBufPool.getBuf(videoSampleSize);
                videoFrame.setDataLen(videoSampleSize);
                videoByteBuffer.get(videoFrame.getData(), 0, videoSampleSize);

                videoFrame.getFrameInfo().codec_id = AVFrame.MEDIA_CODEC_VIDEO_H264;
                videoFrame.getFrameInfo().timestamp = tmpRecodingTime;
                for (int j= 0;j< mListenrs.size();j++){
                    mListenrs.get(j).onExtratorVideoDataReady(videoFrame);
                }
                videoFrame.decreaseRef();
                videoByteBuffer.clear();

                int audioSampleSize = mAudioExtractor.readSampleData(audioByteBuffer, 0);
                if(audioSampleSize > 0){
                    int dataLen = audioSampleSize + ADT_SIZE;
                    TutkFrame audioFrame = mAudioBufPool.getBuf(dataLen);
                    audioFrame.setDataLen(dataLen);

                    addADTStoPacket(audioFrame.getData(), dataLen);
                    audioByteBuffer.get(audioFrame.getData(), ADT_SIZE, audioSampleSize);
                    audioFrame.getFrameInfo().codec_id = AVFrame.MEDIA_CODEC_AUDIO_MP3;
                    audioFrame.getFrameInfo().timestamp = tmpRecodingTime;

                    for (int j= 0;j< mListenrs.size();j++){
                        mListenrs.get(j).onExtratorAudioDataReady(audioFrame);
                    }
                    audioFrame.decreaseRef();
                }
                audioByteBuffer.clear();

                mVideoExtractor.advance();
                mAudioExtractor.advance();
            } catch (Exception e) {
                LogTool.e(TAG,"Extractor history video with exception,please attention!!! ",e);
            }
        }
    }

    public void stop(){
        mIsWorking = false;
        mVideoExtractor.release();
        mAudioExtractor.release();
        mVideoExtractor = null;
        mAudioExtractor = null;
        mExtratorThread.interrupt();
    }

    private static final int ADT_SIZE = 7;
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 8; // 16 KHz
        int chanCfg = 2; // CPE

        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
        Log.d(TAG,"packetLen = " + packetLen + " packet " + packet.length);
    }

    public interface ExtratorDataListenner{
        void onExtratorVideoDataReady(TutkFrame aFrame);
        void onExtratorAudioDataReady(TutkFrame aFrame);
    }

    public void registerExtratorListener(ExtratorDataListenner aListenner){
        if (!mListenrs.contains(aListenner)){
            mListenrs.add(aListenner);
        }
    }

    public void unRegisterExtratorListener(ExtratorDataListenner aListenner){
        mListenrs.remove(aListenner);
    }

    private void debug(String msg) {
        if (DEBUG) {
            LogTool.d(TAG, msg);
        }
    }
}
