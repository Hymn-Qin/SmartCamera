package com.zzdc.abb.smartcamera.util;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogTool {
    private static final String TAG = "LogTool";
    private static final String APP_TAG = "smartcamera.";
    private static final String LOG_FOLDER = "com.zzdc.zhipengli.smartcamera";
    private static final String LOG_PATH = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator
            + LOG_FOLDER;
    private static final String LOG_FILE = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator
            + LOG_FOLDER + File.separator + "log.txt";

    private static final int LOG_VERBOSE = 0;
    private static final int LOG_DEBUG = 1;
    private static final int LOG_INFO = 2;
    private static final int LOG_WARN = 3;
    private static final int LOG_ERROR = 4;

    private static final int LOG_LEVEL = LOG_VERBOSE;
    private static boolean LOG_TO_FILE = false;

    public static void v(String tag, String msg) {
        v(tag, msg, null);
    }

    public static void v(String tag, String msg, Throwable tr) {
        tag = appendTag(tag);
        if (LOG_LEVEL <= LOG_VERBOSE) {
            Log.v(tag, msg, tr);
        }
        if (LOG_TO_FILE) {
            logtoFile("V", tag, msg, tr);
        }
    }

    public static void d(String tag, String msg) {
        d(tag, msg, null);
    }

    public static void d(String tag, String msg, Throwable tr) {
        tag = appendTag(tag);
        if (LOG_LEVEL <= LOG_DEBUG) {
            Log.d(tag, msg, tr);
        }
        if (LOG_TO_FILE) {
            logtoFile("D", tag, msg, tr);
        }
    }

    public static void i(String tag, String msg) {
        i(tag, msg, null);
    }

    public static void i(String tag, String msg, Throwable tr) {
        tag = appendTag(tag);
        if (LOG_LEVEL <= LOG_INFO) {
            Log.i(tag, msg, tr);
        }
        if (LOG_TO_FILE) {
            logtoFile("I", tag, msg, tr);
        }
    }

    public static void w(String tag, String msg) {
        w(tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable tr) {
        tag = appendTag(tag);
        if (LOG_LEVEL <= LOG_WARN) {
            Log.w(tag, msg, tr);
        }
        if (LOG_TO_FILE) {
            logtoFile("W", tag, msg, tr);
        }
    }

    public static void e(String tag, String msg) {
        e(tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable tr) {
        tag = appendTag(tag);
        if (LOG_LEVEL <= LOG_ERROR) {
            Log.e(tag, msg, tr);
        }
        if (LOG_TO_FILE) {
            logtoFile("E", tag, msg, tr);
        }
    }

    public static void callStack(String tag, String msg) {
        tag = appendTag(tag);
        StringBuilder sb = new StringBuilder();
        sb.append(msg).append("\n").append(track());
        Log.d(tag, sb.toString());
        if (LOG_TO_FILE) {
            logtoFile("D", tag, msg, null);
        }
    }

    private static String track() {
        StackTraceElement[] straceTraceElements = Thread.currentThread()
                .getStackTrace();
        int length = straceTraceElements.length;
        String file = "";
        int line = 0;
        String method = "";
        StringBuilder sb = new StringBuilder();
        for (int i = 4; i < length; i++) {
            file = straceTraceElements[i].getFileName();
            line = straceTraceElements[i].getLineNumber();
            method = straceTraceElements[i].getMethodName();
            sb.append("\t").append(file).append(":").append(method).append("(")
                    .append(line).append(")\n");
        }
        return sb.toString();
    }

    /**
     * open log file and write to file
     * 
     * @return
     **/
    private static void logtoFile(String mylogtype, String tag, String text,
                                  Throwable tr) {
        Date nowtime = new Date();
        SimpleDateFormat myLogSdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder logText = new StringBuilder();
        logText.append(myLogSdf.format(nowtime)).append("\t");
        logText.append(mylogtype).append("\t");
        logText.append(tag).append("\t");
        logText.append(text).append("\n");
        logText.append(Log.getStackTraceString(tr));

        File logFolder = new File(LOG_PATH);
        if (!logFolder.exists()) {
            Log.d(TAG, "Create log folder " + logFolder.mkdirs());
        }
        File logFile = new File(LOG_FILE);
        if (!logFile.exists()) {
            try {
                Log.d(TAG, "Create log file " + logFile.createNewFile());
            } catch (IOException e) {
                Log.d(TAG, "", e);
            }
        }

        try {
            OutputStreamWriter writer = null;
            writer = new OutputStreamWriter(new FileOutputStream(logFile, true),
                    "UTF-8");
            writer.write(logText.toString());
            writer.close();
        } catch (IOException e1) {
            Log.e(TAG, "", e1);
        }
    }

    private static String appendTag(String tag) {
        return APP_TAG + tag;
    }

    public static void setLogToFile(boolean logToFile) {
        LOG_TO_FILE = logToFile;
    }
}
