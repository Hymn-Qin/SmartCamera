package com.zzdc.abb.smartcamera.ArcSoft;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;

import com.guo.android_extend.image.ImageConverter;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AirSoftUtil {

    private static final String TAG = "AirSoftUtil";

    private static final String facePath = "FACE";

    /**
     * 保存或修改 家庭成员人脸数据
     *
     * @param name     成员名字
     * @param faceData 成员人脸数据
     */
    public static void saveOrUpdateFaceData(String name, byte[] faceData) {
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
