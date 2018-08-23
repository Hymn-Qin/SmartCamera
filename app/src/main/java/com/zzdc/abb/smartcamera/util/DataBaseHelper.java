package com.zzdc.abb.smartcamera.util;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DataBaseHelper extends SQLiteOpenHelper {
    private static final String TAG = DataBaseHelper.class.getSimpleName();
    private static final String DB_NAME = "media.db";
    private static final int VERSION = 1;

    public static final class History {
        public static final String TABLE = "history_videos";
        public static final String FILE_NAME = "file";
        public static final String START_STRING = "startTime";
        public static final String END_STRING = "endTime";
        public static final String START_LONG = "startTimeLong";
        public static final String END_LONG = "endTimeLong";

        private static final String CREATE = "create table "+TABLE+" ("
                + FILE_NAME +" text primary key, "
                + START_STRING + " text, "
                + END_STRING + " text,"
                + START_LONG + " integer,"
                + END_LONG + " integer)";

        public static final String DELETE = "delete from " + TABLE;
    }

    public static final class HistoryQuantum {
        public static final String TABLE = "history_quantum";
        public static final String START_STRING = "mStart";
        public static final String END_STRING = "mEnd";
        public static final String START_LONG = "startTimeLong";
        public static final String END_LONG = "endTimeLong";

        private static final String CREATE = "create table " +TABLE+" ("
                + START_STRING + " text primary key,"
                + END_STRING + " text,"
                + START_LONG + " integer,"
                + END_LONG + " integer)";

        public static final String DELETE = "delete from " + TABLE;
    }

    public static final class Warning {
        public static final String TABLE = "warning_videos";
        public static final String FILE_NAME = "file";
        public static final String START_STRING = "startTime";
        public static final String END_STRING = "endTime";
        public static final String START_LONG = "startTimeLong";
        public static final String END_LONG = "endTimeLong";

        private static final String CREATE = "create table "+TABLE+" ("
                + FILE_NAME +" text primary key, "
                + START_STRING + " text, "
                + END_STRING + " text,"
                + START_LONG + " integer,"
                + END_LONG + " integer)";

        public static final String DELETE = "delete from " + TABLE;
    }

    public static final class WarnImage {
        public static final String TABLE = "warning_image";
        public static final String FILE_NAME = "file";
        public static final String START_STRING = "startTime";
        public static final String START_LONG = "startTimeLong";

        private static final String CREATE = "create table "+TABLE+" ("
                + FILE_NAME +" text primary key, "
                + START_STRING + " text, "
                + START_LONG + " integer)";

        public static final String DELETE = "delete from " + TABLE;
    }

    public static final class FocusImage {
        public static final String TABLE = "focus_image";
        public static final String FILE_NAME = "file";
        public static final String START_STRING = "startTime";
        public static final String START_LONG = "startTimeLong";

        private static final String CREATE = "create table "+TABLE+" ("
                + FILE_NAME +" text primary key, "
                + START_STRING + " text, "
                + START_LONG + " integer)";

        public static final String DELETE = "delete from " + TABLE;
    }

    public static final class Face {
        public static final String TABLE = "face";
        public static final String NAME = "name";
        public static final String DIRECTION = "direction";
        public static final String FOCUS = "focus";
        public static final String FEATURE = "eigenvalue";

        private static final String CREATE = "create table "+TABLE+" ("
                + NAME +" text , "
                + DIRECTION + " integer,"
                + FOCUS + " boolean,"
                + FEATURE + " blob)";

        public static final String DELETE = "delete from " + TABLE;
    }

    public DataBaseHelper(Context context) {
        super(context, DB_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(History.CREATE);
        db.execSQL(HistoryQuantum.CREATE);
        db.execSQL(Warning.CREATE);
        db.execSQL(WarnImage.CREATE);
        db.execSQL(FocusImage.CREATE);
        db.execSQL(Face.CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogTool.d(TAG,"The oldVersion = "+oldVersion+", newVersion = "+newVersion);
    }
}
