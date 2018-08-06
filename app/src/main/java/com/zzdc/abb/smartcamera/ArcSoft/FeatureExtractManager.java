package com.zzdc.abb.smartcamera.ArcSoft;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.util.Log;
import com.arcsoft.facedetection.AFD_FSDKEngine;
import com.arcsoft.facedetection.AFD_FSDKError;
import com.arcsoft.facedetection.AFD_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.guo.android_extend.image.ImageConverter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FeatureExtractManager {

    private static final String TAG = "FeatureExtractManager";
    private AFD_FSDKEngine AFD;
    private AFR_FSDKEngine AFR;

    private String faceImagePath;
    private String faceName;
    /**
     * 人脸数据提取
     * <p>
     * FaceFeatureManager a = new FaceFeatureManager(faceName, faceImagePath);
     * ArrayList<byte[]> faceDataList = a.FRToExtractFeature();
     * 保存到数据库数据  AirSoftUtil.saveOrUpdateFaceData(faceName, faceDataList.item);
     * a.UninitFaceFeatureManager();
     *
     * @param faceName      人员名字
     * @param faceImagePath 照片保存路径
     */
    public FeatureExtractManager(String faceName, String faceImagePath) {
        this.faceName = faceName;
        this.faceImagePath = faceImagePath;
    }

    /**
     * 初始化人脸识别引擎 和 人脸比对引擎
     */
    public void InitFaceFeatureManager() {
        AFD = getAFD_FSDKEngine();//初始化搜集人脸
        AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
    }

    /**
     * 注销人脸识别引擎 和 人脸提取引擎
     */
    public void UninitFaceFeatureManager() {
        if (AFD != null) {
            AFD.AFD_FSDK_UninitialFaceEngine();
        }
        if (AFR != null) {
            AFR.AFR_FSDK_UninitialEngine();
        }
    }


    //初始化人脸识别引擎
    private static AFD_FSDKEngine getAFD_FSDKEngine() {
        AFD_FSDKEngine FSDKEngine = new AFD_FSDKEngine();
        AFD_FSDKError err = FSDKEngine.AFD_FSDK_InitialFaceEngine(AirSoftFaceConfig.faceAPP_Id,
                AirSoftFaceConfig.faceFD_Key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, AirSoftFaceConfig.scale, AirSoftFaceConfig.maxFacesNUM);
        if (err.getCode() == 0) {
            return FSDKEngine;
        } else {
            Log.d(TAG, "getAFD_FSDKEngine >> AFD_FSDK_InitialFaceEngine = " + err.getCode());
            return null;
        }
    }

    //初始化人脸对比引擎
    private static AFR_FSDKEngine getAFR_FSDKEngine() {
        AFR_FSDKEngine AFRengine = new AFR_FSDKEngine();
        AFR_FSDKError error = AFRengine.AFR_FSDK_InitialEngine(AirSoftFaceConfig.faceAPP_Id, AirSoftFaceConfig.faceFR_KEY);
        if (error.getCode() == 0) {
            return AFRengine;
        } else {
            Log.d(TAG, "getAFR_FSDKEngine >> AFR_FSDK_InitialEngine = " + error.getCode());
            return null;
        }
    }

    /**
     * 通过客户端的传过来的一组人脸图片 都转换为NV21
     *
     * @param facePath 图片文件夹路径
     * @return
     */
    private static List<FacePictures> getFaceImageList(String facePath) {
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
                    facePictures.add(getFaceImage(facePath + "/" + fileName));
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
    private static FacePictures getFaceImage(String path) {
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

            if (!temp.equals(res)) {
                res.recycle();
            }

            byte[] data = new byte[res.getWidth() * res.getHeight() * 3 / 2];
            ImageConverter convert = new ImageConverter();
            convert.initial(res.getWidth(), res.getHeight(), ImageConverter.CP_PAF_NV21);
            if (convert.convert(res, data)) {
                Log.d(TAG, "convert <<path>> ok! " + path);
            }
            convert.destroy();
            facePIC = new FacePictures();
            facePIC.setNV21(data);
            facePIC.setFileName(path);
            facePIC.setWidth(res.getWidth());
            facePIC.setHigh(exeHigh(res.getHeight()));
            return facePIC;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
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


    /***
     * 识别文件夹下的所有图片的人脸信息 并保存
     */
    public ArrayList<byte[]> FRToExtractFeature() {
        InitFaceFeatureManager();
        List<FacePictures> facePicCacheList = getFaceImageList(faceImagePath);
        ArrayList<byte[]> faceDataList = new ArrayList<>();
        if (facePicCacheList == null || facePicCacheList.size() == 0) {
            Log.d(TAG, "  facePicCacheList is null !!  pls help to check it !!");
            return null;//獲取人臉失敗
        }
        if (AFD == null || AFR == null) {
            Log.d(TAG, "FRToExtractFeature  AFD && AFR init failed");
            AFD = getAFD_FSDKEngine();//初始化搜集人脸
            AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
        }
        AFD_FSDKError error_FD;
        AFR_FSDKError error_FR;
        for (FacePictures facePIC : facePicCacheList) {
            List<AFD_FSDKFace> result_FD = new ArrayList<>();
            error_FD = AFD.AFD_FSDK_StillImageFaceDetection(facePIC.getNV21(), facePIC.getWidth(), facePIC.getHigh(), AFD_FSDKEngine.CP_PAF_NV21, result_FD);
            Log.d(TAG, "AFD.AFD_FSDK_StillImageFaceDetection  !! ResultCode = " + error_FD.getCode() + " facePIC = " + facePIC.getFileName());
            if (error_FD.getCode() != 0) {
                Log.d(TAG, "人脸数据提取错误 name >> " + faceName + error_FD.toString());
                continue;
            }
            if (result_FD.size() <= 0) {
                continue;
            }
            Log.d(TAG, "人脸图片 >> " + facePIC.getFileName() + " 人脸个数 >> " + result_FD.size());

            //遍历人脸数组获取人脸信息
            for (int i = 0; i < result_FD.size(); i++) {
                AFR_FSDKFace face = new AFR_FSDKFace(); // 用来存放提取到的人脸信息
                AFD_FSDKFace item = result_FD.get(i);
                Rect itemRect = item.getRect(); // 人脸在图片中的位置
                int degree = item.getDegree(); // 人脸方向
                error_FR = AFR.AFR_FSDK_ExtractFRFeature(facePIC.getNV21(), facePIC.getWidth(), facePIC.getHigh(), AFR_FSDKEngine.CP_PAF_NV21, itemRect, degree, face); // 提取人脸信息
                if (error_FR.getCode() != 0) {
                    Log.d(TAG, "人脸特征数据提取错误 name >> " + faceName + error_FR.toString());
                    continue;
                }
                byte[] faceData = face.getFeatureData();//人脸数据

                faceDataList.add(faceData);
                Log.d(TAG, "获取到人脸数据 name >> " + faceName);
            }
        }
        UninitFaceFeatureManager();
        return faceDataList;
    }
    public static class FacePictures {
        private int width;
        private int high;
        private byte[] NV21;
        private String fileName;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }


        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHigh() {
            return high;
        }

        public void setHigh(int high) {
            this.high = high;
        }

        public byte[] getNV21() {
            return NV21;
        }

        public void setNV21(byte[] NV21) {
            this.NV21 = NV21;
        }
    }
}
