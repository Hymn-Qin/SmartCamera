package com.zzdc.abb.smartcamera.common;

import android.media.MediaFormat;
import android.os.Environment;

import java.io.File;

public class Constant {

    public static final String DIRCTORY =  Environment.getExternalStorageDirectory() + File.separator
            + Environment.DIRECTORY_DCIM;

    //TUTK
    public static  String TUTK_DEVICE_UID = "";
    public static final String ACTION_START_IP_CAMERA = "com.zzdc.action.START_IP_CAMERA";
    public static final String ACTION_STOP_IP_CAMERA = "com.zzdc.action.STOP_IP_CAMERA";
    public static final int START_RECORD = 101;
    public static final int STOP_RECORD = 102;
    public static final int INIT_TUTK_UID = 103;

    public static int IFRAME_INTERVAL = 1;    //关键帧间隔
    public static byte[] PPS;   //配置帧
    public static byte[] AAC_CONFIG; //

}
