package com.zzdc.abb.smartcamera.FaceFeature;

public class FaceConfig {

    public static final String faceAPP_Id = "A4uhGhWyQKGzzpGkGYbkqrGPNsw2WpzBEGRy3CtyVsSi";
    public static final String faceFD_Key = "ESdkhWawr2CPdBHCnSRnwuNbiW6RQSG8LHUNWu3J3ETw";
    public static final String faceFR_KEY = "ESdkhWawr2CPdBHCnSRnwuNisuMYV3oZRTUdTRM6RxuY";
    public static final int maxFacesNUM = 1;//指定检测的人脸数量
    public static final int scale = 32;//指定支持检测的最小人脸尺寸

    public static final int maxContrastFacesNUM = 4;//指定检测的人脸数量
    public static boolean isContrast = true;

    public static String imagePath;//保存第一帧路径
    public static String title; //保存视频和图片名字
    public static String tmpFileName; // 保存视频名字
    public static long tmpTime; //开始录制时间
    public static String tmpMediaFile; //保存视频路径
}
