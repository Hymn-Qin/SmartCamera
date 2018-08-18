package com.zzdc.abb.smartcamera.controller;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.MediaCodec;
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

    private static String start;
    private static String end;

    public static boolean AlertRecordRunning = false;
    private static String fileTitle; //保存视频和图片名字
    private static long startTime; //开始录制时间
    private static String tmpMediaFilePath; //保存视频路径
    private static String imagePath; //保存视频路径

    private static boolean startImage = false;

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
                        if (startImage) {
                            createImage(imagePath, data.getByteBuffer().array());
                        }
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
            imagePath = getImagePath();
            startImage = true;
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
        return true;
    }

    public void stopMediaMuxer() {
        end = Utils.timeNow();
        Log.d(TAG, "Stop media AlertMediaMuxer");
        mMuxering = false;
        if (mWorkThread != null) {
            mWorkThread.interrupt();
        }
        saveStartEndTime(start, end, tmpMediaFilePath);

        saveImagePath(tmpMediaFilePath, imagePath);
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
        String tmpPath = "";
        startTime = System.currentTimeMillis();
        fileTitle = createName(startTime);
        start = Utils.timeNow(startTime);
        String tmpFileName = fileTitle + ".mp4";
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT";
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), "ALERT");
            if (!mkDir.exists()) {
                mkDir.mkdirs();   //目录不存在，则创建
            }
            tmpPath = tmpDir + '/' + tmpFileName;
            Log.d(TAG, "tmpPath " + tmpPath);
        } else {
            Log.d(TAG, "sd卡不存在");
        }

        return tmpPath;
    }
    private String getImagePath () {
        String tmpPath = "";
        String tmpFileName = fileTitle + ".jpg";
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT";
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), "ALERT");
            if (!mkDir.exists()) {
                mkDir.mkdirs();   //目录不存在，则创建
            }
            tmpPath = tmpDir + '/' + tmpFileName;
            Log.d(TAG, "tmpPath " + tmpPath);
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
    private static Bitmap getBitmap(long start, String filePath) {
        Log.d(TAG, " 开始生成第一帧图片 " + filePath);
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(filePath);
        String w = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String h = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

        Bitmap bitmap = null;
        for (long i = start; i < 30000; i += 1000) {
            bitmap = metadataRetriever.getFrameAtTime(i * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bitmap != null) {
                break;
            }
        }

        metadataRetriever.release();
        return bitmap;
    }

    private void createImage(String imagePath, byte[] data) {
        try {
            startImage = false;
            File file = new File(imagePath);
            FileOutputStream outputStream = new FileOutputStream(file);

            YuvImage image = new YuvImage(data, ImageFormat.NV21, 1080, 960, null);
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    /**
     * 把第一帧保存成图片 在SD卡中
     * @param bitmap
     */
    private String saveBitmap(String name, Bitmap bitmap) {
        Log.d(TAG, " 开始保存第一帧图片 ");
        if (bitmap == null) {
            Log.d(TAG, " 保存第一帧图片失败 bitmap 为空");
            return null;
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
                String imagePath = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT" + "/" + name + ".jpg";
                Log.d(TAG, " 保存第一帧图片 name -- " + imagePath);
                File file = new File(imagePath);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }

                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
                fos.close();
                return imagePath;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void saveStartEndTime(String start, String end, String filePath) {
        AlertVideoData videoData = SQLite.select().from(AlertVideoData.class)
                .where(AlertVideoData_Table.start.eq(start))
                .querySingle();
        if (videoData != null) {
            SQLite.update(AlertVideoData.class)
                    .set(AlertVideoData_Table.start.eq(start), AlertVideoData_Table.end.eq(end), AlertVideoData_Table.filePath.eq(filePath))
                    .where(AlertVideoData_Table.id.is(videoData.id))
                    .execute();
        } else {
            AlertVideoData alertVideoData = new AlertVideoData();
            alertVideoData.start = start;
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
