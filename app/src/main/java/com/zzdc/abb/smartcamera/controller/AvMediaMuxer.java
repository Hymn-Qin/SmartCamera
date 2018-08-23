package com.zzdc.abb.smartcamera.controller;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.zzdc.abb.smartcamera.FaceFeature.Utils;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

public class AvMediaMuxer implements AudioEncoder.AudioEncoderListener, VideoEncoder.VideoEncoderListener {
    private final static String TAG = AvMediaMuxer.class.getSimpleName();

    public boolean mMuxering;
    private MediaMuxer mMediaMuxer;
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

    private String types;
    private boolean startCreate = false;
    private String imageFile = null;
    private Thread mWorkThread = new Thread("MediaMuxer-thread") {
        @Override
        public void run() {
            LogTool.d(TAG, "Media muxer thread start.");
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
                        LogTool.w(TAG, "Find unknow track. " + data.getTrack());
                    }
                } catch (Exception e) {
                    LogTool.w(TAG, "Mux video an audio with exception, ", e);
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
                LogTool.e(TAG, "Stop MediaMuxer failed!!! : ", e);
            }
            LogTool.d(TAG, "Media muxer thread stop.");
        }
    };

    public AvMediaMuxer(String type) {
        this.types = type;
    }

    public boolean startMediaMuxer() {

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT == null)) {
            AUDIO_FORMAT = AudioEncoder.AUDIO_FORMAT;
            VIDEO_FORMAT = VideoEncoder.VIDEO_FORMAT;
        }

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT == null)) {
            LogTool.e(TAG, "AUDIO_FORMAT = " + AUDIO_FORMAT + ", VIDEO_FORMAT = " + VIDEO_FORMAT);
            return false;
        } else {
            String tmpMediaFile = null;
            switch (types) {
                case "History":
                    tmpMediaFile = MediaStorageManager.getInstance().generateHistoryMediaFileName();
                    break;
                case "Alert":
                    startCreate = true;
                    tmpMediaFile = MediaStorageManager.getInstance().generateWarningMediaFileName();
                    imageFile = MediaStorageManager.getInstance().generateWarningImageFileName(tmpMediaFile);
                    break;
            }

            try {
                LogTool.d(TAG, "Create MediaMuxer start");
                mMediaMuxer = new MediaMuxer(tmpMediaFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (Exception e) {
                LogTool.e(TAG, "Create MediaMuxer with exception, ", e);
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
        return true;
    }

    public void stopMediaMuxer() {
        LogTool.d(TAG, "Stop media muxer");
        mMuxering = false;
        if (mWorkThread != null) {
            mWorkThread.interrupt();
        }
        AudioEncoder.getInstance().unRegisterEncoderListener(this);
        VideoEncoder.getInstance().unRegisterEncoderListener(this);
    }

    @Override
    public void onAudioEncoded(EncoderBuffer buf) {
        if (!mMuxering)
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
        LogTool.d(TAG, "Audio format changed. " + format);
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
//            if (types.equals("Alert") && startCreate && imageFile != null) {
//                startCreate = false;
//                Log.d("qxj", "start create first image");
//                Utils.createImage(imageFile, tmpPPSBuffer.getByteBuffer(), 1920, 1080);
//                imageFile = null;
//            }
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
        LogTool.d(TAG, "Video format changed. " + format);
        VIDEO_FORMAT = format;
    }

}
