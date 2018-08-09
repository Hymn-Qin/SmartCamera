package com.zzdc.abb.smartcamera.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DataBaseHelper extends SQLiteOpenHelper {

    private static final String TAG = DataBaseHelper.class.getSimpleName();
    private Context mContext;
    private static final int VERSION = 1;
    private static final String DB_NAME = "allFile.db";
    public static final String HISTORY_VIDEOS = "history_videos";
    public static final String TIME_QUANTUM = "time_quantum";
    public static final String ALARM_VIDEOS = "alarm_videos";
    private static final String CREATE_VIDEO = "create table "+HISTORY_VIDEOS+" ("
            + "name text primary key, "
            + "startTime text, "
            + "endTime text,"
            + "startTimeLong integer)";


    private static final String CREATE_TIME_QUANTUM = "create table " +TIME_QUANTUM+" ("
            +"mStart text primary key,"
            +"mEnd text)";

    private static final String CREATE_ALARM_VIDEOS = "create table " +ALARM_VIDEOS+" ("
            +"mStart text primary key,"
            +"mEnd text)";

    public DataBaseHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_VIDEO);
        db.execSQL(CREATE_TIME_QUANTUM);
        db.execSQL(CREATE_ALARM_VIDEOS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogTool.d(TAG,"The oldVersion = "+oldVersion+", newVersion = "+newVersion);
    }
}
