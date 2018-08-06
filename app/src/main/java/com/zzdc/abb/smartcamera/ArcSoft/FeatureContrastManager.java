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
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.guo.android_extend.image.ImageConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FeatureContrastManager {

    private static final String TAG = "FeatureContrastManager";


    private CopyOnWriteArrayList<FaceFRBean> familyFaceFRBeans;//家庭成员人脸
    private CopyOnWriteArrayList<FaceFRBean> focusFaceFRBeans;//家庭重点关注成员人脸

    private AFD_FSDKEngine AFD;
    private AFR_FSDKEngine AFR;

    private static class FeatureContrastManagerHolder {
        private static final FeatureContrastManager INSTANCE = new FeatureContrastManager();
    }

    private FeatureContrastManager() {}
    public static final FeatureContrastManager getInstance() {
        return FeatureContrastManagerHolder.INSTANCE;
    }


    //人脸比对 设置 家庭人员人脸数据 重点关注人员人脸数据
    public void setFamilyAndFocusFace(List<FaceDatabase> familyFaceDataBeans, List<FaceDatabase> focusFaceDataBeans) {
        this.familyFaceFRBeans = faceDataToFaceFR(familyFaceDataBeans);
        this.focusFaceFRBeans = faceDataToFaceFR(focusFaceDataBeans);
    }

    /**
     * 初始化人脸识别引擎 和 人脸比对引擎
     */
    private void InitFaceFeatureManager() {
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

    /***
     * 把传入的数据库数据格式转换为人脸识别引擎需要的数据格式
     * 数据库格式     FaceDatabase
     * 人脸数据格式   AFR_FSDKFace
     * 最终转换格式   FaceFRBean
     * @param faceDataBeans 数据库查询到的人脸数据  包括 人脸数据byte[] faceData  人脸名字 String name
     * @return 返回
     */
    private CopyOnWriteArrayList<FaceFRBean> faceDataToFaceFR(List<FaceDatabase> faceDataBeans) {
        CopyOnWriteArrayList<FaceFRBean> faceFRBeans = new CopyOnWriteArrayList<>();
        for (FaceDatabase faceDatabase : faceDataBeans) {
            if (faceDatabase.face != null && faceDatabase.name != null) {
                continue;
            }
            AFR_FSDKFace fsdkFace = new AFR_FSDKFace();
            fsdkFace.setFeatureData(faceDatabase.face);//取出人脸数据转换
            FaceFRBean frBean = new FaceFRBean();
            frBean.setAFRFace(fsdkFace);
            frBean.setFileName(faceDatabase.name);//人脸名字
            faceFRBeans.add(frBean);
        }

        return faceFRBeans;
    }

    /**
     * 添加家庭人脸
     *
     * @param faceDataBeans 添加的人脸信息
     */
    public void addFamilyFaceFeature(List<FaceDatabase> faceDataBeans) {
        CopyOnWriteArrayList<FaceFRBean> faceFRBeans = faceDataToFaceFR(faceDataBeans);
        familyFaceFRBeans.addAll(faceFRBeans);
    }

    /**
     * 删除家庭成员人脸数据
     *
     * @param faceName 成员名字
     */
    public void deleteFamilyFaceFeature(String faceName) {
        for (FaceFRBean faceFRBean : familyFaceFRBeans) {
            if (faceFRBean.getFileName().equals(faceName)) {
                familyFaceFRBeans.remove(faceFRBean);
            }
        }
    }

    /**
     * 添加重点关注人脸
     *
     * @param faceDataBeans
     */
    public void addFocusFamilyFaceFeature(List<FaceDatabase> faceDataBeans) {
        CopyOnWriteArrayList<FaceFRBean> faceFRBeans = faceDataToFaceFR(faceDataBeans);
        focusFaceFRBeans.addAll(faceFRBeans);
    }

    /**
     * 删除家庭重点关注人员人脸数据
     *
     * @param faceName 人员名字
     */
    public void deleteFocusFamilyFaceFeature(String faceName) {
        for (FaceFRBean faceFRBean : focusFaceFRBeans) {
            if (faceFRBean.getFileName().equals(faceName)) {
                focusFaceFRBeans.remove(faceFRBean);
            }
        }
    }

    //初始化人脸识别引擎
    private static AFD_FSDKEngine getAFD_FSDKEngine() {
        AFD_FSDKEngine FSDKEngine = new AFD_FSDKEngine();
        AFD_FSDKError err = FSDKEngine.AFD_FSDK_InitialFaceEngine(AirSoftFaceConfig.faceAPP_Id, AirSoftFaceConfig.faceFD_Key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, AirSoftFaceConfig.scale, AirSoftFaceConfig.maxFacesNUM);
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
     * 开始视频人脸识别对比
     *
     * @param data 每一帧的数据  格式 NV21  1920  1080
     */
    public void startContrastFeature(byte[] data, int width, int height) {
        InitFaceFeatureManager();
        if (AFD == null || AFR == null) {
            Log.d(TAG, "startContrastFeature  AFD && AFR init failed");
            AFD = getAFD_FSDKEngine();//初始化搜集人脸
            AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
        }
        AFD_FSDKError error_FD;
        AFR_FSDKError error_FR;
        List<AFD_FSDKFace> result_FD = new ArrayList<>();
        error_FD = AFD.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result_FD);
        if (error_FD.getCode() != 0) {
            Log.d(TAG, "人脸数据提取错误 name >> " + error_FD.toString());
            return;
        }
        if (result_FD.size() <= 0) {
            return;
        }
        Log.d(TAG, "图像中人脸个数 >> " + result_FD.size());

        //遍历人脸数组获取人脸信息
        for (int i = 0; i < result_FD.size(); i++) {
            AFR_FSDKFace face = new AFR_FSDKFace(); // 用来存放提取到的人脸信息
            AFD_FSDKFace item = result_FD.get(i);
            Rect itemRect = item.getRect(); // 人脸在图片中的位置
            int degree = item.getDegree(); // 人脸方向
            error_FR = AFR.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, itemRect, degree, face); // 提取人脸信息
            if (error_FR.getCode() != 0) {
                Log.d(TAG, "这一帧中人脸特征数据提取错误");
                continue;
            }
            byte[] faceData = face.getFeatureData();//人脸数据
            Log.d(TAG, "获取到人脸数据 坐标为 : left = " + itemRect.left + " right :" + itemRect.right + " top :" + itemRect.top + " bottom :" + itemRect.bottom);
            contrastFaceFeature(faceData, itemRect, degree);
        }
    }


    /**
     * 对比每一帧中的每一个人脸
     *
     * @param face 人脸数据
     * @param rect 人脸坐标
     * @param ori  人脸角度
     */
    private void contrastFaceFeature(byte[] face, Rect rect, int ori) {
        AFR_FSDKError error_FR;

        AFR_FSDKFace fsdkFace = new AFR_FSDKFace();
        fsdkFace.setFeatureData(face);

        AFR_FSDKMatching score = new AFR_FSDKMatching(); //score用于存放人脸对比的相似度值
        for (FaceFRBean faceFRBean : familyFaceFRBeans) {
            error_FR = AFR.AFR_FSDK_FacePairMatching(fsdkFace, faceFRBean.getAFRFace(), score);
            if (error_FR.getCode() != 0) {
                Log.d(TAG, "人脸比对错误 error " + error_FR.toString());
                continue;
            }
            float similarity = score.getScore();//相似度
            Log.d(TAG, "人脸比对成功 相似度 " + similarity);
            if (similarity > 0.5) {
                //相似度大于 50  当前人脸时家庭成员
                Log.d(TAG, "人脸比对结果 是家庭成员 ：" + faceFRBean.getFileName());
                if (familyFocusFeature(face)) {
                    //是重点关注人员
                }
            } else {
                //当前人脸时陌生人
                Log.d(TAG, "人脸比对结果 是陌生人 ");
            }
        }
    }

    /**
     * 比对是否是重点关注人员
     *
     * @param face
     * @return
     */
    private boolean familyFocusFeature(byte[] face) {
        AFR_FSDKError error_FR;

        AFR_FSDKFace fsdkFace = new AFR_FSDKFace();
        fsdkFace.setFeatureData(face);
        AFR_FSDKEngine AFR = getAFR_FSDKEngine();//人脸特征提取
        AFR_FSDKMatching score = new AFR_FSDKMatching(); //score用于存放人脸对比的相似度值
        for (FaceFRBean faceFRBean : focusFaceFRBeans) {
            error_FR = AFR.AFR_FSDK_FacePairMatching(fsdkFace, faceFRBean.getAFRFace(), score);
            if (error_FR.getCode() != 0) {
                Log.d(TAG, "人脸比对错误 error " + error_FR.toString());
                continue;
            }
            float similarity = score.getScore();//相似度
            Log.d(TAG, "人脸比对成功 相似度 " + similarity);
            if (similarity > 0.7) {
                return true;
            }
        }
        return false;
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
