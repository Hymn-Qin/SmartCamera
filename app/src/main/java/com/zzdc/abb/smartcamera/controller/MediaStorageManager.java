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
import android.support.annotation.Nullable;

import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.zzdc.abb.smartcamera.FaceFeature.FaceFRBean;
import com.zzdc.abb.smartcamera.info.MediaDurationInfo;
import com.zzdc.abb.smartcamera.util.DataBaseHelper;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import java.io.File;
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
    private static final String HIST_MEDIA_FOLDER = "hist_media";
    private static final String WARN_MEDIA_FOLDER = "warn_media";
    private static final String WARN_IMAGE_FOLDER = "warn_image";
    private static final String FOCUS_IMAGE_FOLDER = "focus_image";

    private static final String HIST_MEDIA_PREFIX = "HistMedia_";
    private static final String WARN_MEDIA_PREFIX = "WarnMedia_";
    private static final String WARN_IMAGE_PREFIX = "WarnImage_";
    private static final String FOCUS_IMAGE_PREFIX = "FocusImage_";

    private static final String VIDEO_EXT = ".mp4";
    private static final String IMAGE_EXT = ".jpg";

    private static final int QUANTUM_INTERVAL = 10;

    private SQLiteDatabase mDb;

    private volatile boolean mReady = false;
    private MediaPlayer mMediaPlayer = new MediaPlayer();
    private String mStoragePath = null;
    private String mMediaPath = null;
    private String mHistMediaPath = null;
    private String mWarnMediaPath = null;
    private String mWarnImagePath = null;
    private String mFocusImagePath = null;

    private FolderObserver mHistMediaObserver = null;
    private FolderObserver mWarnMediaObserver = null;
    private FolderObserver mWarnImageObserver = null;
    private FolderObserver mFocusImageObserver = null;
    private TemporaryMediaObserver mTmpObserver = null;

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        }
    };

    private static MediaStorageManager mInstance = new MediaStorageManager();

    private MediaStorageManager() {
        DataBaseHelper dbHelper = new DataBaseHelper(SmartCameraApplication.getContext());
        mDb = dbHelper.getWritableDatabase();
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

            mDatabaseOperateThread.setDaemon(true);
            mDatabaseOperateThread.start();
            mDatabaseOperateQueue.offer(new DatabaseOperateData(DatabaseOperateData.RESET, null));
//            mMediaAddedThread.setDaemon(true);
//            mMediaAddedThread.start();
//            mMediaRemovedThread.setDaemon(true);
//            mMediaRemovedThread.start();
            mDeletOverdueThread.setDaemon(true);
            mDeletOverdueThread.start();

            mHistMediaObserver = new FolderObserver(mHistMediaPath);
            mHistMediaObserver.startWatching();
            mWarnMediaObserver = new FolderObserver(mWarnMediaPath);
            mWarnMediaObserver.startWatching();
            mWarnImageObserver = new FolderObserver(mWarnImagePath);
            mWarnImageObserver.startWatching();
            mFocusImageObserver = new FolderObserver(mFocusImagePath);
            mFocusImageObserver.startWatching();
            mTmpObserver = new TemporaryMediaObserver(mMediaPath);
            mTmpObserver.startWatching();
        }
    }

    public synchronized void stop() {
        if (RECORD_ON_SD) {
            mReady = false;

            mTmpObserver.stopWatching();
            mTmpObserver = null;
            mHistMediaObserver.stopWatching();
            mHistMediaObserver = null;
            mWarnMediaObserver.stopWatching();
            mWarnMediaObserver = null;
            mWarnImageObserver.stopWatching();
            mWarnImageObserver = null;
            mFocusImageObserver.stopWatching();
            mFocusImageObserver = null;

            mDeletOverdueThread.interrupt();
            mDatabaseOperateThread.interrupt();
//            mMediaAddedThread.interrupt();
//            mMediaRemovedThread.interrupt();

            mStoragePath = null;
            mMediaPath = null;
            mHistMediaPath = null;
            mWarnMediaPath = null;
            mWarnImagePath = null;
            mFocusImagePath = null;
        }
    }

    public String generateHistoryMediaFileName() {
        if (mReady) {
            return mMediaPath + File.separator + generateFileName(HIST_MEDIA_PREFIX, VIDEO_EXT);
        } else {
            return null;
        }
    }

    public String generateWarningMediaFileName() {
        if (mReady) {
            return mMediaPath + File.separator + generateFileName(WARN_MEDIA_PREFIX, VIDEO_EXT);
        } else {
            return null;
        }
    }

    public String generateWarningImageFileName(String warningMediaFileName) {
        if (mReady) {
            String time = getStringTimeAccordName(WARN_MEDIA_PREFIX, warningMediaFileName, VIDEO_EXT);
            return mMediaPath + File.separator + WARN_IMAGE_PREFIX + time + IMAGE_EXT;
        } else {
            return null;
        }
    }

    public String generateFocusImageFileName() {
        if (mReady) {
            return mMediaPath + File.separator + generateFileName(FOCUS_IMAGE_PREFIX, IMAGE_EXT);
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
                int indexStartLong = cursor.getColumnIndex(DataBaseHelper.HistoryQuantum.START_LONG);
                int indexEndLong = cursor.getColumnIndex(DataBaseHelper.HistoryQuantum.END_LONG);
                while (cursor.moveToNext()) {
                    long start = cursor.getLong(indexStartLong);
                    long end = cursor.getLong(indexEndLong);
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
                int indexStartLong = cursor.getColumnIndex(DataBaseHelper.Warning.START_LONG);
                int indexEndLong = cursor.getColumnIndex(DataBaseHelper.Warning.END_LONG);

                while (cursor.moveToNext()) {
                    long start = cursor.getLong(indexStartLong);
                    long end = cursor.getLong(indexEndLong);
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
     * @return the history file name
     */
    public String getHistoryFile(long time) {
        if (mReady) {
            String file = getHistory(time);
            if(file == null) {
                file = getHistory(time + QUANTUM_INTERVAL*1000);
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
            if (cursor.moveToFirst()) {
                int indexName = cursor.getColumnIndex(DataBaseHelper.History.FILE_NAME);
                file = cursor.getString(indexName);
            }
            cursor.close();
        }
        LogTool.d(TAG,"Get history file  = "+file);
        return file;
    }

    /**
     *
     * @param time seconds
     * @return the warning file name
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

    /**
     *
     * @param time second
     * @return the warning image file name
     */
    public String getWarningImageFile(long time) {
        if (mReady) {
            String file = null;
            Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.WarnImage.TABLE
                            + " where " + DataBaseHelper.WarnImage.START_LONG + " = " + time,
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

    /**
     *
     * @param time seconds
     * @return the focus image file name
     */
    public String getFocusImageFile(long time) {
        if (mReady) {
            String file = null;
            Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.FocusImage.TABLE
                            + " where " + DataBaseHelper.FocusImage.START_LONG + " = " + time,
                    null);
            if (cursor != null) {
                if (cursor.moveToNext()) {
                    int indexName = cursor.getColumnIndex(DataBaseHelper.FocusImage.FILE_NAME);
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
                    + " order by " + DataBaseHelper.History.START_LONG + " asc", null);
            String nextFile = null;
            if (cursor != null) {
                if (cursor.moveToFirst()) {
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

    public void saveFaceData(List<FaceFRBean> datas) {
        for (FaceFRBean face: datas) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(DataBaseHelper.Face.NAME,face.getmName());
            contentValues.put(DataBaseHelper.Face.DIRECTION, face.getmDirection());
            contentValues.put(DataBaseHelper.Face.FOCUS, face.ismFocus());
            contentValues.put(DataBaseHelper.Face.FEATURE,face.getAFRFace().getFeatureData());
            mDb.insert(DataBaseHelper.Face.TABLE, null, contentValues);
        }
    }

    public void deleteFaceData(String name) {
        int r = mDb.delete(DataBaseHelper.Face.TABLE, DataBaseHelper.Face.NAME + "=?",new String[]{name});
        LogTool.d(TAG,"deleteFaceData, delete  "+ r + " row(s)");
    }

    public List<FaceFRBean> getFaceDatas() {
        Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.Face.TABLE
                        + " order by " + DataBaseHelper.Face.NAME,
                null);
        ArrayList<FaceFRBean> faces = new ArrayList<>();
        if (cursor != null) {
            if (cursor.moveToNext()) {
                int indexName = cursor.getColumnIndex(DataBaseHelper.Face.NAME);
                int indexDirection = cursor.getColumnIndex(DataBaseHelper.Face.DIRECTION);
                int indexFocus = cursor.getColumnIndex(DataBaseHelper.Face.FOCUS);
                int indexFeature = cursor.getColumnIndex(DataBaseHelper.Face.FEATURE);
                FaceFRBean face = new FaceFRBean();
                face.setmName(cursor.getString(indexName));
                face.setmDirection(cursor.getInt(indexDirection));
                face.setmFocus(cursor.getInt(indexFocus) == 1);
                face.setAFRFace(new AFR_FSDKFace(cursor.getBlob(indexFeature)));
                faces.add(face);
            }
            cursor.close();
        }
        return faces;
    }

    public void setFocus(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHelper.Face.FOCUS, true);
        mDb.update(DataBaseHelper.Face.TABLE, contentValues, DataBaseHelper.Face.NAME + "=?", new String[] {name});
    }

    public void cancelFocus(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHelper.Face.FOCUS, false);
        mDb.update(DataBaseHelper.Face.TABLE, contentValues, DataBaseHelper.Face.NAME + "=?", new String[] {name});
    }

    public List<FaceFRBean> getFocus() {
        Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.Face.TABLE
                        + " where " + DataBaseHelper.Face.FOCUS + "=1" + " order by " + DataBaseHelper.Face.NAME,
                null);
        ArrayList<FaceFRBean> faces = new ArrayList<>();
        if (cursor != null) {
            if (cursor.moveToNext()) {
                int indexName = cursor.getColumnIndex(DataBaseHelper.Face.NAME);
                int indexDirection = cursor.getColumnIndex(DataBaseHelper.Face.DIRECTION);
                int indexFocus = cursor.getColumnIndex(DataBaseHelper.Face.FOCUS);
                int indexFeature = cursor.getColumnIndex(DataBaseHelper.Face.FEATURE);
                FaceFRBean face = new FaceFRBean();
                face.setmName(cursor.getString(indexName));
                face.setmDirection(cursor.getInt(indexDirection));
                face.setmFocus(cursor.getInt(indexFocus) == 1);
                face.setAFRFace(new AFR_FSDKFace(cursor.getBlob(indexFeature)));
                faces.add(face);
            }
            cursor.close();
        }
        return faces;
    }

    /**
     *
     * @param absolutePath the history media file name
     * @return return seconds
     */
    public static long getHistoryMediaStartTime(String absolutePath) {
        return getLongTimeAccordName(HIST_MEDIA_PREFIX, absolutePath, VIDEO_EXT);
    }

    public static long getWarningMediaStartTime(String absolutePath) {
        return getLongTimeAccordName(WARN_MEDIA_PREFIX, absolutePath, VIDEO_EXT);
    }

    private static String getStringTimeAccordName(String prefix, String name, String ext) {
        int start = name.lastIndexOf(prefix) + prefix.length();
        int end = name.lastIndexOf(ext);
        return name.substring(start, end);
    }

    /**
     *
     * @param prefix the file name prefix
     * @param name the file name
     * @param ext the ext name
     * @return seconds
     */
    private static long getLongTimeAccordName(String prefix, String name, String ext) {
        String stringTime = getStringTimeAccordName(prefix, name, ext);
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
     * @return the time in string
     */
    private String getStringTimeAccordLong(long time) {
        return TIME_FORMAT.get().format(new Date(time));
    }

    private String generateFileName(String prefix, String ext) {
        Date date = new Date(System.currentTimeMillis());
        return prefix + TIME_FORMAT.get().format(date) + ext;
    }

    private boolean prepareStorageFolders() {
        boolean folderReady = false;
        String path = getStoragePath();
        if (path != null) {
            String mediaPath = path + File.separator + MEDIA_FOLDER;
            String historyPath = mediaPath + File.separator + HIST_MEDIA_FOLDER;
            String warningPath = mediaPath + File.separator + WARN_MEDIA_FOLDER;
            String warnImagePath = mediaPath + File.separator + WARN_IMAGE_FOLDER;
            String focusImagePath = mediaPath + File.separator + FOCUS_IMAGE_FOLDER;
            LogTool.d(TAG, "Check & Create media folder path = " + path + "\n"
                    + "\t, history path = " + historyPath + "\n"
                    + "\t, warning path = " + warningPath + "\n"
                    + "\t, warn image path = " + warnImagePath + "\n"
                    + "\t, focus image path = " + focusImagePath);

            File historyFolder = createFolder(historyPath);
            File warningFolder = createFolder(warningPath);
            File warnImageFolder = createFolder(warnImagePath);
            File focusImageFolder = createFolder(focusImagePath);

            if (historyFolder.exists() && warningFolder.exists() && warnImageFolder.exists() && focusImageFolder.exists()) {
                mMediaPath = mediaPath;
                mHistMediaPath = historyPath;
                mWarnMediaPath = warningPath;
                mWarnImagePath = warnImagePath;
                mFocusImagePath = focusImagePath;
                folderReady = true;
            }
        } else {
            LogTool.w(TAG, "Check & Create media folder path is null");
        }
        return folderReady;
    }

    private File createFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) {
            if (folder.mkdirs()) {
                LogTool.d(TAG, "Create folder succeed: " + folderPath);
            } else {
                LogTool.w(TAG, "Create folder failed: " + folderPath);
            }
        } else {
            LogTool.d(TAG, "Folder had existed: " + folderPath);
        }
        return folder;
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

    private int removeHistoryFile(String absolutePath) {
        String stringTime = getStringTimeAccordName(HIST_MEDIA_PREFIX, absolutePath, VIDEO_EXT);
        return mDb.delete(DataBaseHelper.History.TABLE,
                DataBaseHelper.History.START_STRING + "=?",
                new String[] {stringTime});
    }

    private void removeWarningFile(String absolutePath) {
        String stringTime = getStringTimeAccordName(WARN_MEDIA_PREFIX, absolutePath, VIDEO_EXT);
        mDb.delete(DataBaseHelper.Warning.TABLE,
                DataBaseHelper.Warning.START_STRING + "=?",
                new String[] {stringTime});
    }

    private void removeWarnImageFile(String absolutePath) {
        String stringTime = getStringTimeAccordName(WARN_IMAGE_PREFIX, absolutePath, IMAGE_EXT);
        mDb.delete(DataBaseHelper.WarnImage.TABLE,
                DataBaseHelper.WarnImage.START_STRING + "=?",
                new String[] {stringTime});
    }

    private void removeFocusImageFile(String absolutePath) {
        String stringTime = getStringTimeAccordName(FOCUS_IMAGE_PREFIX, absolutePath, IMAGE_EXT);
        mDb.delete(DataBaseHelper.FocusImage.TABLE,
                DataBaseHelper.FocusImage.START_STRING + "=?",
                new String[] {stringTime});
    }

    private List<File> getAndSortFiles (String path){
        List<File> files = null;
        if (null != path) {
            File file = new File(path);
            if (null != file) {
                File[] allFiles = file.listFiles();
                files = new LinkedList<>();
                Collections.addAll(files, allFiles);

                Collections.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }
                });
                return files;
            }
        }
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
                        List<File> files = getAndSortFiles(mHistMediaPath);
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

    private class DatabaseOperateData {
        private static final int RESET = 0;
        private static final int ADD = 1;
        private static final int REMOVE = 2;
        private int mOperate;
        private String mName;

        private DatabaseOperateData(int operate, String name) {
            mOperate = operate;
            mName = name;
        }
    }

    private LinkedBlockingQueue<DatabaseOperateData> mDatabaseOperateQueue = new LinkedBlockingQueue<>();
    private Thread mDatabaseOperateThread = new Thread("MediaStorageManager Database operate thread.") {
        @Override
        public void run() {
            super.run();
            while (mReady) {
                try {
                    DatabaseOperateData data = mDatabaseOperateQueue.take();
                    switch (data.mOperate) {
                        case DatabaseOperateData.RESET:
                            resetDatabase();
                            break;
                        case DatabaseOperateData.ADD:
                            addFile(data.mName);
                            break;
                        case DatabaseOperateData.REMOVE:
                            removeFile(data.mName);
                            break;
                        default:
                            LogTool.w(TAG, "Un know operate type");
                    }
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "Database operation thread with exception, ", e);
                }
            }
            mDatabaseOperateQueue.clear();
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
                    addFile(mAddMediaQueu.take());
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
                    removeFile(file);
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "Take media file from remove queue with exception, " + e);
                }
            }
        }
    };

    private void resetDatabase() {
        resetHistoryMediaInfo();
        resetWarningMediaInfo();
        resetWarnImageInfo();
        resetFocusImageInfo();
    }

    private void resetHistoryMediaInfo() {
        mDb.execSQL(DataBaseHelper.History.DELETE);
        List<File> files = getAndSortFiles(mHistMediaPath);
        if (null != files && files.size() > 0) {
            for (File file: files) {
                setHistoryFile(file);
            }
            resetHistoryQuantum();
        }
    }

    private void setHistoryFile(File file) {
        String absName = file.getAbsolutePath();
        long longStartTime = getLongTimeAccordName(HIST_MEDIA_PREFIX, absName, VIDEO_EXT);
        String stringStartTime = getStringTimeAccordName(HIST_MEDIA_PREFIX, absName, VIDEO_EXT);
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
        List<File> files = getAndSortFiles(mWarnMediaPath);
        for (File file: files) {
            setWarningFile(file);
        }
    }

    private void setWarningFile(File file) {
        String absName = file.getAbsolutePath();
        String rltName = file.getName();
        long longStartTime = getLongTimeAccordName(WARN_MEDIA_PREFIX, rltName, VIDEO_EXT);
        String stringStartTime = getStringTimeAccordName(WARN_MEDIA_PREFIX, rltName, VIDEO_EXT);
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

    private void resetWarnImageInfo() {
        mDb.execSQL(DataBaseHelper.WarnImage.DELETE);
        List<File> files = getAndSortFiles(mWarnImagePath);
        for (File file: files) {
            setWarnImageFile(file);
        }
    }

    private void setWarnImageFile(File file) {
        String name = file.getAbsolutePath();
        long longStartTime = getLongTimeAccordName(WARN_IMAGE_PREFIX, name, IMAGE_EXT);
        String stringStartTime = getStringTimeAccordName(WARN_IMAGE_PREFIX, name, IMAGE_EXT);

        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHelper.WarnImage.FILE_NAME,name);
        contentValues.put(DataBaseHelper.WarnImage.START_STRING,stringStartTime);
        contentValues.put(DataBaseHelper.WarnImage.START_LONG,longStartTime);
        mDb.insert(DataBaseHelper.WarnImage.TABLE, null, contentValues);
    }

    private void resetFocusImageInfo() {
        mDb.execSQL(DataBaseHelper.FocusImage.DELETE);
        List<File> files = getAndSortFiles(mFocusImagePath);
        for (File file: files) {
            setFocusImageFile(file);
        }
    }

    private void setFocusImageFile(File file) {
        String name = file.getAbsolutePath();
        long longStartTime = getLongTimeAccordName(FOCUS_IMAGE_PREFIX, name, IMAGE_EXT);
        String stringStartTime = getStringTimeAccordName(FOCUS_IMAGE_PREFIX, name, IMAGE_EXT);

        ContentValues contentValues = new ContentValues();
        contentValues.put(DataBaseHelper.FocusImage.FILE_NAME,name);
        contentValues.put(DataBaseHelper.FocusImage.START_STRING,stringStartTime);
        contentValues.put(DataBaseHelper.FocusImage.START_LONG,longStartTime);
        mDb.insert(DataBaseHelper.FocusImage.TABLE, null, contentValues);
    }

    private void resetHistoryQuantum() {
        mDb.execSQL(DataBaseHelper.HistoryQuantum.DELETE);
        Cursor cursor = mDb.rawQuery("select * from " + DataBaseHelper.History.TABLE
                + " ORDER BY " + DataBaseHelper.History.START_LONG + " ASC", null);

        if (cursor != null && cursor.getCount() > 0) {
            int longStartIndex = cursor.getColumnIndex(DataBaseHelper.History.START_LONG);
            int longEndIndex = cursor.getColumnIndex(DataBaseHelper.History.END_LONG);
            int stringStartIndex = cursor.getColumnIndex(DataBaseHelper.History.START_STRING);
            int stringEndIndex = cursor.getColumnIndex(DataBaseHelper.History.END_STRING);

            cursor.moveToFirst();
            String preStartString = cursor.getString(stringStartIndex);
            long preStart = cursor.getLong(longStartIndex);

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

    private void addFile(String file) {
        File f = new File(file);
        if (file.contains(HIST_MEDIA_PREFIX)) {
            setHistoryFile(f);
            resetHistoryQuantum();
        } else if (file.contains(WARN_MEDIA_PREFIX)) {
            setWarningFile(f);
        } else if (file.contains(WARN_IMAGE_PREFIX)) {
            setWarnImageFile(f);
        } else if (file.contains(FOCUS_IMAGE_PREFIX)) {
            setFocusImageFile(f);
        } else {
            LogTool.w(TAG, "Find unknow media file: " + file);
        }
    }

    private void removeFile(String file) {
        if (file.contains(HIST_MEDIA_PREFIX)) {
            if (removeHistoryFile(file) > 0) {
                resetHistoryQuantum();
            }
        } else if (file.contains(WARN_MEDIA_PREFIX)) {
            removeWarningFile(file);
        } else if (file.contains(WARN_IMAGE_PREFIX)) {
            removeWarnImageFile(file);
        } else if (file.contains(FOCUS_IMAGE_PREFIX)) {
            removeFocusImageFile(file);
        } else {
            LogTool.w(TAG, "Remove un know file: " + file);
        }
    }

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
                    mDatabaseOperateQueue.offer(new DatabaseOperateData(DatabaseOperateData.REMOVE, mPath + File.separator + path));
//                    mRemoveMediaQueu.offer(mPath + File.separator + path);
                    break;
                case MOVED_TO:
                case CLOSE_WRITE:
                    mDatabaseOperateQueue.offer(new DatabaseOperateData(DatabaseOperateData.ADD, mPath + File.separator + path));
//                    mAddMediaQueu.offer(mPath + File.separator + path);
                    break;
                case CREATE:
                    break;
                default:
            }
        }
    }

    private class TemporaryMediaObserver extends FileObserver {
        private static final int MASK = FileObserver.CLOSE_WRITE;

        private TemporaryMediaObserver(String path) {
            super(path, MASK);
        }

        @Override
        public void onEvent(int event, @Nullable String path) {
            if (path == null) {
                return;
            }

            if (path.contains(HIST_MEDIA_PREFIX)) {
                moveTmpFileToFolders(path, mHistMediaPath);
            } else if (path.contains(WARN_MEDIA_PREFIX)) {
                moveTmpFileToFolders(path, mWarnMediaPath);
            } else if (path.contains(WARN_IMAGE_PREFIX)) {
                moveTmpFileToFolders(path, mWarnImagePath);
            } else if (path.contains(FOCUS_IMAGE_PREFIX)) {
                moveTmpFileToFolders(path, mFocusImagePath);
            } else {
                LogTool.w(TAG, "Un know type file created. file: " + path);
            }
        }

        private void moveTmpFileToFolders(String fileName, String destPath) {
            File fSrc = new File(mMediaPath+File.separator + fileName);
            File fDst = new File(destPath + File.separator + fileName);
            if (fSrc.renameTo(fDst)) {
                LogTool.d(TAG, "Move file succeed, file: " + fileName + " -> " + destPath) ;
            } else {
                LogTool.w(TAG, "Move file failed, file: " + fileName + " -> " + destPath) ;
            }
        }
    }
}

