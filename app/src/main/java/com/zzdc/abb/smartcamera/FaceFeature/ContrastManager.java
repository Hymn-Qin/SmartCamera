package com.zzdc.abb.smartcamera.FaceFeature;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
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
import com.zzdc.abb.smartcamera.TutkBussiness.SDCardBussiness;
import com.zzdc.abb.smartcamera.controller.AlertMediaMuxer;
import com.zzdc.abb.smartcamera.controller.AvMediaRecorder;
import com.zzdc.abb.smartcamera.controller.EncoderBuffer;
import com.zzdc.abb.smartcamera.controller.VideoGather;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static com.zzdc.abb.smartcamera.FaceFeature.FaceConfig.isContrast;

public class ContrastManager implements VideoGather.VideoRawDataListener {

    private static final String TAG = "qxj";


    private CopyOnWriteArrayList<FaceFRBean> familyFaceFRBeans;//家庭成员人脸
    private CopyOnWriteArrayList<FaceFRBean> focusFaceFRBeans;//家庭重点关注成员人脸

    private AFD_FSDKEngine AFD;
    private AFR_FSDKEngine AFR;
    private AFT_FSDKEngine AFT;

    private boolean isStartImageOK = false;
    private boolean isStartImage = false;

    private LinkedBlockingQueue<byte[]> videoDatas = new LinkedBlockingQueue<>();

    private static class FeatureContrastManagerHolder {
        private static final ContrastManager INSTANCE = new ContrastManager();
    }

    private ContrastManager() {
    }

    public static final ContrastManager getInstance() {
        return FeatureContrastManagerHolder.INSTANCE;
    }

    private OnContrastListener onContrastListener = null;

    public void setOnContrastListener(OnContrastListener onContrastListener) {
        this.onContrastListener = onContrastListener;
    }

    Thread contrastThread = new Thread() {
        @Override
        public void run() {
            super.run();
            while (true) {
                try {
                    byte[] videoData = videoDatas.take();
                    startContrastFeature(videoData, 0, 0);
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

    /***
     * 把传入的数据库数据格式转换为人脸识别引擎需要的数据格式
     * 数据库格式     FaceDatabase
     * 人脸数据格式   AFR_FSDKFace
     * 最终转换格式   FaceFRBean
     * @param faceDataBeans 数据库查询到的人脸数据  包括 人脸数据byte[] faceData  人脸名字 String name
     * @return 返回
     */
    private ArrayList<FaceFRBean> faceDataToFaceFR(List<FaceDatabase> faceDataBeans) {
        ArrayList<FaceFRBean> faceFRBeans = new ArrayList<>();
        for (FaceDatabase faceDatabase : faceDataBeans) {

            AFR_FSDKFace fsdkFace = new AFR_FSDKFace(faceDatabase.face);
//            fsdkFace.setFeatureData(faceDatabase.face);//取出人脸数据转换
            FaceFRBean frBean = new FaceFRBean();
            frBean.setAFRFace(fsdkFace);
            frBean.setFileName(faceDatabase.name);//人脸名字
            faceFRBeans.add(frBean);
        }

        return faceFRBeans;
    }

    private FaceFRBean faceDataToFaceFR(byte[] faceData,  String name) {
        AFR_FSDKFace fsdkFace = new AFR_FSDKFace();
        fsdkFace.setFeatureData(faceData);//取出人脸数据转换
        FaceFRBean frBean = new FaceFRBean();
        frBean.setAFRFace(fsdkFace);
        frBean.setFileName(name);//人脸名字

        return frBean;
    }

    /**
     * 添加家庭人脸
     *
     * @param faceData 添加的人脸信息
     */

    public void addFamilyFaceFeature(byte[] faceData,  String name) {
        familyFaceFRBeans.add(faceDataToFaceFR(faceData, name));
    }

    public void addFamilyFaceFeature(List<FaceDatabase> faceDataBeans) {
        familyFaceFRBeans.addAll(faceDataToFaceFR(faceDataBeans));
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
        focusFaceFRBeans.addAll(faceDataToFaceFR(faceDataBeans));
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
        AFD_FSDKError err = FSDKEngine.AFD_FSDK_InitialFaceEngine(FaceConfig.faceAPP_Id, FaceConfig.faceFD_Key, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, FaceConfig.scale, FaceConfig.maxContrastFacesNUM);
        if (err.getCode() == 0) {
            return FSDKEngine;
        } else {
            Log.d(TAG, "getAFD_FSDKEngine >> AFD_FSDK_InitialFaceEngine = " + err.getCode());
            return null;
        }
    }

    //初始化人脸对比引擎
    private static AFR_FSDKEngine getAFR_FSDKEngine() {
        AFR_FSDKEngine AFREngine = new AFR_FSDKEngine();
        AFR_FSDKError error = AFREngine.AFR_FSDK_InitialEngine(FaceConfig.faceAPP_Id, FaceConfig.faceFR_KEY);
        if (error.getCode() == 0) {
            return AFREngine;
        } else {
            Log.d(TAG, "getAFR_FSDKEngine >> AFR_FSDK_InitialEngine = " + error.getCode());
            return null;
        }
    }

    //初始化人脸追踪引擎
    private static AFT_FSDKEngine getAFT_FSDKEngine() {
        AFT_FSDKEngine FTDKEngine = new AFT_FSDKEngine();
        AFT_FSDKError err = FTDKEngine.AFT_FSDK_InitialFaceEngine(FaceConfig.faceAPP_Id, FaceConfig.faceFT_KEY, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, FaceConfig.scaleFT, FaceConfig.maxContrastFacesNUM);
        if (err.getCode() == 0) {
            return FTDKEngine;
        } else {
            Log.d(TAG, "getAFT_FSDKEngine >> AFT_FSDK_InitialFaceEngine = " + err.getCode());
            return null;
        }
    }

    //人脸比对 设置 家庭人员人脸数据 重点关注人员人脸数据
    public void setFamilyFace(List<FaceDatabase> familyFaceDataBeans) {
        ArrayList<FaceFRBean> faceFRBeans = faceDataToFaceFR(familyFaceDataBeans);
        familyFaceFRBeans.addAll(faceFRBeans);
    }

    public void setFamilyFocusFace(List<FaceDatabase> focusFaceDataBeans) {
        ArrayList<FaceFRBean> faceFRBeans = faceDataToFaceFR(focusFaceDataBeans);
        focusFaceFRBeans.addAll(faceFRBeans);
    }

    public void setSwitchContrast(boolean isOK) {
        if (isContrast != isOK) {
            isContrast = isOK;
            if (isContrast) {
                familyFaceFRBeans = new CopyOnWriteArrayList<>();
                focusFaceFRBeans = new CopyOnWriteArrayList<>();
                InitFaceFeatureManager();
                contrastThread.start();
            } else {
                UninitFaceFeatureManager();
            }
        }
    }

    public void setVideoData(byte[] data, int width, int height) {
        createImage(data, width, height);//生成图片
        startContrastFeature(data, width, height);//设别
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
        if (familyFaceFRBeans == null || familyFaceFRBeans.size() == 0) {
            return;
        }
        if (AFT == null || AFR == null) {

            if (AFR == null) {
                Log.d(TAG, "startContrastFeature  AFR init failed");
                AFR = getAFR_FSDKEngine();//初始化人脸特征提取比对
            }
            if (AFT == null) {
                Log.d(TAG, "startContrastFeature  AFT init failed");
                AFT = getAFT_FSDKEngine();
            }
        }
        AFR_FSDKError error_FR;
        AFT_FSDKError error_FT;
        List<AFT_FSDKFace> result_FT = new ArrayList<>();
        error_FT = AFT.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result_FT);
        if (error_FT.getCode() != 0) {
            Log.d(TAG, "视频人脸识别出错 >> " + error_FT.toString());
            return;
        }
        if (result_FT.size() <= 0) {
            return;
        }
        Log.d(TAG, "视频中人脸个数 >> " + result_FT.size());
        List<Rect> rectList = new ArrayList<>();
        for (AFT_FSDKFace faceFT : result_FT) {
            AFR_FSDKFace face = new AFR_FSDKFace(); // 用来存放提取到的人脸信息

            Rect itemRect = faceFT.getRect(); // 人脸在图片中的位置
            int degree = faceFT.getDegree(); // 人脸方向
            error_FR = AFR.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, itemRect, degree, face); // 提取人脸信息
            if (error_FR.getCode() != 0) {
                Log.d(TAG, "这一帧中人脸特征数据提取错误");
                continue;
            }

            byte[] faceData = face.getFeatureData();//人脸数据
            rectList.add(itemRect);
            //faceData 这张人脸的坐标 itemRect
            contrastFaceFeature(faceData, itemRect, degree, width, height);
        }

        onContrastListener.OnContrastSuccee(rectList);

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
                Log.d(TAG, "人脸比对错误 error " + error_FR.toString());
                continue;
            }
            float similarity = score.getScore();//相似度
            Log.d(TAG, "人脸比对成功 相似度 " + similarity);
            if (similarity > 0.5) {
                //相似度大于 50  当前人脸时家庭成员
                Log.d(TAG, "人脸比对结果 是家庭成员 ：" + faceFRBean.getFileName());
                if (familyFocusFeature(faceFRBean.getFileName())) {
                    //是重点关注人员
                    //开始生成图片

                }
                return;
            }
            //当前人脸是陌生人
            Log.d(TAG, "人脸比对结果 是陌生人 坐标： " + rect.toString());
            Log.d(TAG, "开始追踪陌生人 ");
            startAlertVideo();
        }

    }

    private void startAlertVideo() {
        AvMediaRecorder mAvMediaRecorder = AvMediaRecorder.getInstance();
        AlertMediaMuxer alertMediaMuxer = AlertMediaMuxer.getInstance();
        if (!AlertMediaMuxer.AlertRecordRunning) {
            mAvMediaRecorder.startAlertRecord();
        } else {
            mAvMediaRecorder.resetStopTime(1);
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
        if (focusFaceFRBeans == null || focusFaceFRBeans.size() == 0) {
            return false;
        }
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

    private boolean familyFocusFeature(String name) {

        if (focusFaceFRBeans == null || focusFaceFRBeans.size() == 0) {
            return false;
        }
        for (FaceFRBean faceFRBean : focusFaceFRBeans) {
            if (faceFRBean.getFileName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void startFocusImage(boolean ok) {
        isStartImageOK = ok;
    }

    public void startFocusImage(byte[] data) {
        if (isStartImage && isStartImageOK) {
            byteToImage(data);
        }
    }

    //byte数组到图片
    private void byteToImage(byte[] data) {
        if (data.length < 3) return;
        try {
            File file = getImageFile();
            if (file == null) return;
            FileOutputStream outputStream = new FileOutputStream(file);
            YuvImage image = new YuvImage(data, ImageFormat.NV21, 2, 2, null);
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private File getImageFile() {
        SDCardBussiness tmpBussiness = SDCardBussiness.getInstance();
        if (tmpBussiness.isSDCardAvailable()) {
            //SD卡 DCIM目录
            String tmpDir = tmpBussiness.getSDCardVideoRootPath() + "/" + "ALERT";
            File mkDir = new File(tmpBussiness.getSDCardVideoRootPath(), "ALERT");
            if (!mkDir.exists()) {
                mkDir.mkdirs();   //目录不存在，则创建
            }
            Date tmpDate = new Date(System.currentTimeMillis());
            SimpleDateFormat tmpDateFormat = new SimpleDateFormat("'VID'_yyyyMMdd_HHmmss");
            String name = tmpDateFormat.format(tmpDate);
            try {
                String imagePath = name + ".jpg";
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
            facePIC.setName(faceName);
            facePIC.setFileName(path);
            facePIC.setWidth(temp.getWidth());
            facePIC.setHigh(exeHigh(temp.getHeight()));
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


    private static String faceName;
    private static String faceImagePath;
    /***
     * 识别文件夹下的所有图片的人脸信息 并保存
     */
    public ArrayList<FaceDatabase> getFRToExtractFeature(String faceName, String faceImagePath) {
        ContrastManager.faceName = faceName;
        ContrastManager.faceImagePath = faceImagePath;
        List<FacePictures> facePicCacheList = getFaceImageList(faceImagePath);
        ArrayList<FaceDatabase> faceDataList = new ArrayList<>();
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
            Log.d(TAG, "facePIC" + facePIC.getNV21() + " width" + facePIC.getWidth() + " height" + facePIC.getHigh() + " fileName" + facePIC.getFileName());
            error_FD = AFD.AFD_FSDK_StillImageFaceDetection(facePIC.getNV21(), facePIC.getWidth(), facePIC.getHigh(), AFD_FSDKEngine.CP_PAF_NV21, result_FD);
            Log.d(TAG, "AFD.AFD_FSDK_StillImageFaceDetection  !! ResultCode = " + error_FD.getCode() + " facePIC = " + facePIC.getFileName());
            if (error_FD.getCode() != 0) {
                Log.d(TAG, "人脸数据提取错误 name >> " + facePIC.getName() + error_FD.toString());
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
                    Log.d(TAG, "人脸特征数据提取错误 name >> " + facePIC.getName() + error_FR.toString());
                    continue;
                }
                byte[] faceData = face.getFeatureData();//人脸数据
                FaceDatabase faceDataBean = new FaceDatabase();
                faceDataBean.face = faceData;
                faceDataBean.name = facePIC.getName();
                faceDataBean.direction = facePIC.getFileName();
                faceDataList.add(faceDataBean);
                Log.d(TAG, "获取到人脸数据 name >> " + facePIC.getName());
            }
        }
        return faceDataList;
    }


    private void createImage(byte[] data, int width, int height) {

//        MediaExtractor extractor = new MediaExtractor();
//        extractor.setDataSource();

        if (!FaceConfig.startImage) {
            return;
        }
        FaceConfig.startImage = false;
        FaceConfig.imagePath = getImagePath();
        Log.d(TAG, "开始生成图片 image = " + FaceConfig.imagePath);
        try {
            File file = new File(FaceConfig.imagePath);
            FileOutputStream outputStream = new FileOutputStream(file);

            YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "生成图片完成 image = " + FaceConfig.imagePath);
    }

    private String getImagePath () {
        String tmpPath = Utils.getSDPath("ALERT");
        if (tmpPath == null) return null;
        String tmpFileName = FaceConfig.imageTitle + ".jpg";

        return tmpPath + '/' + tmpFileName;
    }
    public static class FacePictures {
        private int width;
        private int high;
        private byte[] NV21;
        private String name;
        private String fileName;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

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
