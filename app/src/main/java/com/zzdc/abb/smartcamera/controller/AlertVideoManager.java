package com.zzdc.abb.smartcamera.controller;

import android.media.MediaExtractor;
import android.text.TextUtils;
import android.util.Log;

import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.info.VideoFileInfo;
import com.zzdc.abb.smartcamera.util.BufferPool;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.WriteToFileTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class AlertVideoManager {

    private static final String TAG = AlertVideoManager.class.getSimpleName();
    private static final AlertVideoManager mInstance = new AlertVideoManager();
    private WriteToFileTool mTool;
    private String mDestFile = null;
    private Boolean mIsWorking = false;
    private SDCardBussiness mSDCardBussiness;
    public static byte[] PPS;
    private long mVideoSatrtTimeStamp;   //视频内部时间
    private long mVideoStartTime;        //视频文件时间
    private long mDestTime;
    private ArrayList<File> mFiles;

    private BufferPool<TutkFrame> mVideoBufPool = new BufferPool<>(TutkFrame.class, 3);
    private BufferPool<TutkFrame> mAudioBufPool = new BufferPool<>(TutkFrame.class, 3);

    private LinkedBlockingQueue<TutkFrame> mVideoQueue = new LinkedBlockingQueue<>();
    private LinkedBlockingQueue<TutkFrame> mAudioQueue = new LinkedBlockingQueue<>();
    private CopyOnWriteArrayList<AvMediaTransfer.AvTransferLister> mAvTransferListers = new CopyOnWriteArrayList<>();
    private boolean isDoSend = false;

    private Thread mVideoSendThread;
    private Thread mAudioSendThread;
    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;

    public MP4Extrator mExtrator;

    private AlertVideoManager(){

    }

    public static final AlertVideoManager getInstance(){
        return mInstance;
    }

    public void registerAvTransferListener(AvMediaTransfer.AvTransferLister listener) {
        LogTool.d(TAG,"transferlisterners count:"+mAvTransferListers.size());

        if (!mAvTransferListers.contains(listener)) {
            LogTool.d(TAG,"transferlisterners count: add");
            mAvTransferListers.add(listener);
        }
    }

    public void unRegisterAvTransferListener(AvMediaTransfer.AvTransferLister listener) {
        LogTool.d(TAG,"transferlisterners count: remove");
        mAvTransferListers.remove(listener);
    }

    private void sortFile(){
        mSDCardBussiness = SDCardBussiness.getInstance();
        String videoRootPath = mSDCardBussiness.getSDCardVideoRootPath() + "/" + "ALERT/";
        ArrayList<File>tmpFiles = new ArrayList<>();
        getFiles(tmpFiles ,videoRootPath ,".mp4");

        if(tmpFiles != null &&tmpFiles.size()>0){
            Collections.sort(tmpFiles, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {

                    if(o1.isDirectory()&&o2.isFile())
                        return -1;
                    if(o1.isFile()&&o2.isDirectory())
                        return 1;
                    return o2.getName().compareTo(o1.getName());
                }
            });
        }


        mFiles = tmpFiles;
    }

    public ArrayList<String> getHistoryVideoFileInfo(){

        ArrayList<String> tmpRet = new ArrayList<>();
        JSONObject tmpRes = new JSONObject();
        mSDCardBussiness = SDCardBussiness.getInstance();
        if(!mSDCardBussiness.isSDCardAvailable()){
            try {
                tmpRes.put("type", "getAlertInfoList");
                tmpRes.put("ret", "-1");
            }catch (Exception e){
                Log.d(TAG," Exception " + e.toString());
            }
            tmpRet.add(tmpRes.toString());
            return tmpRet;
        }

        ArrayList<VideoFileInfo> tmpInfos = new ArrayList<>();
        sortFile();
        JSONArray tmpJson = new JSONArray();

        for (int i = 0; i< mFiles.size(); i++){
            VideoFileInfo tmpFileInfo = new VideoFileInfo();
            String tmpTime = mFiles.get(i).getName().substring(mFiles.get(i).getName().lastIndexOf("VID_") + 4, mFiles.get(i).getName().indexOf(".mp4"));
            tmpFileInfo.setFileName(mFiles.get(i).getName());
//            Log.d(TAG,"tmpTime " + tmpTime);
            DateFormat tmpFormat1 = new SimpleDateFormat("yyyyMMdd_HHmmss");
            SimpleDateFormat tmpDateFormat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            try {
                Date tmpDate = tmpFormat1.parse(tmpTime);
                tmpFileInfo.setStartTime(tmpDateFormat2.format(tmpDate));
                JSONObject tmpObj = new JSONObject();
                tmpObj.put("Date", tmpFileInfo.getStartTime());
                tmpJson.put(tmpObj);
//                Log.d(TAG,"tmpJson.toString().length() " + tmpJson.toString().length());

                if(i%25 == 0 ||i == (mFiles.size() - 1)){
                    tmpRes.put("type", "getAlertInfoList");
                    tmpRes.put("ret", "0");
                    tmpRes.put("DateArray", tmpJson);
                    tmpRet.add(tmpRes.toString());
                    tmpRes.remove("type");
                    tmpRes.remove("ret");
                    tmpRes.remove("DateArray");
                    tmpJson = null;
                    tmpJson = new JSONArray();
                }

            } catch (Exception e) {
                Log.d(TAG,"format1.parse ParseException " + e);
            }
        }

//        Log.d(TAG,"all file info " + tmpRet.toString());
        return tmpRet;
    }
    /*
     * 功能：根据时间参数寻找文件
     * 参数：aTime
     * 返回值：
     * */
    public String searchVideoFile(String aTime){
        String tmpSearchFile = null;
        if(TextUtils.isEmpty(aTime)){
            Log.d(TAG, "searchVideo aTime ==null");
            return tmpSearchFile;

        }
        Log.d(TAG,"aTime = " + aTime);
        mSDCardBussiness = SDCardBussiness.getInstance();
        if(!mSDCardBussiness.isSDCardAvailable()){
            Log.d(TAG,"SDCard not Available");
            return tmpSearchFile;
        }

        Date tmpDate;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat tmpDateFormat = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss'.mp4'");
        String tmpFileName = null;
        try{
            tmpDate = sdf.parse(aTime);
            Log.d(TAG,"tmpDate = " + tmpDate );
            mDestTime = tmpDate.getTime();
            tmpFileName = tmpDateFormat.format(tmpDate);
            Log.d(TAG,"tmpFileName = " + tmpFileName );
        }catch(Exception e){
            Log.d(TAG,"dateFormat.parse Exception " + e.toString());
        }

        getHistoryVideoFileInfo();

        for(int i = 0; i < mFiles.size(); i++){

            if(mFiles.get(i).getName().compareTo(tmpFileName) == 0 ){
                tmpSearchFile = mFiles.get(i).getName();
                Log.d(TAG,"search file just begin time equal para  " + mFiles.get(i).getName());
                break;
            }
            if(i == mFiles.size() - 1)
                break;

            if(mFiles.get(i).getName().compareTo(tmpFileName) >= 0 &&(mFiles.get(i + 1).getName().compareTo(tmpFileName) < 0)){
                tmpSearchFile = mFiles.get(i + 1).getName();
                Log.d(TAG,"search file is " + mFiles.get(i + 1).getName());
                break;
            }
        }
        Log.d(TAG," SearchFile  = " + tmpSearchFile);
        return tmpSearchFile;

    }

    private void setVideoSatrtTime() {
        if (TextUtils.isEmpty(mDestFile)) {
            return;
        }


        String tmpTime =mDestFile.substring(mDestFile.lastIndexOf("VID_") + 4,mDestFile.indexOf(".mp4"));
        Log.d(TAG,"mDestFile " + mDestFile);
        DateFormat format1 = new SimpleDateFormat("yyyyMMdd_HHmmss");
        try {
            Date tmpDate = format1.parse(tmpTime);
            mVideoStartTime = tmpDate.getTime();
            Log.d(TAG,"mVideoSatrtTime = " + mVideoStartTime);
        } catch (ParseException e) {
            Log.d(TAG,"format1.parse ParseException " + e);
        }


    }
    private void getFiles(ArrayList<File> fileList, String path, String aExtension) {
        File[] allFiles = new File(path).listFiles();

        if(allFiles == null ||allFiles.length == 0){
            return;
        }
        for (int i = 0; i < allFiles.length; i++) {
            File file = allFiles[i];
            if (file.isFile()) {
                if (file.getPath().substring(file.getPath().length() - aExtension.length()).equals(aExtension))
                    fileList.add(file);
            } else if (!file.getAbsolutePath().contains(".thumnail")) {
                getFiles(fileList, file.getAbsolutePath(), aExtension);
            }
        }
    }
    /*
     * 处理历史视频指令
     *
     * **/
    public int handleHistoryVideo(String aDate){
        Log.d(TAG,"handleHistoryVideo");
        if (TextUtils.isEmpty(aDate)){
            mDestFile = searchVideoFile("2018-07-17 19:59:10");
        }else {
            mDestFile = searchVideoFile(aDate);
        }
        Log.d(TAG,"aDate = " + aDate);
        if(TextUtils.isEmpty(mDestFile)){
            return -1;
        }
        setVideoSatrtTime();
        mDestFile = mSDCardBussiness.getSDCardVideoRootPath() + "/" + "ALERT" + "/" + mDestFile;

        if (mExtrator!= null){
            mExtrator.stop();
            mExtrator = null;
        }

        mExtrator = new MP4Extrator(mDestFile, mDestTime, mVideoStartTime);
        int tmpResult = mExtrator.init();
        Log.d(TAG,"mExtrator.init " + tmpResult);
        if(tmpResult == 0){
            mExtrator.start();
        }
        return 0;

    }

    public void release(){

        mExtrator.stop();
        mExtrator = null;
    }




}
