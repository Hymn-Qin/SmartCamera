package com.zzdc.abb.smartcamera.FaceFeature;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;

import com.guo.android_extend.image.ImageConverter;
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.controller.MediaStorageManager;
import com.zzdc.abb.smartcamera.controller.VideoGather;
import com.zzdc.abb.smartcamera.util.LogTool;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final String TAG = "QXJUtils";


    public static void startGetFeature(final List<FacePictures> facePictures) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "qxj--------开始人脸提取");
                //提取人脸数据
                Utils.startToSaveFeature(facePictures);
            }
        }).start();
    }

    public static void startToCreatePicture(String imagePath) {
        LogTool.d(TAG, "qxj--------Create Picture, this name : " + imagePath);
        PictureProductManager produceManager = PictureProductManager.getInstance();
        produceManager.startCreatePicture(imagePath, true);
        VideoGather.getInstance().registerVideoRawDataListener(produceManager);
    }
    private static void startToSaveFeature(List<FacePictures> facePictures) {
        Log.d(TAG, "qxj--------进入人脸提取");
        if (facePictures == null) {
            return;
        }
        ContrastManager feature = ContrastManager.getInstance();
        feature.setSwitchContrast(true);
        ArrayList<FaceFRBean> faceDatas = feature.ObtainPicturesFeature(facePictures);
        if (faceDatas == null || faceDatas.size() == 0) {
            return;
        }
        //添加到识别队列
        feature.addFamilyFaceFeature(faceDatas);
        //保存数据库
        MediaStorageManager mediaStorageManager = MediaStorageManager.getInstance();
        mediaStorageManager.saveFaceData(faceDatas);
    }


    public static FacePictures getFaceImage(byte[] picture, String name, String direction) {
        FacePictures facePIC;
        Bitmap temp = BitmapFactory.decodeByteArray(picture, 0, picture.length);
        byte[] data = new byte[temp.getWidth() * temp.getHeight() * 3 / 2];
        ImageConverter convert = new ImageConverter();
        convert.initial(temp.getWidth(), temp.getHeight(), ImageConverter.CP_PAF_NV21);
        if (convert.convert(temp, data)) {
            Log.d(TAG, "convert <<picture byte[]>> ok! ");
        }
        convert.destroy();
        facePIC = new FacePictures();
        facePIC.setNV21(data);
        facePIC.setName(name);
        facePIC.setDirection(direction);
        facePIC.setWidth(temp.getWidth());
        facePIC.setHigh(exeHigh(temp.getHeight()));
        return facePIC;
    }

    //资源的High不能为奇数，这是ArcSoft的规定。需要处理一下
    private static int exeHigh(int high) {
        if (high / 2 == 1) {
            return high + 1;
        }
        return high;
    }
}
