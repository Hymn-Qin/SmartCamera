package com.zzdc.abb.smartcamera.TutkBussiness;

import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

public class SDCardBussiness {
    private static final SDCardBussiness ourInstance = new SDCardBussiness();
    private static final String TAG = SDCardBussiness.class.getSimpleName();

    public static SDCardBussiness getInstance() {
        return ourInstance;
    }

    private SDCardBussiness() {
    }

    public Boolean isSDCardAvailable() {
        Boolean tmpIsRemove = false;
        StorageManager tmpStorageManager =  (StorageManager) SmartCameraApplication.getContext().getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = tmpStorageManager.getStorageVolumes();

        try {
            Class<?> storageVolumeClass = Class.forName("android.os.storage.StorageVolume");
            Method getPath = storageVolumeClass.getMethod("getPath");
            Method isRemovable = storageVolumeClass.getMethod("isRemovable");
            for (int i = 0; i < volumes.size(); i++ ){
                StorageVolume tmpStorageVolume = volumes.get(i);
                String tmpStoragePath = (String) getPath.invoke(tmpStorageVolume);
                Boolean IsCanRemoveAble = (Boolean) isRemovable.invoke(tmpStorageVolume);
                Log.d(TAG, "i = " + i + " tmpStoragePath = " + tmpStoragePath + ",isRemovableResult=" + IsCanRemoveAble);
                if (IsCanRemoveAble){
                    tmpIsRemove = true;
                    break;
                }
            }
        }catch (Exception e){

        }

        return tmpIsRemove;

    }

    public String getSDCardVideoRootPath() {
        String tmpSDPath= null;
        StorageManager tmpStorageManager =  (StorageManager) SmartCameraApplication.getContext().getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = tmpStorageManager.getStorageVolumes();

        try {
            Class<?> storageVolumeClass = Class.forName("android.os.storage.StorageVolume");
            Method getPath = storageVolumeClass.getMethod("getPath");
            Method isRemovable = storageVolumeClass.getMethod("isRemovable");
            for (int i = 0; i < volumes.size(); i++ ){
                StorageVolume tmpStorageVolume = volumes.get(i);
                String tmpStoragePath = (String) getPath.invoke(tmpStorageVolume);
                Boolean IsCanRemoveAble = (Boolean) isRemovable.invoke(tmpStorageVolume);
                Log.d(TAG, "i = " + i + " tmpStoragePath = " + tmpStoragePath + ",isRemovableResult=" + IsCanRemoveAble);
                if (IsCanRemoveAble){
                    tmpSDPath = tmpStoragePath;

                    break;
                }
            }
        }catch (Exception e){

        }
        return tmpSDPath;
    }

    public static void getAvaliableStorages(){
        StorageManager tmpStorageManager =  (StorageManager) SmartCameraApplication.getContext().getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> volumes = tmpStorageManager.getStorageVolumes();

        try {
            Class<?> storageVolumeClass = Class.forName("android.os.storage.StorageVolume");
            Method getPath = storageVolumeClass.getMethod("getPath");
            Method isRemovable = storageVolumeClass.getMethod("isRemovable");
            for (int i = 0; i < volumes.size(); i++ ){
                StorageVolume tmpStorageVolume = volumes.get(i);
                String tmpStoragePath = (String) getPath.invoke(tmpStorageVolume);
                Boolean IsCanRemoveAble = (Boolean) isRemovable.invoke(tmpStorageVolume);
                Log.d(TAG, "i = " + i + " tmpStoragePath = " + tmpStoragePath + ",isRemovableResult=" + IsCanRemoveAble);
                if (IsCanRemoveAble){

                    return;
                }
            }
        }catch (Exception e){

        }
    }

    public  long getAvailableSpace(){
        File sdcard_filedir = new File(getSDCardVideoRootPath());
        long usableSpace = sdcard_filedir.getUsableSpace();
        long tmpUsableSpace =  ((usableSpace/1024) /1024);
        long totalSpace = sdcard_filedir.getTotalSpace();
        long tmpTotalSpace =  ((totalSpace/1024) /1024);
        Log.d(TAG,"getUsableSpace = " + tmpUsableSpace + " ,totalSpace = " + tmpTotalSpace);
        return tmpUsableSpace;
    }
}
