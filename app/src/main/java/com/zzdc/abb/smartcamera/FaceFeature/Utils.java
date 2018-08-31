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

    private static final String facePath = "FACE";



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

    public static void startGetFeatureFromPath() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "qxj--------开始人脸提取");
                //提取人脸数据
                Utils.startToSaveFeature(getFaceImageList(getFaceImagePath(), "家庭成员"));
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


    /**
     * 创建图片保存路径
     *
     * @return
     */
    private static String getFaceImagePath() {
        return getSDPath(facePath);
    }

    public static String getSDPath(String path) {
        String tmpPath = null;
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + path;
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), path);
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

    /**
     * 通过客户端的传过来的一组人脸图片 都转换为NV21
     *
     * @param facePath 图片文件夹路径
     * @return
     */
    private static List<FacePictures> getFaceImageList(String facePath, String name) {
        File file = new File(facePath);
        List<FacePictures> facePictures = new ArrayList<>();
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files.length == 0) {
                return null;
            }
            for (File file1 : files) {
                String fileName = file1.getName();
                if (isImage(fileName)) {
                    Log.d(TAG, "Face image path --> " + facePath + "/" + fileName);
                    facePictures.add(getFaceImage(facePath + "/" + fileName, name));
                }
            }
            if (facePictures.size() > 0) {
                file.delete();
                return facePictures;
            }
        }

        return null;
    }

    //转换照片格式 NV21
    private static FacePictures getFaceImage(String path, String name) {
        FacePictures facePIC;
        try {
            Bitmap res;
            Log.d(TAG, "decodeImage  path = " + path);
            ExifInterface exif = new ExifInterface(path);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inSampleSize = 4;
            op.inJustDecodeBounds = false;
            //op.inMutable = true;
            res = BitmapFactory.decodeFile(path, op);
            //rotate and scale.
            Matrix matrix = new Matrix();

            Log.d(TAG, "PIC orientation >> " + orientation);

            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                matrix.postRotate(90);
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                matrix.postRotate(180);
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                matrix.postRotate(270);
            }

            Bitmap temp = Bitmap.createBitmap(res, 0, 0, res.getWidth(), res.getHeight(), matrix, true);
            Log.d(TAG, "check target Image:" + temp.getWidth() + "X" + temp.getHeight());

//            if (!temp.equals(res)) {
//                res.recycle();
//            }

            byte[] data = new byte[temp.getWidth() * temp.getHeight() * 3 / 2];
            ImageConverter convert = new ImageConverter();
            convert.initial(temp.getWidth(), temp.getHeight(), ImageConverter.CP_PAF_NV21);
            if (convert.convert(temp, data)) {
                Log.d(TAG, "convert <<path>> ok! " + path);
            }
            convert.destroy();
            facePIC = new FacePictures();
            facePIC.setNV21(data);
            facePIC.setName(name);
            facePIC.setDirection(path);
            facePIC.setWidth(temp.getWidth());
            facePIC.setHigh(exeHigh(temp.getHeight()));
            return facePIC;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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

    private static boolean isImage(String fileName) {
        if (fileName == null || fileName.length() < 4) {
            return false;
        }
        String fileType = fileName.substring(fileName.length() - 3, fileName.length());
        return fileType.equals("jpg") || fileType.equals("png") || fileType.equals("gif") || fileType.equals("tif") || fileType.equals("bmp");
    }
}
