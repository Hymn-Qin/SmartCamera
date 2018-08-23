package com.zzdc.abb.smartcamera.FaceFeature;

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
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.guo.android_extend.image.ImageConverter;
import com.zzdc.abb.smartcamera.controller.VideoGather;
import com.zzdc.abb.smartcamera.util.LogTool;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;


public class ContrastManager implements VideoGather.VideoRawDataListener{

    private static final String TAG = ContrastManager.class.getSimpleName() + "qxj";

    private LinkedBlockingQueue<byte[]> videoDatas = new LinkedBlockingQueue<>();
    private CopyOnWriteArrayList<FaceFRBean> familyFaceFRBeans;//家庭成员人脸
    private CopyOnWriteArrayList<FaceFRBean> focusFaceFRBeans;//家庭重点关注成员人脸

    private AFD_FSDKEngine AFD;
    private AFR_FSDKEngine AFR;
    private AFT_FSDKEngine AFT;

    private String faceAPP_Id;
    private String faceFD_Key;
    private String faceFR_KEY;
    private String faceFT_KEY;

    public static boolean isContrast = false;
    private int scaleFD;
    private int scaleFT;
    private int MAXFD;
    private int MAXFT;
    private int width;
    private int height;

    private static class ContrastManagerHolder {
        private static final ContrastManager INSTANCE = new ContrastManager();
    }

    private ContrastManager() {
    }

    public static final ContrastManager getInstance() {
        return ContrastManagerHolder.INSTANCE;
    }

    private OnContrastListener onContrastListener = null;

    public void onContrasManager(OnContrastListener onContrastListener) {
        this.onContrastListener = onContrastListener;
    }
    private Thread contrastThread = new Thread("Thread-contrastThread"){
        @Override
        public void run() {
            super.run();
            Log.d(TAG, "start contrastThread data");
            while (isContrast) {
                byte[] videoData = null;
                try {
                    videoData = videoDatas.take();
                    Log.d(TAG, "data contrasting ");
                    if (videoData != null) {
                        startContrastFeature(videoData, width, height);//设别
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /**
     * 初始化人脸识别引擎 和 人脸比对引擎
     */
    private void InitFaceFeatureManager() {
        AFD = getAFD_FSDKEngine();//初始化搜集人脸
        AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
        AFT = getAFT_FSDKEngine();
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
        if (AFT != null) {
            AFT.AFT_FSDK_UninitialFaceEngine();
        }
    }

    public void addFamilyFaceFeature(List<FaceFRBean> faceDataBeans) {
        if (familyFaceFRBeans == null) return;
        familyFaceFRBeans.addAll(faceDataBeans);
    }

    /**
     * 删除家庭成员人脸数据
     *
     * @param faceName 成员名字
     */
    public void deleteFamilyFaceFeature(String faceName) {
        for (FaceFRBean faceFRBean : familyFaceFRBeans) {
            if (faceFRBean.getmName().equals(faceName)) {
                familyFaceFRBeans.remove(faceFRBean);
            }
        }
    }

    /**
     * 添加重点关注人脸
     *
     * @param faceDataBeans
     */
    public void addFocusFamilyFaceFeature(List<FaceFRBean> faceDataBeans) {
        focusFaceFRBeans.addAll(faceDataBeans);
    }

    /**
     * 删除家庭重点关注人员人脸数据
     *
     * @param faceName 人员名字
     */
    public void deleteFocusFamilyFaceFeature(String faceName) {
        for (FaceFRBean faceFRBean : focusFaceFRBeans) {
            if (faceFRBean.getmName().equals(faceName)) {
                focusFaceFRBeans.remove(faceFRBean);
            }
        }
    }

    //初始化人脸识别引擎
    private AFD_FSDKEngine getAFD_FSDKEngine() {
        AFD_FSDKEngine FSDKEngine = new AFD_FSDKEngine();
        AFD_FSDKError err = FSDKEngine.AFD_FSDK_InitialFaceEngine(faceAPP_Id, faceFD_Key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, scaleFD, MAXFD);
        if (err.getCode() == 0) {
            return FSDKEngine;
        } else {
            if (onContrastListener != null) {
                onContrastListener.onContrastError("getAFD_FSDKEngine >> AFD_FSDK_InitialFaceEngine = " + err.getCode());
            }
            return null;
        }
    }

    //初始化人脸对比引擎
    private AFR_FSDKEngine getAFR_FSDKEngine() {
        AFR_FSDKEngine AFREngine = new AFR_FSDKEngine();
        AFR_FSDKError error = AFREngine.AFR_FSDK_InitialEngine(faceAPP_Id, faceFR_KEY);
        if (error.getCode() == 0) {
            return AFREngine;
        } else {
            if (onContrastListener != null) {
                onContrastListener.onContrastError("getAFR_FSDKEngine >> AFR_FSDK_InitialEngine = " + error.getCode());
            }
            return null;
        }
    }

    //初始化人脸追踪引擎
    private AFT_FSDKEngine getAFT_FSDKEngine() {
        AFT_FSDKEngine FTDKEngine = new AFT_FSDKEngine();
        AFT_FSDKError err = FTDKEngine.AFT_FSDK_InitialFaceEngine(faceAPP_Id, faceFT_KEY, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, scaleFT, MAXFT);
        if (err.getCode() == 0) {
            return FTDKEngine;
        } else {
            if (onContrastListener != null) {
                onContrastListener.onContrastError("getAFT_FSDKEngine >> AFT_FSDK_InitialFaceEngine = " + err.getCode());
            }
            return null;
        }
    }

    public ContrastManager setContrastKey(String APP_ID, String FD_KEY, String FR_KEY, String FT_KEY) {
        this.faceAPP_Id = APP_ID;
        this.faceFD_Key = FD_KEY;
        this.faceFR_KEY = FR_KEY;
        this.faceFT_KEY = FT_KEY;
        return ContrastManager.getInstance();
    }

    public ContrastManager setContrastConfig(int width, int height, int scaleFD, int scaleFT, int MAXFD, int MAXFT) {
        this.width = width;
        this.height = height;
        this.scaleFD = scaleFD;
        this.scaleFT = scaleFT;
        this.MAXFD = MAXFD;
        this.MAXFT = MAXFT;
        return ContrastManager.getInstance();
    }

    public ContrastManager setSwitchContrast(boolean isOK) {
        if (isContrast != isOK) {
            isContrast = isOK;
            if (isContrast) {
                InitFaceFeatureManager();
                familyFaceFRBeans = new CopyOnWriteArrayList<>();
                focusFaceFRBeans = new CopyOnWriteArrayList<>();
            } else {
                UninitFaceFeatureManager();
            }
        }
        return ContrastManager.getInstance();
    }

    //人脸比对 设置 家庭人员人脸数据 重点关注人员人脸数据
    public ContrastManager setFamilyFace(List<FaceFRBean> familyFaceDataBeans) {
        if (familyFaceDataBeans != null && familyFaceDataBeans.size() > 0) {
            familyFaceFRBeans.addAll(familyFaceDataBeans);
        }
        return ContrastManager.getInstance();
    }

    public ContrastManager setFamilyFocusFace(List<FaceFRBean> focusFaceDataBeans) {
        if (focusFaceDataBeans != null && focusFaceDataBeans.size() > 0) {
            focusFaceFRBeans.addAll(focusFaceDataBeans);
        }

        return ContrastManager.getInstance();
    }

    public void startContrast() {
        if (isContrast) {
            contrastThread.start();
        }
    }

    @Override
    public void onVideoRawDataReady(VideoGather.VideoRawBuf buf) {
        videoDatas.offer(buf.getData());
    }

    /**
     * 开始视频人脸识别对比
     *
     * @param data 每一帧的数据  格式 NV21  1920  1080
     */
    public void startContrastFeature(byte[] data, int width, int height) {
        if (!isContrast) {
            return;
        }
        if (AFT == null || AFR == null) {

            if (AFR == null) {
                Log.e(TAG, "startContrastFeature  AFR init failed");
                AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
            }
            if (AFT == null) {
                Log.e(TAG, "startContrastFeature  AFT init failed");
                AFT = getAFT_FSDKEngine();
            }
        }
        AFR_FSDKError error_FR;
        AFT_FSDKError error_FT;
        List<AFT_FSDKFace> result_FT = new ArrayList<>();
        error_FT = AFT.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result_FT);
        if (error_FT.getCode() != 0) {
            Log.d(TAG, "error_FT AFT_FSDK_FaceFeatureDetect >> " + error_FT.getCode());

            if (onContrastListener != null) {
                onContrastListener.onContrastError("error_FT >> " + error_FT.getCode());
            }
            return;
        }
        if (result_FT.size() <= 0) {
            return;
        }
        Log.d(TAG, "AFT get face num >> " + result_FT.size());
        List<Rect> rectList = new ArrayList<>();
        for (AFT_FSDKFace faceFT : result_FT) {
            AFR_FSDKFace face = new AFR_FSDKFace(); // 用来存放提取到的人脸信息

            Rect itemRect = faceFT.getRect(); // 人脸在图片中的位置
            int degree = faceFT.getDegree(); // 人脸方向
            error_FR = AFR.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, itemRect, degree, face); // 提取人脸信息
            if (error_FR.getCode() != 0) {
                Log.d(TAG, "error_FR AFR_FSDK_ExtractFRFeature >> " + error_FR.getCode());
                if (onContrastListener != null) {
                    onContrastListener.onContrastError("error_FR AFR_FSDK_ExtractFRFeature >> " + error_FR.getCode());

                }
                continue;
            }

            byte[] faceData = face.getFeatureData();//人脸数据

            rectList.add(itemRect);
            //faceData 这张人脸的坐标 itemRect
            contrastFaceFeature(faceData, itemRect, degree, width, height);
        }
        if (onContrastListener != null) {
            onContrastListener.onContrastRects(rectList);

        }

    }


    /**
     * 对比每一帧中的每一个人脸
     *
     * @param face 人脸数据
     * @param rect 人脸坐标
     * @param ori  人脸角度
     */
    private void contrastFaceFeature(byte[] face, Rect rect, int ori, int width, int height) {
        AFR_FSDKError error_FR;

        AFR_FSDKFace fsdkFace = new AFR_FSDKFace();
        fsdkFace.setFeatureData(face);

        AFR_FSDKMatching score = new AFR_FSDKMatching(); //score用于存放人脸对比的相似度值
        if (familyFaceFRBeans == null || familyFaceFRBeans.size() == 0) {
            return;
        }
        for (FaceFRBean faceFRBean : familyFaceFRBeans) {
            error_FR = AFR.AFR_FSDK_FacePairMatching(fsdkFace, faceFRBean.getAFRFace(), score);
            if (error_FR.getCode() != 0) {
                Log.d(TAG, "error_FR  AFR_FSDK_FacePairMatching>> " + error_FR.getCode());
                if (onContrastListener != null) {
                    onContrastListener.onContrastError("error_FR AFR_FSDK_FacePairMatching >> " + error_FR.getCode());
                }
                continue;
            }
            float similarity = score.getScore();//相似度
            Log.d(TAG, "get similarity >> " + similarity);
            if (similarity > 0.5) {
                //相似度大于 50  当前人脸时家庭成员
                Log.d(TAG, "the face data from video is  ：" + faceFRBean.getmName());
                if (familyFocusFeature(faceFRBean.getmName())) {
                    //是重点关注人员
                    if (onContrastListener != null) {
                        onContrastListener.onContrastSucceed(true, faceFRBean.getmName());

                    }
                } else {
                    if (onContrastListener != null) {
                        onContrastListener.onContrastSucceed(false, faceFRBean.getmName());

                    }
                }
                return;
            }

        }
        //当前人脸是陌生人
        Log.d(TAG, "this is a stranger , rect >> " + rect.toString());
        Log.d(TAG, "start alert for face ");
        if (onContrastListener != null) {
            onContrastListener.onContrastFailed("陌生人", face);

        }
    }



    /**
     * 比对是否是重点关注人员
     *
     * @param name
     * @return
     */
    private boolean familyFocusFeature(String name) {

        if (focusFaceFRBeans == null || focusFaceFRBeans.size() == 0) {
            return false;
        }
        for (FaceFRBean faceFRBean : focusFaceFRBeans) {
            if (faceFRBean.getmName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /************** 以下是图片人脸提取 ***************/

    /***
     * 识别文件夹下的所有图片的人脸信息 并保存
     */
    public ArrayList<FaceFRBean> ObtainPicturesFeature(List<FacePictures> facePicCacheList) {
        if (facePicCacheList == null || facePicCacheList.size() == 0) {
            LogTool.e(TAG, "  facePicCacheList is null !!  pls help to check it !!");
            return null;//獲取人臉失敗
        }
        if (AFD == null || AFR == null) {
            LogTool.e(TAG, "FRToExtractFeature  AFD && AFR init failed");
            AFD = getAFD_FSDKEngine();//初始化搜集人脸
            AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
        }
        AFD_FSDKError error_FD;
        AFR_FSDKError error_FR;
        ArrayList<FaceFRBean> faceDataList = new ArrayList<>();
        for (FacePictures facePIC : facePicCacheList) {
            List<AFD_FSDKFace> result_FD = new ArrayList<>();
            error_FD = AFD.AFD_FSDK_StillImageFaceDetection(facePIC.getNV21(), facePIC.getWidth(), facePIC.getHigh(), AFD_FSDKEngine.CP_PAF_NV21, result_FD);
            if (error_FD.getCode() != 0) {
                LogTool.e(TAG, "get face data from image error, name >> " + facePIC.getName() + error_FD.getCode());
                continue;
            }
            if (result_FD.size() <= 0) {
                continue;
            }
            Log.d(TAG, "get face num from image is  >> " + result_FD.size());

            //遍历人脸数组获取人脸信息
            for (int i = 0; i < result_FD.size(); i++) {
                AFR_FSDKFace face = new AFR_FSDKFace(); // 用来存放提取到的人脸信息
                AFD_FSDKFace item = result_FD.get(i);
                Rect itemRect = item.getRect(); // 人脸在图片中的位置
                int degree = item.getDegree(); // 人脸方向
                error_FR = AFR.AFR_FSDK_ExtractFRFeature(facePIC.getNV21(), facePIC.getWidth(), facePIC.getHigh(), AFR_FSDKEngine.CP_PAF_NV21, itemRect, degree, face); // 提取人脸信息
                if (error_FR.getCode() != 0) {
                    LogTool.e(TAG, "get face data from image ,error_FR name >> " + facePIC.getName() + error_FR.getCode());
                    continue;
                }
                byte[] faceData = face.getFeatureData();//人脸数据
                FaceFRBean faceDataBean = new FaceFRBean();
                AFR_FSDKFace fsdkFace = new AFR_FSDKFace();
                fsdkFace.setFeatureData(faceData);
                faceDataBean.setAFRFace(fsdkFace);
                faceDataBean.setmName(facePIC.getName());
                faceDataBean.setmFocus(false);
                faceDataBean.setmDirection(i);

                faceDataList.add(faceDataBean);
                LogTool.d(TAG, "get face data name >> " + facePIC.getName());
            }
        }
        return faceDataList;
    }
}
