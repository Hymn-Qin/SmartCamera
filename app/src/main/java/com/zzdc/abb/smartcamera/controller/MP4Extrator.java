package com.zzdc.abb.smartcamera.controller;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.tutk.IOTC.AVFrame;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.ByteToHexTool;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class MP4Extrator {
    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;
    private static final  String TAG = MP4Extrator.class.getSimpleName();
    private boolean mIsWorking = false;
    private boolean mIsDoSend = false;
    private String mDestFilePath ;
    private long mDestTime;
    private long mVideoSatrtTimeStamp;   //视频内部时间
    private long mVideoStartTime;        //视频文件时间
    private int mAudioTrackIndex = -1;
    private int mVideoTrackIndex = -1;

    ByteBuffer mAudioByteBuffer = ByteBuffer.allocate(500 * 1024);
    ByteBuffer mVideoByteBuffer = ByteBuffer.allocate(500 * 1024);
    private Thread mExtratorThread;
    private BufferPool<TutkFrame> mVideoBufPool = new BufferPool<>(TutkFrame.class, 3);
    private BufferPool<TutkFrame> mAudioBufPool = new BufferPool<>(TutkFrame.class, 3);
    private CopyOnWriteArrayList<ExtratorDataListenner> mListenrs = new CopyOnWriteArrayList<>();

    byte[]PPS = new byte[]{
        (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x67,
                (byte)0x42, (byte)0x80, (byte)0x1E, (byte)0xDA, (byte)0x02,
                (byte)0x80, (byte)0xF6, (byte)0x94, (byte)0x82, (byte)0x81,
                (byte)0x01, (byte)0x03, (byte)0x68, (byte)0x50, (byte)0x9A,
                (byte)0x80, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
                (byte)0x68, (byte)0xCE, (byte)0x06, (byte)0xE2};

    public MP4Extrator(String aFilePath, long aDestTime, long aVideoStartTime){

        mDestFilePath = aFilePath;
        mDestTime = aDestTime;
        mVideoStartTime = aVideoStartTime;

    }
    public  int init(){
        Log.d(TAG,"mDestFilePath = " + mDestFilePath);
        File tmpFile = new File(mDestFilePath);
        if (tmpFile == null || ! tmpFile.exists() || tmpFile.length() == 0) {
            Log.d(TAG,"文件不存在");
            mIsWorking = false;
            return -1;
        }else {
            mVideoExtractor = new MediaExtractor();
            mAudioExtractor = new MediaExtractor();
            int tmpVideoTrack = -1;
            int tmpAudioTrack = -1;

            try {
                mAudioExtractor.setDataSource(mDestFilePath);
                mVideoExtractor.setDataSource(mDestFilePath);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG,"setDataSource IOException " + e.toString());
                return -2;
            }


            int tmpVideoTrackCount = mVideoExtractor.getTrackCount();
            int tmpAudioTrackCount = mAudioExtractor.getTrackCount();
            for (int i = 0; i < tmpVideoTrackCount; i++){
                MediaFormat tmpFormat = mVideoExtractor.getTrackFormat(i);
                String mineType = tmpFormat.getString(MediaFormat.KEY_MIME);
                //视频信道
                if (mineType.startsWith("video/")) {
                    mVideoTrackIndex = i;
                    break;
                }
            }

            for (int i = 0; i < tmpAudioTrackCount; i++) {
                MediaFormat tmpFormat = mAudioExtractor.getTrackFormat(i);
                String mineType = tmpFormat.getString(MediaFormat.KEY_MIME);
                //音频信道
                if (mineType.startsWith("audio/")) {
                    mAudioTrackIndex = i;
                    break;
                }
            }

        }

        try{
            mVideoExtractor.selectTrack(mVideoTrackIndex);
            mAudioExtractor.selectTrack(mAudioTrackIndex);
        }catch (Exception e){
            Log.d(TAG,"selectTrack Exception " + e.toString());
            return -3;
        }


        return 0;
    }

    public void start(){
        mExtratorThread = new Thread("ExtratorThread"){
            @Override
            public void run() {
                super.run();
                    extraMP4();
            }
        };
        mExtratorThread.start();
        mIsWorking = true;
    }

    private void extraMP4(){

        Log.d(TAG,"extraMP4");
        TutkFrame tmpVideoFrame = null;
        TutkFrame tmpAudioFrame = null;
        long tmpSeekTime = mDestTime - mVideoStartTime;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        int i = 0;
        while(mIsWorking){
            try{
                long tmpTime = mVideoExtractor.getSampleTime();
                if(i == 0){
                    mVideoSatrtTimeStamp = mVideoExtractor.getSampleTime();
                if(tmpSeekTime > 0)
                {
                    Log.d(TAG,"mVideoSatrtTimeStamp = " + mVideoSatrtTimeStamp + " seekTo = " + ((tmpSeekTime * 1000) + mVideoSatrtTimeStamp));
                    mVideoExtractor.seekTo((tmpSeekTime * 1000) + mVideoSatrtTimeStamp , MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    mAudioExtractor.seekTo((tmpSeekTime * 1000) + mVideoSatrtTimeStamp , MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }

                }

                int tmpVideoSampleCount = mVideoExtractor.readSampleData(mVideoByteBuffer, 0);
                int tmpAudioSampleCount = mAudioExtractor.readSampleData(mAudioByteBuffer, 0);
                Log.d(TAG,"tmpVideoSampleCount = " + tmpVideoSampleCount);
                if (tmpVideoSampleCount < 0) {
                    mIsWorking = false;
                    break;
                }

                long tmpDurion = tmpTime - mVideoSatrtTimeStamp;
                long tmpRecodingTime = mVideoStartTime + (tmpDurion/1000);

                Date tmpTime2 = new Date(tmpRecodingTime);
                ByteBuffer tmpBuffer = mVideoByteBuffer;
                int type = tmpBuffer.get(4)& 0x1F;
                if (type == 7 || type == 8) {
                    //sps
                    Log.d(TAG,"before copy pps");
                    byte[] tmpPPS = new byte[tmpVideoSampleCount];
                    tmpBuffer.get(tmpPPS, 0, tmpVideoSampleCount);
                    PPS = tmpPPS;
                    Log.d(TAG,"配置帧信息：" + ByteToHexTool.bytesToHex(tmpPPS));
                    Log.d(TAG,"after copy pps");
                } else if (type == 5) {
                    Log.d(TAG,"发现关键帧");
                    if(PPS == null){
                        Log.d(TAG,"PPS " + ByteToHexTool.bytesToHex(PPS));
                        PPS = Constant.PPS;
                    }else {
                        Log.d(TAG,"PPS " + ByteToHexTool.bytesToHex(PPS));
                    }

                    //tutk 标签
                    if (PPS != null && PPS.length > 0) {

                        tmpVideoFrame = mVideoBufPool.getBuf(PPS.length + tmpVideoSampleCount);
                        tmpVideoFrame.setDataLen(PPS.length + tmpVideoSampleCount);
                        System.arraycopy(PPS, 0, tmpVideoFrame.getData(), 0, PPS.length);

                        try {

                            mVideoByteBuffer.get(tmpVideoFrame.getData(), PPS.length, tmpVideoSampleCount);
                        } catch (Exception e) {
                            Log.d(TAG, "COPY PPS AND DATA exception " + e);
                        }
                    } else {
                        Log.d(TAG,"发现关键帧，但配置帧为空");
                        tmpVideoFrame = mVideoBufPool.getBuf(tmpVideoSampleCount);
                        tmpVideoFrame.setDataLen(tmpVideoSampleCount);

                        try {
                            mVideoByteBuffer.get(tmpVideoFrame.getData(), 0, tmpVideoSampleCount);
                        } catch (Exception e) {
                            Log.d(TAG, "Exception " + e.toString());
                        }
                    }

                } else {

                    tmpVideoFrame = mVideoBufPool.getBuf(tmpVideoSampleCount);
                    tmpVideoFrame.setDataLen(tmpVideoSampleCount);
                    try {

                        mVideoByteBuffer.get(tmpVideoFrame.getData(), 0, tmpVideoSampleCount);
                    } catch (Exception e) {
                        Log.d(TAG, "3333 Exception " + e.toString());
                    }
                }


                //新的

                tmpVideoFrame.getFrameInfo().codec_id = AVFrame.MEDIA_CODEC_VIDEO_H264;
                tmpVideoFrame.getFrameInfo().timestamp = tmpRecodingTime;
//            tmpVideoFrame.getFrameInfo().mType = "H264";
//            tmpVideoFrame.getFrameInfo().timestamp = sdf.format(tmpTime2);
                for (int j= 0;j< mListenrs.size();j++){
                    mListenrs.get(j).onExtratorVideoDataReady(tmpVideoFrame);
                }
                tmpVideoFrame.decreaseRef();
                mVideoByteBuffer.clear();



                //保存音频信道信息
                if(tmpAudioSampleCount > 0){


                    int dataLen = tmpAudioSampleCount + 7;
                    TutkFrame frame = mAudioBufPool.getBuf(dataLen);
                    frame.setDataLen(dataLen);

                    addADTStoPacket(frame.getData(), dataLen);
                    mAudioByteBuffer.get(frame.getData(), 7,tmpAudioSampleCount);
                    frame.getFrameInfo().codec_id = AVFrame.MEDIA_CODEC_AUDIO_MP3;
                    frame.getFrameInfo().timestamp = tmpRecodingTime;

//                frame.getFrameInfo().mType = "AAC";
//                frame.getFrameInfo().timestamp = sdf.format(tmpTime2);
//                mAudioQueue.offer(frame);
                    for (int j= 0;j< mListenrs.size();j++){
                        mListenrs.get(j).onExtratorAudioDataReady(frame);
                    }
                    frame.decreaseRef();

                }

                mAudioByteBuffer.clear();
                mVideoExtractor.advance();//移动到下一帧
                mAudioExtractor.advance();//移动到下一帧
            }catch(Exception e){
                Log.d(TAG,"Exception " + e.toString());
            }
            
            i++;
        }
    }


    public void stop(){
        mIsWorking = false;
        mVideoExtractor.release();
        mAudioExtractor.release();
        mExtratorThread.interrupt();
    }

    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 8; // 16 KHz
        int chanCfg = 2; // CPE
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
    /*
     * 文件视频数据接口
     *
     *
     **/
    public interface ExtratorDataListenner{
        public void onExtratorVideoDataReady(TutkFrame aFrame);
        public void onExtratorAudioDataReady(TutkFrame aFrame);
    }

    public void registerExtratorListener(ExtratorDataListenner aListenner){
        if (!mListenrs.contains(aListenner)){
            mListenrs.add(aListenner);
        }
    }

    public void unRegisterExtratorListener(ExtratorDataListenner aListenner){
        mListenrs.remove(aListenner);
    }
}
