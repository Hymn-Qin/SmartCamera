package com.zzdc.abb.smartcamera.FaceFeature;

import android.annotation.SuppressLint;
import android.util.Log;

import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Utils {

    private static final String TAG = "QXJUtils";

    private static final String facePath = "FACE";

    /**
     * 保存或修改 家庭成员人脸数据
     *
     * @param name     成员名字
     * @param faceData 成员人脸数据
     */
    private static void saveOrUpdateFaceData(String name, String direction, byte[] faceData) {
        FaceDatabase faceDatabase = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name), FaceDatabase_Table.direction.eq(direction))
                .querySingle();
        if (faceDatabase != null) {
            SQLite.update(FaceDatabase.class)
                    .set(FaceDatabase_Table.times.eq(timeNow()), FaceDatabase_Table.face.eq(faceData))
                    .where(FaceDatabase_Table.id.is(faceDatabase.id)).execute();
            Log.d(TAG, "更新人脸数据 name >> " + name);

        } else {
            faceDatabase = new FaceDatabase();
            faceDatabase.times = timeNow();
            faceDatabase.name = name;
            faceDatabase.face = faceData;
            faceDatabase.direction = direction;
            faceDatabase.focus = false;
            faceDatabase.save();
            Log.d(TAG, "保存新的人脸数据 name >> " + name);
        }
    }

    public static void startGetFeature(final String name) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "开始人脸提取");
                //提取人脸数据
//                Utils.startToSaveFeature(name, Utils.getFaceImagePath());
            }
        }).start();
    }

    private static void startToSaveFeature(String name, String path) {
        Log.d(TAG, "进入人脸提取");
        FeatureContrastManager feature = FeatureContrastManager.getInstance();
        feature.setSwitchContrast(true);
        ArrayList<FaceDatabase> faceData =  feature.getFRToExtractFeature(name, path);
        if (faceData == null || faceData.size() == 0) {
            return;
        }

        FeatureContrastManager features = FeatureContrastManager.getInstance();
        features.addFamilyFaceFeature(faceData);
        for (int i = 0; i < faceData.size(); i++) {
            Log.d(TAG, "识别到 name - " + faceData.get(i).name + " 的人脸数据" + i);
            saveOrUpdateFaceData(name, faceData.get(i).direction, faceData.get(i).face);
            Log.d(TAG, "添加 " + name + " 的人脸数据 到视频识别队列中");
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
        List<FaceDatabase> faceDatabases = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name))
                .queryList();
        if (faceDatabases != null && faceDatabases.size() > 0) {
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
        List<FaceDatabase> faceDatabases = SQLite.select()
                .from(FaceDatabase.class)
                .where(FaceDatabase_Table.name.eq(name))
                .queryList();
        if (faceDatabases != null && faceDatabases.size() > 0) {
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

    public static File getFacePictureFile(String fileName) {
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = getFaceImagePath();
            // TODO
            try {
                String imagePath = fileName + ".jpg";
                File file = new File(tmpDir, imagePath);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                return file;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    public static String timeNow() {
        //时间System.currentTimeMillis()
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// HH:mm:ss
        // 获取当前时间
        Date date = new Date(System.currentTimeMillis());
        String time = simpleDateFormat.format(date);
        return time;
    }
    public static String timeNow(long times) {
        //时间System.currentTimeMillis()
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");// HH:mm:ss
        // 获取当前时间
        Date date = new Date(times);
        String time = simpleDateFormat.format(date);
        return time;
    }

}
