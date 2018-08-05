package com.zzdc.abb.smartcamera.controller;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.tutk.IOTC.AVFrame;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.info.FrameInfo;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.util.WriteToFileTool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class HistoryManager {

    private static final String TAG = HistoryManager.class.getSimpleName();
    private static final HistoryManager mInstance = new HistoryManager();
    private WriteToFileTool mTool;
    private HistoryManager(){

    }

    public static final HistoryManager getInstance(){
        return mInstance;
    }

    public void sendAudioVideo(){
        Log.d(TAG,"sendAudioVideo");
        mTool = new WriteToFileTool();
        mTool.prepare();
        File file = new File(Constant.DIRCTORY + '/'+"test.mp4");
        if (file == null || ! file.exists() || file.length() == 0) {
            Log.d(TAG,"文件不存在");
            return;
        }else {
            MediaExtractor videoExtractor = new MediaExtractor();
            MediaExtractor audioExtractor = new MediaExtractor();
            int videoIndex = -1;
            //源文件
            try {
                videoExtractor.setDataSource(Constant.DIRCTORY + '/' + "test.mp4");
                audioExtractor.setDataSource(Constant.DIRCTORY + '/' + "test.mp4");
                //信道总数
                int videoTrackCount = videoExtractor.getTrackCount();
                int audioTrackCount = audioExtractor.getTrackCount();

                int audioTrackIndex = -1;
                int videoTrackIndex = -1;

                for (int i = 0; i < videoTrackCount; i++) {
                    MediaFormat trackFormat = videoExtractor.getTrackFormat(i);
                    String mineType = trackFormat.getString(MediaFormat.KEY_MIME);
                    //视频信道
                    if (mineType.startsWith("video/")) {
                        videoTrackIndex = i;
                    }
                }
                for (int i = 0; i < audioTrackCount; i++) {
                    MediaFormat trackFormat = audioExtractor.getTrackFormat(i);
                    String mineType = trackFormat.getString(MediaFormat.KEY_MIME);
                    //音频信道
                    if (mineType.startsWith("audio/")) {
                        audioTrackIndex = i;
                    }
                }
                ByteBuffer audioByteBuffer = ByteBuffer.allocate(500 * 1024);
                ByteBuffer videoByteBuffer = ByteBuffer.allocate(500 * 1024);
                Log.d(TAG,"sendAudioVideo 0");
                while (true) {
                    //切换到视频信道
                    videoExtractor.selectTrack(videoTrackIndex);
                    //切换到音频信道
                    audioExtractor.selectTrack(audioTrackIndex);
                    int i = 0;
                    while (true) {
                        Log.d(TAG,"sendAudioVideo i=" + i++);
                        int videoReadSampleCount = videoExtractor.readSampleData(videoByteBuffer, 0);
                        int audioReadSampleCount = audioExtractor.readSampleData(audioByteBuffer, 0);
                        if (videoReadSampleCount < 0) {
                            break;
                        }
                        //保存视频信道信息
                        byte[] videoBuffer = new byte[videoReadSampleCount];
                        videoByteBuffer.get(videoBuffer);

                        mTool.write(videoBuffer);
                        FrameInfo tmpInfo = new FrameInfo();
                        tmpInfo.codec_id = AVFrame.MEDIA_CODEC_VIDEO_H264;
                        tmpInfo.timestamp = System.currentTimeMillis();

                        TutkFrame tmpFrame = new TutkFrame();
                        //tmpFrame.setData(videoBuffer);
                        //tmpFrame.setFrameInfo(tmpInfo);
//                        mRealTimeMonitor.sendVideoData(tmpFrame);

                        videoByteBuffer.clear();

                        //保存音频信道信息
                        byte[] audioBuffer = new byte[audioReadSampleCount];
                        audioByteBuffer.get(audioBuffer);

                        FrameInfo audioTmpInfo = new FrameInfo();
                        audioTmpInfo.codec_id = AVFrame.MEDIA_CODEC_AUDIO_MP3;
                        audioTmpInfo.timestamp = System.currentTimeMillis();

                        TutkFrame audioTmpFrame = new TutkFrame();
                        //tmpFrame.setData(audioBuffer);
                        //tmpFrame.setFrameInfo(audioTmpInfo);

                        audioByteBuffer.clear();

                        videoExtractor.advance();//移动到下一帧
                        audioExtractor.advance();//移动到下一帧
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d(TAG,"sendAudioVideo e="+e.getMessage());
            }
        }
    }

    private void extractorMedia() {
        FileOutputStream videoOutputStream = null;
        FileOutputStream audioOutputStream = null;
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            //分离的视频文件
            File videoFile = new File(Constant.DIRCTORY, "output_video.mp4");
            //分离的音频文件
            File audioFile = new File(Constant.DIRCTORY, "output_audio.mp3");
            videoOutputStream = new FileOutputStream(videoFile);
            audioOutputStream = new FileOutputStream(audioFile);
            //源文件
            mediaExtractor.setDataSource(Constant.DIRCTORY + '/'+"test.mp4");
            //信道总数
            int trackCount = mediaExtractor.getTrackCount();
            int audioTrackIndex = -1;
            int videoTrackIndex = -1;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
                String mineType = trackFormat.getString(MediaFormat.KEY_MIME);
                //视频信道
                if (mineType.startsWith("video/")) {
                    videoTrackIndex = i;
                }
                //音频信道
                if (mineType.startsWith("audio/")) {
                    audioTrackIndex = i;
                }
            }

            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            //切换到视频信道
            mediaExtractor.selectTrack(videoTrackIndex);
            while (true) {
                int readSampleCount = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleCount < 0) {
                    break;
                }
                //保存视频信道信息
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                ///////////////


                videoOutputStream.write(buffer);
                byteBuffer.clear();
                mediaExtractor.advance();
            }
            //切换到音频信道
            mediaExtractor.selectTrack(audioTrackIndex);
            while (true) {
                int readSampleCount = mediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleCount < 0) {
                    break;
                }
                //保存音频信息
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                audioOutputStream.write(buffer);
                byteBuffer.clear();
                mediaExtractor.advance();
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"exactorMedia IO e="+e.getMessage());
        } finally {
            mediaExtractor.release();
            try {
                videoOutputStream.close();
                audioOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG,"exactorMedia e="+e.getMessage());
            }
        }
    }
}
