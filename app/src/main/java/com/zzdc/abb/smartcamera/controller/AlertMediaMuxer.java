package com.zzdc.abb.smartcamera.controller;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.zzdc.abb.smartcamera.FaceFeature.AlertVideoData;
import com.zzdc.abb.smartcamera.FaceFeature.AlertVideoData_Table;
import com.zzdc.abb.smartcamera.FaceFeature.FaceConfig;
import com.zzdc.abb.smartcamera.FaceFeature.Utils;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

public class AlertMediaMuxer implements AudioEncoder.AudioEncoderListener, VideoEncoder.VideoEncoderListener {

    private final static String TAG = "qxj";
    private boolean mMuxering;
    private MediaMuxer mMediaMuxer;
    //缓冲传输过来的数据
    private LinkedBlockingQueue<EncoderBuffer> mMuxerDatas = new LinkedBlockingQueue<>();
    private BufferPool<EncoderBuffer> mEncodBuf = new BufferPool<>(EncoderBuffer.class, 3);
    private static final int TRACK_VIDEO = 0;
    private static final int TRACK_AUDIO = 1;
    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;
    private MediaFormat AUDIO_FORMAT = null;
    private MediaFormat VIDEO_FORMAT = null;

    private long mLastAudioFrameTimestamp = 0;
    private long mLastVideoFrameTimestamp = 0;
    private boolean isPPSAdded = false;

    private static long start;
    private static long end;

    public static boolean AlertRecordRunning = false;
    private static String fileTitle; //保存视频和图片名字
    private static long startTime; //开始录制时间
    private static String tmpMediaFilePath; //保存视频路径

    private AlertMediaMuxer() {}
    private static class AlertMediaMuxerHolder {
        private static final AlertMediaMuxer INSTANCE = new AlertMediaMuxer();
    }
    public static final AlertMediaMuxer getInstance() {
        return AlertMediaMuxerHolder.INSTANCE;
    }


    private Thread mWorkThread = new Thread("AlertMediaMuxer-thread") {
        @Override
        public void run() {
            Log.d(TAG, "AlertMediaMuxer thread start.");
            mMediaMuxer.start();
            while (mMuxering) {
                EncoderBuffer data = null;
                try {
                    data = mMuxerDatas.take();
                    if (data.getTrack() == TRACK_VIDEO) {
                        mMediaMuxer.writeSampleData(mVideoTrackIndex, data.getByteBuffer(), data.getBufferInfo());
                    } else if (data.getTrack() == TRACK_AUDIO) {
                        mMediaMuxer.writeSampleData(mAudioTrackIndex, data.getByteBuffer(), data.getBufferInfo());
                    } else {
                        Log.w(TAG, "Find unknow track. " + data.getTrack());
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Mux video an audio with exception, ", e);
                } finally {
                    if (data != null) {
                        data.decreaseRef();
                    }
                }
            }
            try {
                mMediaMuxer.stop();
                mMediaMuxer.release();

            } catch (Exception e) {
                Log.e(TAG,"Stop MediaMuxer failed!!! : ",e);
            }
            Log.d(TAG, "AlertMediaMuxer muxer thread stop.");
        }
    };

    public boolean startMediaMuxer() {

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT ==  null)){
            AUDIO_FORMAT = AudioEncoder.AUDIO_FORMAT;
            VIDEO_FORMAT  = VideoEncoder.VIDEO_FORMAT;
        }

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT ==  null)){
            Log.e(TAG,"AUDIO_FORMAT = " + AUDIO_FORMAT + ", VIDEO_FORMAT = " + VIDEO_FORMAT);
            return false;
        } else {
            tmpMediaFilePath = generateVideoFileName();
            try {
                Log.d(TAG, "Create AlertMediaMuxer start");
                mMediaMuxer = new MediaMuxer(tmpMediaFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }catch (Exception e){
                Log.e(TAG, "Create AlertMediaMuxer with exception, ", e);
                return false;
            }
        }

        mAudioTrackIndex = mMediaMuxer.addTrack(AUDIO_FORMAT);
        mVideoTrackIndex = mMediaMuxer.addTrack(VIDEO_FORMAT);

        mMuxerDatas.clear();
        AudioEncoder.getInstance().registerEncoderListener(this);
        VideoEncoder.getInstance().registerEncoderListener(this);
        mMuxering = true;
        mWorkThread.start();
        AlertRecordRunning = true;
        FaceConfig.startImage = true;
        return true;
    }

    public void stopMediaMuxer() {
        end = System.currentTimeMillis();
        Log.d(TAG, "Stop media AlertMediaMuxer");
        mMuxering = false;
        if (mWorkThread != null) {
            mWorkThread.interrupt();
        }
        saveStartEndTime(start, end, tmpMediaFilePath);

        saveImagePath(tmpMediaFilePath, FaceConfig.imagePath);
        AudioEncoder.getInstance().unRegisterEncoderListener(this);
        VideoEncoder.getInstance().unRegisterEncoderListener(this);
        AlertRecordRunning = false;
    }

    @Override
    public void onAudioEncoded(EncoderBuffer buf) {
        if(!mMuxering)
            return;

        ByteBuffer bufferSrc = buf.getByteBuffer();
        MediaCodec.BufferInfo infoSrc = buf.getBufferInfo();
        if (infoSrc.presentationTimeUs < mLastAudioFrameTimestamp) {
            return;
        } else {
            mLastAudioFrameTimestamp = infoSrc.presentationTimeUs;
        }

        if (!isPPSAdded)
            return;

        EncoderBuffer encoderBuffer = mEncodBuf.getBuf(bufferSrc.remaining());
        encoderBuffer.put(bufferSrc, infoSrc);
        encoderBuffer.setTrack(TRACK_AUDIO);
        mMuxerDatas.offer(encoderBuffer);
    }

    @Override
    public void onAudioFormatChanged(MediaFormat format) {
        Log.d(TAG, "Audio format changed. " + format);
        AUDIO_FORMAT = format;
    }

    @Override
    public void onVideoEncoded(EncoderBuffer buf) {
        if (!mMuxering)
            return;

        ByteBuffer bufferSrc = buf.getByteBuffer();
        MediaCodec.BufferInfo infoSrc = buf.getBufferInfo();
        if (infoSrc.presentationTimeUs < mLastVideoFrameTimestamp) {
            return;
        } else {
            mLastVideoFrameTimestamp = infoSrc.presentationTimeUs;
        }

        int type = bufferSrc.get(4) & 0x1F;
        if (type == 5 && !isPPSAdded) {
            VideoEncoder.PPS_BUFFER_INFO.presentationTimeUs = buf.getBufferInfo().presentationTimeUs;
            EncoderBuffer tmpPPSBuffer = mEncodBuf.getBuf(VideoEncoder.PPS_BUFFER_INFO.size);
            tmpPPSBuffer.put(VideoEncoder.PPS_BUFFER, VideoEncoder.PPS_BUFFER_INFO);
            tmpPPSBuffer.setTrack(TRACK_VIDEO);
            mLastVideoFrameTimestamp = buf.getBufferInfo().presentationTimeUs;
            mMuxerDatas.offer(tmpPPSBuffer);
            isPPSAdded = true;
        }

        if (!isPPSAdded)
            return;
        EncoderBuffer encoderBuffer = mEncodBuf.getBuf(bufferSrc.remaining());
        encoderBuffer.put(bufferSrc, infoSrc);
        encoderBuffer.setTrack(TRACK_VIDEO);
        mMuxerDatas.offer(encoderBuffer);
    }

    @Override
    public void onVideoFormatChanged(MediaFormat format) {
        Log.d(TAG, "Video format changed. " + format);
        VIDEO_FORMAT = format;
    }

    private String generateVideoFileName() {
        String tmpDir = Utils.getSDPath("ALERT");
        startTime = System.currentTimeMillis();
        fileTitle = createName(startTime);
        FaceConfig.imageTitle = fileTitle;
        start = startTime;
        String tmpFileName = fileTitle + ".mp4";

        return tmpDir + '/' + tmpFileName;
    }


    private String createName(long aDate) {
        Date tmpDate = new Date(aDate);
        SimpleDateFormat tmpDateFormat = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss");
        return tmpDateFormat.format(tmpDate);
    }


    private void saveStartEndTime(long startTime, long endTime, String filePath) {
        String start = Utils.timeNow(startTime);
        String end = Utils.timeNow(endTime);
        AlertVideoData videoData = SQLite.select().from(AlertVideoData.class)
                .where(AlertVideoData_Table.startTime.eq(startTime))
                .querySingle();
        if (videoData != null) {
            SQLite.update(AlertVideoData.class)
                    .set(AlertVideoData_Table.start.eq(start), AlertVideoData_Table.startTime.eq(startTime)
                            , AlertVideoData_Table.end.eq(end), AlertVideoData_Table.endTime.eq(endTime),
                            AlertVideoData_Table.filePath.eq(filePath))
                    .where(AlertVideoData_Table.id.is(videoData.id))
                    .execute();
        } else {
            AlertVideoData alertVideoData = new AlertVideoData();
            alertVideoData.startTime = startTime;
            alertVideoData.start = start;
            alertVideoData.endTime = endTime;
            alertVideoData.end = end;
            alertVideoData.filePath = filePath;
            alertVideoData.save();
        }
    }

    private void saveImagePath(String filePath, String imagePath) {
        AlertVideoData videoData = SQLite.select().from(AlertVideoData.class)
                .where(AlertVideoData_Table.filePath.eq(filePath))
                .querySingle();
        if (videoData != null) {
            SQLite.update(AlertVideoData.class)
                    .set(AlertVideoData_Table.imagePath.eq(imagePath))
                    .where(AlertVideoData_Table.id.is(videoData.id))
                    .execute();
        } else {
            AlertVideoData alertVideoData = new AlertVideoData();
            alertVideoData.imagePath = imagePath;
            alertVideoData.filePath = filePath;
            alertVideoData.save();
        }
    }

}
