package com.zzdc.abb.smartcamera.controller;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;


import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

public class AvMediaMuxer implements AudioEncoder.AudioEncoderListener, VideoEncoder.VideoEncoderListener{

    private final static String TAG = AvMediaMuxer.class.getSimpleName();
    public static final int TRACK_VIDEO = 0;
    public static final int TRACK_AUDIO = 1;
    private final Object lock = new Object();
    private MediaMuxer mediaMuxer;
    //缓冲传输过来的数据
    private LinkedBlockingQueue<EncoderBuffer> muxerDatas = new LinkedBlockingQueue<>();
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean isVideoAdd;
    private boolean isAudioAdd;
    private Thread workThread;
    private boolean isMediaMuxerStart;
    private volatile boolean loop;

    public static MediaFormat AUDIO_FORMAT;
    public static MediaFormat VIDEO_FORMAT;
    private String mMediaFile;

    private static final AvMediaMuxer mInstance = new AvMediaMuxer();

    private AvMediaMuxer() {}

    public static AvMediaMuxer newInstance() {
        return mInstance;
    }

    public void initMediaMuxer() {
        //create file for local test
        mMediaFile = generateVideoFileName();
        if (loop) {
            throw new RuntimeException("====MediaMuxer线程已经启动===");
        }
        try {
            Log.d(TAG, "====创建媒体混合器 start...");
            mediaMuxer = new MediaMuxer(mMediaFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Log.d(TAG, "====创建媒体混合器 done...");
        }catch (Exception e){
            e.printStackTrace();
            Log.e(TAG, "====创建媒体混合器 error: "+e.toString());
        }

        workThread = new Thread("mediaMuxer-thread") {
            @Override
            public void run() {
                //混合器未开启
                synchronized (lock) {
                    try {
                        Log.d(TAG, "====媒体混合器等待开启...");
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                while (loop && !Thread.interrupted()) {
                    EncoderBuffer data = null;
                    try {
                        data = muxerDatas.take();
                        int track = -1;
                        if (data.getTrack() == TRACK_VIDEO) {
                            track = videoTrackIndex;
                        } else if(data.getTrack() == TRACK_AUDIO){
                            track = audioTrackIndex;
                        }
                        mediaMuxer.writeSampleData(track, data.getByteBuffer(), data.getBufferInfo());

                    } catch (Exception e) {
                        LogTool.w(TAG, "Mux video an audio failed", e);
                    } finally {
                        if (data != null) {
                            data.decreaseRef();
                        }
                    }
                }
                muxerDatas.clear();
                stopMediaMuxer();
                Log.d(TAG, "=====媒体混合器退出...");
            }
        };

        loop = true;
        workThread.start();
    }

    public void release() {
        loop = false;
        if (workThread != null) {
            workThread.interrupt();
        }
    }

    public boolean startMediaMuxer() {
        if (isMediaMuxerStart)
            return false;
        if(VideoEncoder.VIDEO_FORMAT == null && AudioEncoder.AUDIO_FORMAT == null){
            Log.d(TAG,"VideoEncoder.VIDEO_FORMAT == null && AudioEncoder.AUDIO_FORMAT == null");
            return false;
        }
        if ((AUDIO_FORMAT == null || VIDEO_FORMAT ==  null)){
            AUDIO_FORMAT = AudioEncoder.AUDIO_FORMAT;
            VIDEO_FORMAT  = VideoEncoder.VIDEO_FORMAT;
        }

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT ==  null)){
            Log.d(TAG,"AUDIO_FORMAT == null || VIDEO_FORMAT ==  null");
            return false;
        }

        audioTrackIndex = mediaMuxer.addTrack(AUDIO_FORMAT);
        isAudioAdd = true;
        videoTrackIndex = mediaMuxer.addTrack(VIDEO_FORMAT);
        isVideoAdd = true;

        synchronized (lock) {
            if (isAudioAdd && isVideoAdd) {
                Log.d(TAG, "====启动媒体混合器=====");
                mediaMuxer.start();
                isMediaMuxerStart = true;
                lock.notify();
            }
        }
        return true;
    }

    public void stopMediaMuxer() {
        if (!isMediaMuxerStart)
            return;
        mediaMuxer.stop();
        mediaMuxer.release();
        isMediaMuxerStart = false;
        isAudioAdd = false;
        isVideoAdd = false;
        Log.d(TAG, "====停止媒体混合器=====");
    }

    @Override
    public void onAudioEncoded(EncoderBuffer buf) {
        buf.increaseRef();
        muxerDatas.offer(buf);
    }

    @Override
    public void onAudioFormatChanged(MediaFormat format) {
        AUDIO_FORMAT = format;
    }

    @Override
    public void onVideoEncoded(EncoderBuffer buf) {
        buf.increaseRef();
        muxerDatas.offer(buf);
    }

    @Override
    public void onVideoFormatChanged(MediaFormat format) {
        VIDEO_FORMAT = format;
    }

    /**
     * 封装需要传输的数据类型
     */
    public static class MuxerData {
        int trackIndex;
        ByteBuffer byteBuf;
        MediaCodec.BufferInfo bufferInfo;

        public MuxerData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo) {
            this.trackIndex = trackIndex;
            this.byteBuf = byteBuf;
            this.bufferInfo = bufferInfo;
        }

    }

    private String generateVideoFileName(){
        String tmpPath ="";
        long tmpTime = System.currentTimeMillis();
        String title = createName(tmpTime);
        String tmpFileName = title + ".mp4";
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()){
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + "DCIM";
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), "DCIM");
            if (!mkDir.exists())
            {
                mkDir.mkdirs();   //目录不存在，则创建
            }
            tmpPath = tmpDir + '/' + tmpFileName;
            LogTool.d(TAG, "tmpPath " + tmpPath);
        } else {
            Log.d(TAG,"sd卡不存在");
//            tmpPath = Constant.DIRCTORY + '/' + tmpFileName;
        }

        return tmpPath ;
    }

    private String createName(long aDate){
        Date tmpDate = new Date(aDate);
        SimpleDateFormat tmpDateFormat = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss");
        return  tmpDateFormat.format(tmpDate);
    }

}
