package com.zzdc.abb.smartcamera.controller;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.MediaPlayer;
import android.os.FileObserver;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.zzdc.abb.smartcamera.info.MediaDurationInfo;
import com.zzdc.abb.smartcamera.util.DataBaseHelper;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class MediaStorageManager {
    private static final String TAG = MediaStorageManager.class.getSimpleName();

    private static final boolean RECORD_ON_SD = true;
    private static final String LOCAL_PATH = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
    private static final String MEDIA_FOLDER = "com.zzdc.abb.smartcamera";
    private static final String HISTORY_FOLDER = "history";
    private static final String WARNING_FOLDER = "warning";

    private static final String HISTORY_PREFIX = "History_";
    private static final String WARNING_PREFIX = "Warning_";
    private static final String NAME_EXT = ".mp4";

    private static final int QUANTUM_INTERVAL = 10;

    private SQLiteDatabase mDb = null;

    private volatile boolean mReady = false;
    private MediaPlayer mMediaPlayer = new MediaPlayer();
    private String mStoragePath = null;
    private String mMediaPath = null;
    private String mHistoryPath = null;
    private String mWarningPath = null;
    private FolderObserver mHistoryObserver = null;
    private FolderObserver mWarningObserver = null;
    private TemporaryMediaObserver mTmpObserver = null;

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        }
    };

    private static MediaStorageManager mInstance = new MediaStorageManager();

    private MediaStorageManager() {
        start();
    }

    public static MediaStorageManager getInstance() {
        return mInstance;
    }

    public boolean isReady() {
        return mReady;
    }

    public synchronized void start() {
        if (!mReady && prepareStorageFolders()) {
            mReady = true;
            resetDatabase();
            mHistoryObserver = new FolderObserver(mHistoryPath);
            mHistoryObserver.startWatching();
            mWarningObserver = new FolderObserver(mWarningPath);
            mWarningObserver.startWatching();
            mTmpObserver = new TemporaryMediaObserver(mMediaPath);
            mTmpObserver.startWatching();
            mDeletOverdueThread.setDaemon(true);
            mDeletOverdueThread.start();
            mMediaAddedThread.setDaemon(true);
            mMediaAddedThread.start();
            mMediaRemovedThread.setDaemon(true);
            mMediaRemovedThread.start();
        }
    }

    public synchronized void stop() {
        if (RECORD_ON_SD) {
            mReady = false;
            mHistoryObserver.stopWatching();
            mHistoryObserver = null;
            mWarningObserver.stopWatching();
            mWarningObserver = null;
            mTmpObserver.stopWatching();
            mTmpObserver = null;
            mDeletOverdueThread.interrupt();
            mMediaAddedThread.interrupt();
            mMediaRemovedThread.interrupt();
            mStoragePath = null;
            mMediaPath = null;
            mHistoryPath = null;
            mWarningPath = null;
        }
    }

    public String generateHistoryMediaFileName() {
        if (mReady) {
            return mMediaPath + File.separator + generateFileName(HISTORY_PREFIX);
        } else {
            return null;
        }
    }

    public String generateWarningMediaFileName() {
        if (mReady) {
            return mMediaPath + File.separator + generateFileName(WARNING_PREFIX);
        } else {
            return null;
        }
    }

    public List<MediaDurationInfo> getHistoryDurationInfo() {
        if (mReady) {
            Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.HistoryQuantum.TABLE
                    + " order by " + DataBaseHelper.HistoryQuantum.START_LONG + " asc", null);

            ArrayList<MediaDurationInfo> infos = new ArrayList<>();
            if (cursor != null) {
                int indexStartString = cursor.getColumnIndex(DataBaseHelper.HistoryQuantum.START_STRING);
                int indexEndString = cursor.getColumnIndex(DataBaseHelper.HistoryQuantum.END_STRING);
                int indexStartLong = cursor.getColumnIndex(DataBaseHelper.HistoryQuantum.START_LONG);
                int indexEndLong = cursor.getColumnIndex(DataBaseHelper.HistoryQuantum.END_LONG);

                while (cursor.moveToNext()) {
                    long start = cursor.getLong(indexStartLong);
                    long end = cursor.getLong(indexEndLong);
                    String startString = cursor.getString(indexStartString);
                    String endString = cursor.getString(indexEndString);

                    infos.add(new MediaDurationInfo(start, end));
                }
                cursor.close();
            }
            return infos;
        } else {
            return null;
        }
    }

    public List<MediaDurationInfo> getWarningDurationInfo() {
        if (mReady) {
            Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.Warning.TABLE
                    + " ORDER BY " + DataBaseHelper.Warning.START_LONG + " ASC", null);

            ArrayList<MediaDurationInfo> infos = new ArrayList<>();
            if (cursor != null) {
                int indexStartString = cursor.getColumnIndex(DataBaseHelper.Warning.START_STRING);
                int indexEndString = cursor.getColumnIndex(DataBaseHelper.Warning.END_STRING);
                int indexStartLong = cursor.getColumnIndex(DataBaseHelper.Warning.START_LONG);
                int indexEndLong = cursor.getColumnIndex(DataBaseHelper.Warning.END_LONG);

                while (cursor.moveToNext()) {
                    long start = cursor.getLong(indexStartLong);
                    long end = cursor.getLong(indexEndLong);
                    String startString = cursor.getString(indexStartString);
                    String endString = cursor.getString(indexEndString);

                    infos.add(new MediaDurationInfo(start, end));
                }
                cursor.close();
            }
            return infos;
        } else {
            return null;
        }
    }

    /**
     *
     * @param time seconds
     * @return
     */
    public String getHistoryFile(long time) {
        if (mReady) {
            String file = getHistory(time);
            if(file == null) {
                file = getHistory(time + QUANTUM_INTERVAL);
            }
            return file;
        } else {
            return null;
        }
    }

    private String getHistory(long time) {
        String file = null;

        Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.History.TABLE
                        + " where " + DataBaseHelper.History.START_LONG + " <= " + time
                        + " and " + DataBaseHelper.History.END_LONG + " >= " + time,
                null);

        if (cursor != null) {
            if (cursor.moveToNext()) {
                int indexName = cursor.getColumnIndex(DataBaseHelper.History.FILE_NAME);
                file = cursor.getString(indexName);
            }
            cursor.close();
        }
        return file;
    }

    /**
     *
     * @param time seconds
     * @return
     */
    public String getWarningFile(long time) {
        if (mReady) {
            String file = null;
            Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.Warning.TABLE
                            + " where " + DataBaseHelper.Warning.START_LONG + " <= " + time
                            + " and " + DataBaseHelper.Warning.END_LONG + " >= " + time,
                    null);
            if (cursor != null) {
                if (cursor.moveToNext()) {
                    int indexName = cursor.getColumnIndex(DataBaseHelper.Warning.FILE_NAME);
                    file = cursor.getString(indexName);
                }
                cursor.close();
            }
            return file;
        } else {
            return null;
        }
    }

    public String nextHistoryFile(String absolutePath) {
        if (mReady) {
            long time = getHistoryMediaStartTime(absolutePath);
            Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.History.TABLE
                    + " where " + DataBaseHelper.History.START_LONG + " > " + time
                    + " oder by " + DataBaseHelper.History.START_LONG + " asc", null);
            String nextFile = null;
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int indexName = cursor.getColumnIndex(DataBaseHelper.History.FILE_NAME);
                    nextFile = cursor.getString(indexName);
                }
                cursor.close();
            }
            return nextFile;
        } else {
            return null;
        }
    }

/**
 *
 * @param absolutePath
 * @return return seconds
 */
    public static long getHistoryMediaStartTime(String absolutePath) {
        return getLongTimeAccordName(HISTORY_PREFIX, absolutePath);
    }

    private static String getStringTimeAccordName(String prefix, String name) {
        int start = name.lastIndexOf(prefix) + prefix.length();
        int end = name.lastIndexOf(NAME_EXT);
        return name.substring(start, end);
    }

/**
 * 
 * @param prefix
 * @param name
 * @return seconds
 */
    private static long getLongTimeAccordName(String prefix, String name) {
        String stringTime = getStringTimeAccordName(prefix, name);
        long time = 0;
        try {
            time = TIME_FORMAT.get().parse(stringTime).getTime();
        } catch (ParseException e) {
            LogTool.w(TAG, "Parse time with exception, " + e);
        }
        return time;
    }

    /**
     *
     * @param time seconds
     * @return
     */
    private String getStringTimeAccordLong(long time) {
        return TIME_FORMAT.get().format(new Date(time));
    }

    private String generateFileName(String prefix) {
        Date date = new Date(System.currentTimeMillis());
        return prefix + TIME_FORMAT.get().format(date) + NAME_EXT;
    }

    private boolean prepareStorageFolders() {
        boolean folderReady = false;
        String path = getStoragePath();
        if (path != null) {
            String mediaPath = path + File.separator + MEDIA_FOLDER;
            String historyPath = mediaPath + File.separator + HISTORY_FOLDER;
            String warningPath = mediaPath + File.separator + WARNING_FOLDER;
            LogTool.d(TAG, "Check & Create media folder path = " + path + "\n"
                    + "\t, history path = " + historyPath + "\n"
                    + "\t, warning path = " + warningPath);

            File historyFolder = new File(historyPath);
            if (!historyFolder.exists()) {
                if (historyFolder.mkdirs()) {
                    LogTool.d(TAG, "Create history folder succeed");
                } else {
                    LogTool.w(TAG, "Create history folder failed");
                }
            } else {
                LogTool.d(TAG, "History folder had existed");
            }

            File warningFolder = new File(warningPath);
            if (!warningFolder.exists()) {
                if (warningFolder.mkdirs()) {
                    LogTool.d(TAG, "Create warning folder succeed");
                } else {
                    LogTool.w(TAG, "Create warning folder failed");
                }
            } else {
                LogTool.d(TAG, "Warning folder had existed");
            }

            if (historyFolder.exists() && warningFolder.exists()) {
                mMediaPath = mediaPath;
                mHistoryPath = historyPath;
                mWarningPath = warningPath;
                folderReady = true;
            }
        } else {
            LogTool.w(TAG, "Check & Create media folder path is null");
        }
        return folderReady;
    }

    private String getStoragePath() {
        if (mStoragePath == null) {
            if (RECORD_ON_SD) {
                LogTool.d(TAG, "Store the media files on SD");
                try {
                    Class<?> storageVolumeClass = Class.forName("android.os.storage.StorageVolume");
                    Method getPath = storageVolumeClass.getMethod("getPath");
                    Method isRemovable = storageVolumeClass.getMethod("isRemovable");
                    StorageManager storageManager = (StorageManager) SmartCameraApplication.getContext().getSystemService(Context.STORAGE_SERVICE);
                    @SuppressLint({"NewApi", "LocalSuppress"}) List<StorageVolume> volumes = storageManager.getStorageVolumes();
                    for (StorageVolume volume : volumes) {
                        String path = (String) getPath.invoke(volume);
                        boolean removable = (boolean) isRemovable.invoke(volume);
                        LogTool.d(TAG, "Volume path = " + path + ", removable =" + removable);
                        if (removable) {
                            mStoragePath = path;
                            break;
                        }
                    }
                } catch (Exception e) {
                    LogTool.w(TAG, "Get storage path with exception, ", e);
                }
            } else {
                LogTool.d(TAG, "Store the media files on local");
                mStoragePath = LOCAL_PATH;
            }
        }
        return mStoragePath;
    }

    private void resetDatabase() {
        DataBaseHelper dbHelper = new DataBaseHelper(SmartCameraApplication.getContext());
        mDb = dbHelper.getWritableDatabase();
        resetHistoryMediaInfo();
//        resetWarningMediaInfo();
    }

    private void resetHistoryMediaInfo() {
        mDb.execSQL(DataBaseHelper.History.DELETE);
        List<File> files = getAndSortFiles(mHistoryPath);
        if (null != files && files.size() > 0) {
            for (File file: files) {
                setHistoryFile(file);
            }
            resetHistoryQuantum();
        }
    }

    private void setHistoryFile(File file) {
        String absName = file.getAbsolutePath();
        long longStartTime = getLongTimeAccordName(HISTORY_PREFIX, absName);
        String stringStartTime = getStringTimeAccordName(HISTORY_PREFIX, absName);
        long longEndTime =  longStartTime + getDuration(file);
        String stringEndTime = getStringTimeAccordLong(longEndTime);

        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHelper.History.FILE_NAME,absName);
        contentValues.put(DataBaseHelper.History.START_STRING,stringStartTime);
        contentValues.put(DataBaseHelper.History.END_STRING,stringEndTime);
        contentValues.put(DataBaseHelper.History.START_LONG,longStartTime);
        contentValues.put(DataBaseHelper.History.END_LONG,longEndTime);
        mDb.insert(DataBaseHelper.History.TABLE, null, contentValues);
    }

    private void resetWarningMediaInfo() {
        mDb.execSQL(DataBaseHelper.Warning.DELETE);
        List<File> files = getAndSortFiles(mWarningPath);
        for (File file: files) {
            setWarningFile(file);
        }
    }

    private void setWarningFile(File file) {
        String absName = file.getAbsolutePath();
        String rltName = file.getName();
        long longStartTime = getLongTimeAccordName(WARNING_PREFIX, rltName);
        String stringStartTime = getStringTimeAccordName(WARNING_PREFIX, rltName);
        long longEndTime =  longStartTime + getDuration(file);
        String stringEndTime = getStringTimeAccordLong(longEndTime);

        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHelper.Warning.FILE_NAME,absName);
        contentValues.put(DataBaseHelper.Warning.START_STRING,stringStartTime);
        contentValues.put(DataBaseHelper.Warning.END_STRING,stringEndTime);
        contentValues.put(DataBaseHelper.Warning.START_LONG,longStartTime);
        contentValues.put(DataBaseHelper.Warning.END_LONG,longEndTime);
        mDb.insert(DataBaseHelper.Warning.TABLE, null, contentValues);
    }

    private int removeHistoryFile(String absolutePath) {
        String stringTime = getStringTimeAccordName(HISTORY_PREFIX, absolutePath);
        return mDb.delete(DataBaseHelper.History.TABLE,
            DataBaseHelper.History.START_STRING + "=?",
                new String[] {stringTime});
    }

    private void resetHistoryQuantum() {
        mDb.execSQL(DataBaseHelper.HistoryQuantum.DELETE);
        Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.History.TABLE
                + " ORDER BY " + DataBaseHelper.History.START_LONG + " ASC", null);

        if (cursor != null) {
            long preStart = 0;
            String preStartString = null;
            int longStartIndex = cursor.getColumnIndex(DataBaseHelper.History.START_LONG);
            int longEndIndex = cursor.getColumnIndex(DataBaseHelper.History.END_LONG);
            int stringStartIndex = cursor.getColumnIndex(DataBaseHelper.History.START_STRING);
            int stringEndIndex = cursor.getColumnIndex(DataBaseHelper.History.END_STRING);

            cursor.moveToFirst();
            preStartString = cursor.getString(stringStartIndex);
            preStart = cursor.getLong(longStartIndex);

            for(int i=0;i<(cursor.getCount());i++) {
                cursor.moveToPosition(i);
                String endString = cursor.getString(stringEndIndex);
                long end = cursor.getLong(longEndIndex);

                if (i<(cursor.getCount()-1)) {
                    cursor.moveToNext();
                    String compareStartString = cursor.getString(stringStartIndex);
                    long compareStartTime = cursor.getLong(longStartIndex);
                    if((compareStartTime - end)>QUANTUM_INTERVAL*1000) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(DataBaseHelper.HistoryQuantum.START_STRING, preStartString);
                        contentValues.put(DataBaseHelper.HistoryQuantum.END_STRING, endString);
                        contentValues.put(DataBaseHelper.HistoryQuantum.START_LONG, preStart);
                        contentValues.put(DataBaseHelper.HistoryQuantum.END_LONG, end);
                        mDb.insert(DataBaseHelper.HistoryQuantum.TABLE, null, contentValues);

                        preStartString = compareStartString;
                        preStart = compareStartTime;
                    }
                } else {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(DataBaseHelper.HistoryQuantum.START_STRING, preStartString);
                    contentValues.put(DataBaseHelper.HistoryQuantum.END_STRING, endString);
                    contentValues.put(DataBaseHelper.HistoryQuantum.START_LONG, preStart);
                    contentValues.put(DataBaseHelper.HistoryQuantum.END_LONG, end);
                    mDb.insert(DataBaseHelper.HistoryQuantum.TABLE, null, contentValues);
                }
            }
            cursor.close();
        }
    }

    private long getDuration(File file) {
        long second = 0;
        try {
            mMediaPlayer.setDataSource(file.getAbsolutePath());
            mMediaPlayer.prepare();
            second = mMediaPlayer.getDuration();
            mMediaPlayer.reset();
        } catch (Exception e) {
            LogTool.w(TAG, "Get video duration with exception, ", e);
        }
        return second;
    }

    private int removeWarningFile(String absolutePath) {
        String stringTime = getStringTimeAccordName(HISTORY_PREFIX, absolutePath);
        return mDb.delete(DataBaseHelper.Warning.TABLE,
                DataBaseHelper.Warning.START_STRING + "=?",
                new String[] {stringTime});
    }

    private List<File> getAndSortFiles (String path){
        File[] allFiles = new File(path).listFiles();
        List<File> files = new LinkedList<>();
        Collections.addAll(files, allFiles);

        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });
        return files;
    }

    private Thread mDeletOverdueThread = new Thread("Delete Old Media file thread") {
        private static final int MEMORY_THRESHOLD = 300;

        @Override
        public void run() {
            super.run();
            while (mReady) {
                try {
                    long exSize = MEMORY_THRESHOLD - getAvailableSpace();
                    if (exSize > 0) {
                        List<File> files = getAndSortFiles(mHistoryPath);
                        long freeSize = 0;
                        for (File file : files) {
                            String name = file.getName();
                            long length = file.length();
                            if (file.delete()) {
                                freeSize += length;
                                LogTool.d(TAG, "Delete file succeed, file: " + name + ", file size = " + length + ", free size = " + freeSize);
                            } else {
                                LogTool.w(TAG, "Delete file failed, file: " + name);
                            }
                            if (freeSize > exSize) {
                                break;
                            }
                        }
                    }
                    sleep(60 * 1000);
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "Delete old media thread exception, ", e);
                }
            }
        }

        private long getAvailableSpace() {
            File storageFolder = new File(getStoragePath());
            long usableSpace = storageFolder.getUsableSpace() / 1024 / 1024;
            long totalSpace = storageFolder.getTotalSpace() / 1024 / 1024;
            LogTool.d(TAG, "getUsableSpace = " + usableSpace + " ,totalSpace = " + totalSpace);
            return usableSpace;
        }
    };

    private LinkedBlockingQueue<String> mAddMediaQueu = new LinkedBlockingQueue<>();
    private Thread mMediaAddedThread = new Thread("Media-added-Thread") {
        @Override
        public void run() {
            super.run();
            mAddMediaQueu.clear();
            while (mReady) {
                try {
                    String file = mAddMediaQueu.take();
                    File f = new File(file);
                    if (file.contains(HISTORY_PREFIX)) {
                        setHistoryFile(f);
                        resetHistoryQuantum();
                    } else if (file.contains(WARNING_PREFIX)) {
                        setWarningFile(f);
                    } else {
                        LogTool.w(TAG, "Find unknow media file: " + file);
                    }
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "Take media file from add queue with exception, " + e);
                }
            }
        }
    };

    private LinkedBlockingQueue<String> mRemoveMediaQueu = new LinkedBlockingQueue<>();
    private Thread mMediaRemovedThread = new Thread("Media-removed-Thread") {
        @Override
        public void run() {
            super.run();
            mRemoveMediaQueu.clear();
            while (mReady) {
                try {
                    String file = mRemoveMediaQueu.take();
                    if (file.contains(HISTORY_PREFIX)) {
                        if (removeHistoryFile(file) > 0) {
                            resetHistoryQuantum();
                        }
                    } else if (file.contains(WARNING_PREFIX)) {
                        removeWarningFile(file);
                    } else {
                        LogTool.w(TAG, "Remove unknow media file: " + file);
                    }
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "Take media file from remove queue with exception, " + e);
                }
            }
        }
    };

    private class FolderObserver extends FileObserver {
        private static final int MASK = FileObserver.MOVED_FROM | FileObserver.MOVED_TO
                | FileObserver.DELETE | FileObserver.CREATE | FileObserver.CLOSE_WRITE;
        private String mPath;

        FolderObserver(String path) {
            super(path, MASK);
            mPath = path;
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            LogTool.d(TAG, "Observe event:" + mPath + ", event: " + event + ", path: " + path);
            switch (event) {
                case MOVED_FROM:
                case DELETE:
                    mRemoveMediaQueu.offer(mPath + File.separator + path);
                    break;
                case MOVED_TO:
                case CLOSE_WRITE:
                    mAddMediaQueu.offer(mPath + File.separator + path);
                    break;
                case CREATE:
                    break;
                default:
            }
        }
    }

    private class TemporaryMediaObserver extends FileObserver {
        private static final int MASK = FileObserver.CLOSE_WRITE;

        public TemporaryMediaObserver(String path) {
            super(path, MASK);
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            if (path == null) {
                return;
            }
            File fSrc = new File(mMediaPath+File.separator+path);
            String name = fSrc.getName();
            if (name.contains(HISTORY_PREFIX)) {
                File fDst = new File(mHistoryPath + File.separator + name);
                if (fSrc.renameTo(fDst)) {
                    LogTool.d(TAG, "Move history media to history folder succeed, file: " + name);
                } else {
                    LogTool.w(TAG, "Move history media to history folder failed, file: " + name);
                }
            } else if (name.contains(WARNING_PREFIX)) {
                File fDst = new File(mWarningPath + File.separator + name);
                if (fSrc.renameTo(fDst)) {
                    LogTool.d(TAG, "Move warning media to warning folder succeed, file: " + name);
                } else {
                    LogTool.w(TAG, "Move warning media to warning folder failed, file: " + name);
                }
            } else {
                LogTool.w(TAG, "Unknow type file created.");
            }
        }
    }
}

