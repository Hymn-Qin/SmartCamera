package com.zzdc.abb.smartcamera.FaceFeature;

import android.util.Log;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.zzdc.abb.smartcamera.FaceContrast.FaceDatabase_Table;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final String TAG = "Utils";

    private static final String facePath = "FACE";

    /**
     * 保存或修改 家庭成员人脸数据
     *
     * @param name     成员名字
     * @param faceData 成员人脸数据
     */
    private static void saveOrUpdateFaceData(String name, byte[] faceData) {
        FaceDatabase faceDatabase = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name))
                .querySingle();
        if (faceDatabase != null) {
            SQLite.update(FaceDatabase.class)
                    .set(FaceDatabase_Table.times.eq((long) 123), FaceDatabase_Table.face.eq(faceData))
                    .where(FaceDatabase_Table.id.is(faceDatabase.id)).execute();
            Log.d(TAG, "更新人脸数据 name >> " + name);

        } else {
            faceDatabase = new FaceDatabase();
            faceDatabase.times = 123;
            faceDatabase.name = name;
            faceDatabase.face = faceData;
            faceDatabase.focus = false;
            faceDatabase.save();
            Log.d(TAG, "保存新的人脸数据 name >> " + name);
        }
    }

    public static void startToSaveFeature(String name, String path) {
        FeatureExtractManager feature = new FeatureExtractManager(name, path);
        ArrayList<byte[]> faceData =  feature.getFRToExtractFeature();
        if (faceData == null || faceData.size() == 0) {
            return;
        }
        for (int i = 0; i < faceData.size(); i++) {
            Log.d(TAG, "识别到 name - " + name + " 的人脸数据" + i);
            saveOrUpdateFaceData(name, faceData.get(i));

            Log.d(TAG, "添加 " + name + " 的人脸数据 到视频识别队列中");
            FaceDatabase faceDatabase = new FaceDatabase();
            faceDatabase.face = faceData.get(i);
            faceDatabase.name = name;
            FeatureContrastManager features = FeatureContrastManager.getInstance();
            features.addFamilyFaceFeature(faceDatabase);
        }
        Log.d(TAG, "完成识别 " + name + " 的人脸数据");
    }
    /**
     * 设置重点关注
     *
     * @param name
     */
    public void setFocusFaceData(String name) {
        List<FaceDatabase> faceDatabaseList = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name))
                .queryList();
        if (faceDatabaseList.size() > 0) {
            SQLite.update(FaceDatabase.class)
                    .set(FaceDatabase_Table.focus.is(true))
                    .where(FaceDatabase_Table.name.eq(name)).execute();
        }
    }

    /**
     * 删除人脸数据
     *
     * @param name
     */
    public void deleteFaceData(String name) {
        FaceDatabase faceDatabase = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name))
                .querySingle();
        if (faceDatabase != null) {
            SQLite.delete()
                    .from(FaceDatabase.class)
                    .where(FaceDatabase_Table.name.eq(name))
                    .execute();
        }
    }

    /**
     * 删除重点关注人员
     *
     * @param name
     */
    public void deleteFocusFaceData(String name) {
        FaceDatabase faceDatabase = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name))
                .querySingle();
        if (faceDatabase != null) {
            SQLite.update(FaceDatabase.class)
                    .set(FaceDatabase_Table.focus.is(false))
                    .where(FaceDatabase_Table.name.eq(name))
                    .execute();
        }
    }

    /**
     * 获取所有的人脸数据
     *
     * @return 所有保存的人脸数据
     */
    public static List<FaceDatabase> getAllFaceData() {
        List<FaceDatabase> faceDatabaseList = SQLite.select()
                .from(FaceDatabase.class)
                .queryList();
        if (faceDatabaseList.size() == 0) {
            return null;
        }
        return faceDatabaseList;
    }

    /**
     * 获取所有的重点关注人脸数据
     *
     * @return 所有重点关注的人脸数据
     */
    public static List<FaceDatabase> getFocusFaceData() {
        List<FaceDatabase> faceDatabaseList = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.focus.is(true))
                .queryList();
        if (faceDatabaseList.size() == 0) {
            return null;
        }
        return faceDatabaseList;
    }

    /**
     * 创建图片保存路径
     *
     * @return
     */
    public static String getFaceImagePath() {
        String tmpPath = "";
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + facePath;
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), facePath);
            if (!mkDir.exists()) {
                mkDir.mkdirs();   //目录不存在，则创建
            }
            tmpPath = tmpDir;
            LogTool.d(TAG, "tmpPath " + tmpPath);
        } else {
            Log.d(TAG, "sd卡不存在");
        }
        return tmpPath;
    }


}
