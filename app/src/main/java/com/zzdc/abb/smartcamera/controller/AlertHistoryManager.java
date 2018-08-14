package com.zzdc.abb.smartcamera.controller;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.DataBaseHelper;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class AlertHistoryManager {

    private static final String TAG = AlertHistoryManager.class.getSimpleName();
    private static final AlertHistoryManager mInstance = new AlertHistoryManager();
    private String mDestFile = null;
    private SDCardBussiness mSDCardBussiness;
    private long mVideoStartTime;        //视频文件时间
    private long mDestTime;
    public MP4Extrator mExtrator;
    private SQLiteDatabase db;

    private AlertHistoryManager(){
        DataBaseHelper mDataBaseHelper = new DataBaseHelper(SmartCameraApplication.getContext());
        db = mDataBaseHelper.getWritableDatabase();
        mSDCardBussiness = SDCardBussiness.getInstance();
    }

    public static AlertHistoryManager getInstance(){
        return mInstance;
    }

    public ArrayList<String> getHistoryVideoFileInfo(){

        ArrayList<String> videosInfo = new ArrayList<>();
        JSONObject objectForVideoInfo = new JSONObject();
        if(!mSDCardBussiness.isSDCardAvailable()){
            try {
                objectForVideoInfo.put("type", "getAlertInfoList");
                objectForVideoInfo.put("ret", "-1");
            }catch (Exception e){
                LogTool.d(TAG," Exception " + e.toString());
            }
            videosInfo.add(objectForVideoInfo.toString());
            return videosInfo;
        }

        JSONArray timeQuantumArray = new JSONArray();

        Cursor cursor = db.rawQuery("select * from "+ DataBaseHelper.TIME_QUANTUM, null);
        for (int i=0;i<(cursor.getCount());i++) {
            JSONObject oneTimeQuantum = new JSONObject();
            cursor.moveToPosition(i);
            try {
                oneTimeQuantum.put("mStart",cursor.getString(cursor.getColumnIndex("mStart")));
                oneTimeQuantum.put("mEnd",cursor.getString(cursor.getColumnIndex("mEnd")));
                timeQuantumArray.put(oneTimeQuantum);
                if(i%10 == 0 ||i == (cursor.getCount() - 1)){
                    objectForVideoInfo.put("type", "getAlertInfoList");
                    objectForVideoInfo.put("ret", "0");
                    objectForVideoInfo.put("DateArray", timeQuantumArray);
                    videosInfo.add(objectForVideoInfo.toString());
                    objectForVideoInfo.remove("type");
                    objectForVideoInfo.remove("ret");
                    objectForVideoInfo.remove("DateArray");
                    timeQuantumArray = null;
                    timeQuantumArray = new JSONArray();
                }
            } catch (JSONException e) {
                LogTool.e(TAG,"Get time quantum error : "+e);
            }
        }

        cursor.close();
        LogTool.d(TAG,"all file info " + videosInfo.toString());
        return videosInfo;
    }
    /*
     * 功能：根据时间参数寻找文件
     * 参数：aTime
     * 返回值：
     * */
    private String searchVideoFile(String aTime){
        LogTool.d(TAG,"aTime = " + aTime);
        String tmpSearchFile = null;

        if(TextUtils.isEmpty(aTime) || !mSDCardBussiness.isSDCardAvailable()){
            LogTool.d(TAG, "searchVideo aTime is null or SDCard not available!!!");
            return tmpSearchFile;

        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Cursor cursor;
        try {
            mDestTime = dateFormat.parse(aTime).getTime();
            cursor = db.query(DataBaseHelper.HISTORY_VIDEOS,new String[] {"startTimeLong"},
                    "startTimeLong <= "+mDestTime,null,null,null,null);
            if(cursor.getCount() > 0) {
                cursor.moveToLast();
                String absoluteName = cursor.getString(cursor.getColumnIndex("name"));
                tmpSearchFile = absoluteName.substring(absoluteName.lastIndexOf("VID"));
            }
            cursor.close();
        } catch (ParseException e) {
            LogTool.e(TAG,"Parse need file aTime error : "+e);
        }

        LogTool.d(TAG," Search history file  = " + tmpSearchFile);
        return tmpSearchFile;

    }

    private void setVideoSatrtTime() {
        if (TextUtils.isEmpty(mDestFile)) {
            return;
        }

        String tmpTime =mDestFile.substring(mDestFile.lastIndexOf("VID_") + 4,mDestFile.indexOf(".mp4"));
        LogTool.d(TAG,"mDestFile " + mDestFile);
        DateFormat format1 = new SimpleDateFormat("yyyyMMdd_HHmmss");
        try {
            Date tmpDate = format1.parse(tmpTime);
            mVideoStartTime = tmpDate.getTime();
            LogTool.d(TAG,"mVideoSatrtTime = " + mVideoStartTime);
        } catch (ParseException e) {
            LogTool.d(TAG,"format1.parse ParseException " + e);
        }

    }

    /*
     * 处理历史视频指令
     *
     * **/
    public int handleHistoryVideo(String aDate){
        LogTool.d(TAG,"handleHistoryVideo");
        if (TextUtils.isEmpty(aDate)){
            mDestFile = searchVideoFile("2018-07-17 19:59:10");
        }else {
            mDestFile = searchVideoFile(aDate);
        }
        LogTool.d(TAG,"aDate = " + aDate);
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
        if(mExtrator.init()){
            mExtrator.start();
        }
        return 0;
    }

    public void release(){
        mExtrator.stop();
        mExtrator = null;
    }
}
