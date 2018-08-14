package com.zzdc.abb.smartcamera.controller;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import com.zzdc.abb.smartcamera.FaceFeature.FaceConfig;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
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

    private Thread mWorkThread = new Thread("MediaMuxer-thread") {
        @Override
        public void run() {
            LogTool.d(TAG, "Media muxer thread start.");
            mMediaMuxer.start();
            while (mMuxering) {
                EncoderBuffer data = null;
                try {
                    if (mMuxerDatas == null) {
                        continue;
                    }
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
                LogTool.e(TAG,"Stop MediaMuxer failed!!! : ",e);
            }
            LogTool.d(TAG, "Media muxer thread stop.");
        }
    };

    public boolean startMediaMuxer() {

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT ==  null)){
            AUDIO_FORMAT = AudioEncoder.AUDIO_FORMAT;
            VIDEO_FORMAT  = VideoEncoder.VIDEO_FORMAT;
        }

        if ((AUDIO_FORMAT == null || VIDEO_FORMAT ==  null)){
            LogTool.e(TAG,"AUDIO_FORMAT = " + AUDIO_FORMAT + ", VIDEO_FORMAT = " + VIDEO_FORMAT);
            return false;
        } else {
            FaceConfig.tmpMediaFile = generateVideoFileName();
            try {
                LogTool.d(TAG, "Create MediaMuxer start");
                mMediaMuxer = new MediaMuxer(FaceConfig.tmpMediaFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }catch (Exception e){
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
//        if (mWorkThread != null) {
//            mWorkThread.interrupt();
//        }
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

    private String generateVideoFileName() {
        String tmpPath = "";
        FaceConfig.tmpTime = System.currentTimeMillis();
        FaceConfig.title = createName(FaceConfig.tmpTime);
        FaceConfig.tmpFileName = FaceConfig.title + ".mp4";
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT";
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), "ALERT");
            if (!mkDir.exists()) {
                mkDir.mkdirs();   //目录不存在，则创建
            }
            tmpPath = tmpDir + '/' + FaceConfig.tmpFileName;
            LogTool.d(TAG, "tmpPath " + tmpPath);
        } else {
            Log.d(TAG, "sd卡不存在");
//            tmpPath = Constant.DIRCTORY + '/' + tmpFileName;
        }

        return tmpPath;
    }

    private String createName(long aDate) {
        Date tmpDate = new Date(aDate);
        SimpleDateFormat tmpDateFormat = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss");
        return tmpDateFormat.format(tmpDate);
    }

    /**
     * 提取第一帧图片
     * @return 返回图片
     */
    public static Bitmap getBitmap() {
        Log.d("qxj", " 开始生成第一帧图片 " + FaceConfig.tmpMediaFile);
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(FaceConfig.tmpMediaFile);
        String w = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String h = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

        Bitmap bitmap = null;
        for (long i = FaceConfig.tmpTime; i < 30000; i += 1000) {
            bitmap = metadataRetriever.getFrameAtTime(i * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap != null) {
                break;
            }
        }

        metadataRetriever.release();
        return bitmap;
    }

    /**
     * 把第一帧保存成图片 在SD卡中
     * @param bitmap
     */
    public static void saveBitmap(Bitmap bitmap) {
        Log.d("qxj", " 开始保存第一帧图片 ");
        if (bitmap == null) {
            Log.d("qxj", " 保存第一帧图片失败 bitmap 为空");
            return;
        }
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT";
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), "ALERT");
            if (!mkDir.exists()) {
                mkDir.mkdirs();   //目录不存在，则创建
            }

            try {
                FaceConfig.imagePath = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT" + "/" + FaceConfig.title + ".jpg";
                Log.d("qxj", " 保存第一帧图片 name -- " + FaceConfig.imagePath);
                File file = new File(FaceConfig.imagePath);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }

                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
