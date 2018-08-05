package com.zzdc.abb.smartcamera.util;
/**
 *  FILE: NV21Convertor.java
 *  功能说明：camera 1 api，数据是NV21,编码器需要的是nv12
 *  版本：1.0
 *
 */

public class NV21Convertor {

    /**
     * 数据格式说明
     * Nv21:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * VUVUVUVU
     * VUVUVUVU
     *
     * Nv12:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * UVUVUVUV
     * UVUVUVUV
     *
     * YUV420P:
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * YYYYYYYY
     * UUUU
     * UUUU
     * VVVV
     * VVVV
     */
    private static final String TAG = "NV21Convertor";
    public static void Nv21ToI420(byte[] data, byte[] dstData, int w, int h) {

        int size = w * h;
        // Y
        System.arraycopy(data, 0, dstData, 0, size);
        for (int i = 0; i < size / 4; i++) {
            dstData[size + i] = data[size + i * 2 + 1]; //U
            dstData[size + size / 4 + i] = data[size + i * 2]; //V
        }
    }

    public static void Nv21ToYuv420SP(byte[] nv21, byte[] nv12, int w, int h) {
        //LogTool.d(TAG,"W = " + w +" h = " + h);
        int size = w * h;
        // Y
        if(nv21 == null || nv12 == null)return;
        int framesize = w * h ;
        int i = 0,j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j+1] = nv21[j+framesize];
            nv12[framesize + j] = nv21[j+framesize+1];

        }
    }

    public static void YUV420PClockRot90(byte[] data, byte[] dstData, int w, int h){

        int nPos = 0;
        //旋转Y
        int k = 0;
        for(int i=0;i<w;i++)
        {
            for(int j = h -1;j >=0;j--)
            {
                dstData[k++] = data[j*w + i];
            }
        }
        //旋转U
        nPos = w*h;
        for(int i=0;i<w;i+=2)
        {
            for(int j= h/2-1;j>=0;j--)
            {
                dstData[k] = data[nPos+ j*w +i];
                dstData[k+1] = data[nPos+ j*w +i+1];
                k+=2;
            }
        }


    }
}
