package com.zzdc.abb.smartcamera.util;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

public class LogTool {
    private static final String TAG = "LogTool";
    private static final String APP_TAG = "smartcamera.";
    private static final String LOG_FOLDER = "com.zzdc.abb.smartcamera";
    private static final String LOG_PATH = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator
            + LOG_FOLDER;
    private static final String LOG_FILE = LOG_PATH + File.separator + "log.txt";

    private static final int LOG_VERBOSE = 0;
    private static final int LOG_DEBUG = 1;
    private static final int LOG_INFO = 2;
    private static final int LOG_WARN = 3;
    private static final int LOG_ERROR = 4;

    private static final int LOG_LEVEL = LOG_DEBUG;
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
            logToFile("V", tag, msg, tr);
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
            logToFile("D", tag, msg, tr);
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
            logToFile("I", tag, msg, tr);
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
            logToFile("W", tag, msg, tr);
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
            logToFile("E", tag, msg, tr);
        }
    }

    public static void callStack(String tag, String msg) {
        tag = appendTag(tag);
        String s = msg + "\n" + track();
        Log.d(tag, s);

        if (LOG_TO_FILE) {
            logToFile("D", tag, s, null);
        }
    }

    private static String appendTag(String tag) {
        return APP_TAG + tag;
    }

    private static String track() {
        StackTraceElement[] straceTraceElements = Thread.currentThread()
                .getStackTrace();
        int length = straceTraceElements.length;
        String file;
        int line;
        String method;
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

    private static final ThreadLocal<SimpleDateFormat> FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        }
    };
    private static void logToFile(String logType, String tag, String text, Throwable tr) {
        String log = FORMAT.get().format(new Date()) + "\t"
                + logType + "/" + tag + "\t"
                + text + "\n"
                + Log.getStackTraceString(tr);
        writeLogToFile(log);
    }

    private static LinkedBlockingQueue<String> mLogQueue = new LinkedBlockingQueue<>();
    private static void writeLogToFile(String log) {
        mLogQueue.offer(log);
        startWriterThreadIfNot();
    }

    private static void startWriterThreadIfNot() {
        if (!mWriterThreadRunning) {
            if(getLogFile() != null) {
                mWriterThreadRunning = true;
                mWriteThread.start();
            }
        }
    }

    private static File mLogFile = null;
    private static File getLogFile() {
        if (mLogFile == null) {
            File logFolder = new File(LOG_PATH);
            if (!logFolder.exists()) {
                try {
                    if (logFolder.mkdirs()) {
                        Log.d(TAG, "Create log folder succeed. " + LOG_PATH);
                    } else {
                        Log.w(TAG, "Create log folder failed. " + LOG_PATH);
                        return null;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Create log folder with exception, ", e);
                    return null;
                }
            }
            File logFile = new File(LOG_FILE);
            try {
                if (logFile.createNewFile()) {
                    Log.d(TAG, "Create log file: " + LOG_FILE);
                } else {
                    Log.d(TAG, "Log file: " + LOG_FILE + " has exist already.");
                }
            } catch (IOException e) {
                Log.w(TAG, "Create log file with exception, ", e);
                return null;
            }
            mLogFile = logFile;
        }
        return mLogFile;
    }

    private static boolean mWriterThreadRunning = false;
    private static Thread mWriteThread = new Thread("LogTool write thread") {
        @Override
        public void run() {
            super.run();
            while (mWriterThreadRunning) {
                try {
                    String log = mLogQueue.take();
                    FileOutputStream oStream = new FileOutputStream(mLogFile, true);
                    OutputStreamWriter writer = new OutputStreamWriter(oStream, "UTF-8");
                    writer.write(log);
                    writer.close();
                } catch (InterruptedException | IOException e) {
                    Log.w(TAG, "Writer log with exception, ", e);
                }
            }
        }
    };
}
