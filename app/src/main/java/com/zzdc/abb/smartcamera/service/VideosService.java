package com.zzdc.abb.smartcamera.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.os.FileObserver;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.DataBaseHelper;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public class VideosService extends Service {
    private static final String TAG = VideosService.class.getSimpleName();
    private SQLiteDatabase db;
    private String rootPath;
    private ArrayList<File> fileArray = new ArrayList<>();
    private DateFormat tmpFormat1 = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private SimpleDateFormat tmpDateFormat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private MediaPlayer meidaPlayer;
    UpdateTimeQuantumSQLiteListener mUpdateListener;
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long mStartTime,mEndTime;
    private SDCardRootPathListener mSDCardRootPathListener;
    private SDCardListener mSDCardListener;
    private String mFileDir;
    private DirRemovedListener mDirRemovedListener;
    private static String mNextToAddFileName = "firstScan";
    private static boolean mIsDCIMCreated = false;
    private final String mFileDirName = "DCIM";

    public VideosService() {
    }

    public class DirRemovedListener extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)) {
                LogTool.d(TAG,"Intent.ACTION_MEDIA_EJECT");
                stopSelf();
            }
        }
    }

    private void startWork() {
        LogTool.d(TAG,"Database begin to word!!!");
        mSDCardListener = new SDCardListener(mFileDir);
        mSDCardListener.startWatching();
        //创建DATABASE
        DataBaseHelper mDataBaseHelper = new DataBaseHelper(this);
        db = mDataBaseHelper.getWritableDatabase();
        if (null != db ) {
            //开启子线程开始扫描并记录数据库.
            scanAndUpdateSQLite();
        } else {
            LogTool.d(TAG,"db is null!");
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogTool.d(TAG,"onCreate!!!");
        SDCardBussiness SDCardManager = SDCardBussiness.getInstance();
        mDirRemovedListener = new DirRemovedListener();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addDataScheme("file");
        registerReceiver(mDirRemovedListener,filter);
        if (SDCardManager.isSDCardAvailable()) {
            mUpdateListener = new UpdateTimeQuantumSQLiteListener() {
                @Override
                public void startUpdate() {
                    //启动新线程更新存储时间段的表.
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            beginUpdate();
                        }
                    }).start();
                }
            };
            meidaPlayer = new MediaPlayer();
            rootPath = SDCardManager.getSDCardVideoRootPath() + "/";
            LogTool.d(TAG,"SDCard root path = "+rootPath);
            mFileDir = rootPath+mFileDirName+"/";
            File fileDir = new File(mFileDir);
            if (!fileDir.exists()) {
                LogTool.d(TAG,mFileDir+" does not exist!!! start listener!");
                mSDCardRootPathListener = new SDCardRootPathListener(rootPath);
                mSDCardRootPathListener.startWatching();
            } else {
                startWork();
            }
        }
    }

    private int getAllFiles(ArrayList<File> fileArray, String path) {
        fileArray.clear();
        File[] allFiles = new File(path).listFiles();

        if(allFiles == null ||allFiles.length == 0){
            LogTool.d(TAG,"There is no video file in SDCard!");
            return 0;
        }

        for (File file:allFiles) {
            if (file.isFile()) {
                if (file.getName().startsWith("VID") && file.getName().endsWith(".mp4"))
                    fileArray.add(file);
            }
        }

        LogTool.d(TAG,"There are "+fileArray.size()+" videos on SDCard.");
        if(fileArray.size()>0){
            Collections.sort(fileArray, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o2.getName().compareTo(o1.getName());
                }
            });
        }
        return fileArray.size();
    }

    private long getDuration(File file) {
        long second = 0;
        try {
            meidaPlayer.setDataSource(file.getAbsolutePath());
            meidaPlayer.prepare();
            second = meidaPlayer.getDuration();
            meidaPlayer.reset();
        } catch (Exception e) {
            LogTool.w(TAG, "Get video duration with exception : ", e);
        }
        return second;
    }

    private void setDataBase(File file,String fileName,String absoluteName) {
        try {
            Date startDate = tmpFormat1.parse(fileName.substring(fileName.lastIndexOf("VID_") + 4,
                    fileName.indexOf(".mp4")));
            String startTime = tmpDateFormat2.format(startDate);
            Long startTimeLong = startDate.getTime();
            String endTime = tmpDateFormat2.format(new Date(startDate.getTime()+getDuration(file)));
            LogTool.d(TAG,"Parse mp4 file, startTime = "+startTime + ", endTime = "+endTime+", startTimeLong = "+startTimeLong);

            ContentValues contentValues = new ContentValues();
            contentValues.put("name",absoluteName);
            contentValues.put("startTime",startTime);
            contentValues.put("endTime",endTime);
            contentValues.put("startTimeLong",startTimeLong);
            db.insert(DataBaseHelper.HISTORY_VIDEOS,null,contentValues);
        } catch (ParseException e) {
            LogTool.w(TAG, "Parse file name with exception, ", e);
        }
    }

    private synchronized void scanAndUpdateSQLite() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                db.execSQL("delete from "+DataBaseHelper.HISTORY_VIDEOS);
                if(getAllFiles(fileArray,rootPath + mFileDirName + "/")<0) {
                    LogTool.d(TAG,"There is no video!!!");
                    return;
                }
                for(int i=(fileArray.size()-1); i>=0; i--) {
                    //一：获取视频名称以及绝对路径。
                    File file = fileArray.get(i);
                    String fileName = file.getName();
                    if(i > 0) {
                        String absoluteName = rootPath+mFileDirName+"/"+fileName;
                        setDataBase(file,fileName,absoluteName);
                        if (i == 1 && null != mUpdateListener) {
                            mUpdateListener.startUpdate();
                        }
                    } else {
                        mNextToAddFileName = fileName;
                    }

                }
            }
        }).start();
    }

    private synchronized void beginUpdate() {

        //清除记录时间段的表.
        db.execSQL("delete from "+DataBaseHelper.TIME_QUANTUM);
        //获取所有的mp4文件信息.
        Cursor cursor = db.rawQuery("select * from "+DataBaseHelper.HISTORY_VIDEOS, null);
        LogTool.d(TAG,"The videos number = "+cursor.getCount());
        if (cursor.getCount()>=1) {
            cursor.moveToFirst();
            String mStart = cursor.getString(cursor.getColumnIndex("startTime"));
            for(int i=0; i<(cursor.getCount());i++){
                cursor.moveToPosition(i);
                String mEnd = cursor.getString(cursor.getColumnIndex("endTime"));
                if (i<(cursor.getCount()-1)) {
                    cursor.moveToNext();
                    String compareStartTime = cursor.getString(cursor.getColumnIndex("startTime"));
                    try {
                        mStartTime = simpleDateFormat.parse(compareStartTime).getTime();
                        mEndTime = simpleDateFormat.parse(mEnd).getTime();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    //大于10秒就认为文件不连续.
                    if ((mStartTime-mEndTime) > 10000) {
                        db.execSQL("INSERT OR IGNORE INTO "+DataBaseHelper.TIME_QUANTUM+" VALUES(?,?)",
                                new String[] {mStart,mEnd});
                        mStart = cursor.getString(cursor.getColumnIndex("startTime"));
                    }
                } else {
                    db.execSQL("INSERT OR IGNORE INTO "+DataBaseHelper.TIME_QUANTUM+" VALUES(?,?)",
                            new String[] {mStart,mEnd});
                }
            }
            cursor.close();
        } else {
            LogTool.d(TAG,"There is no video!");
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogTool.d(TAG,"VideosService onStartCommand!");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogTool.d(TAG,"onDestroy!!!");
        if(null != meidaPlayer) {
            meidaPlayer.release();
            meidaPlayer = null;
        }
        if(null != mSDCardRootPathListener) {
            mSDCardRootPathListener.stopWatching();
        }
        if(null != mSDCardListener) {
            mSDCardListener.stopWatching();
        }
        if(null != mDirRemovedListener) {
            unregisterReceiver(mDirRemovedListener);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    //添加指定的视频数据库，参数是不带绝对路径的文件名.
    private synchronized void addVideo(String fileName) {
        String absoluteName = rootPath + mFileDirName + "/" + fileName;
        Cursor cursor = db.query(DataBaseHelper.HISTORY_VIDEOS,new String[] {"name"},"name = ?",
                new String[] {absoluteName},null,null,null);
        if (cursor.getCount() == 0) {
            Cursor comCursor = db.rawQuery("select * from "+DataBaseHelper.HISTORY_VIDEOS, null);
            if(comCursor.getCount() == 0) {
                File file = new File(absoluteName);
                setDataBase(file,fileName,absoluteName);
                if (null != mUpdateListener) {
                    mUpdateListener.startUpdate();
                }
                return;
            }
            comCursor.moveToLast();
            String currentLastName = comCursor.getString(comCursor.getColumnIndex("name"));
            LogTool.d(TAG,"The last file name is = "+currentLastName);
            if (fileName.compareTo(currentLastName.substring(currentLastName.lastIndexOf("VID")))>0) {
                //当新增的文件时间相对于数据库是最新的话就直接写入数据库。
                File file = new File(absoluteName);
                setDataBase(file,fileName,absoluteName);
                if (null != mUpdateListener) {
                    mUpdateListener.startUpdate();
                }
            } else {
                //当新增的文件时间早于数据库已有的最新时间，则重新编写数据库。
                scanAndUpdateSQLite();
            }

            comCursor.close();
        } else {
            LogTool.w(TAG,"SDCard has this video : "+absoluteName);
        }
        cursor.close();
    }

    //删除指定的视频数据库,参数是不带绝对路径的文件名.
    private synchronized void removeVideo(String fileName) {
        String absoluteName = rootPath + mFileDirName + "/" + fileName;
        Cursor cursor = db.query(DataBaseHelper.HISTORY_VIDEOS,new String[] {"name"},"name = ?",
                new String[] {absoluteName},null,null,null);
        if (cursor.getCount() != 0) {
            db.delete(DataBaseHelper.HISTORY_VIDEOS,"name = ?",new String[] {absoluteName});
            if (null != mUpdateListener) {
                mUpdateListener.startUpdate();
            }
        } else {
            LogTool.w(TAG,"Can not find this video : "+absoluteName);
        }
        cursor.close();
    }

    private interface UpdateTimeQuantumSQLiteListener {
        void startUpdate();
    }

    //创建根目录监听器，监听历史文件存储目录的创建
    private class SDCardRootPathListener extends FileObserver {

        private SDCardRootPathListener(String path) {
            super(path);
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            if(null != path && path.equalsIgnoreCase(mFileDirName) && !mIsDCIMCreated) {
                LogTool.d(TAG,path+" has created!!!");
                mIsDCIMCreated = true;
                startWork();
            }
        }
    }

    //创建目录监听器类
    private class SDCardListener extends FileObserver {

        private SDCardListener(String path) {
            super(path);
            LogTool.d(TAG,"Observer path = "+path);
        }

        @Override
        public void onEvent(int event, @Nullable final String path) {
            switch (event) {
                case FileObserver.DELETE :
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            removeVideo(path);
                        }
                    }).start();
                    break;
                case FileObserver.CREATE :
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if(mNextToAddFileName.equalsIgnoreCase("firstScan")) {
                                scanAndUpdateSQLite();
                            } else {
                                addVideo(mNextToAddFileName);
                                mNextToAddFileName = path;
                            }
                        }
                    }).start();
                    break;
            }
        }
    }
}
