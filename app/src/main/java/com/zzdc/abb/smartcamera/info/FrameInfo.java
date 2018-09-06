package com.zzdc.abb.smartcamera.info;

import android.graphics.Rect;

import com.tutk.IOTC.Packet;

import java.io.Serializable;
import java.util.List;

/***
 *
 * filename：FrameInfo.java
 * 版本：1.0
 * Frame 相关信息
 *
 * */
public class FrameInfo implements Serializable{
    public static final int dataLength = 112;
    public short codec_id;  //2byte
    public byte flags;  //1byte
    public byte cam_index;  //1byte
    public byte onlineNum;  //1byte
    public byte[] reserve1 = new byte[3];  //3byte
    public long timestamp;  //8byte
    public byte[] mRects = new byte[96];  //96byte
    byte[] result = new byte[dataLength];

    public byte[] parseContent() {
        byte[] code = Packet.shortToByteArray_Little(codec_id);
        byte[] flag = new byte[]{flags};
        byte[] camIndex = new byte[]{cam_index};
        byte[] paseOnLineNum = new byte[]{onlineNum};
        byte[] reserve = reserve1;
        byte[] frameTime = Packet.longToByteArray_Little(timestamp);
        byte[] rects = mRects;

        System.arraycopy(code, 0, result, 0, 2);
        System.arraycopy(flag, 0, result, 2, 1);
        System.arraycopy(camIndex, 0, result, 3, 1);
        System.arraycopy(paseOnLineNum, 0, result, 4, 1);
        System.arraycopy(reserve, 0, result, 5, 3);
        System.arraycopy(frameTime, 0, result, 8, 8);
        System.arraycopy(rects, 0, result, 16, 96);
        return result;
    }

    public FrameInfo parseByteArrayToFrameInfo (byte[] byteArray) {
        FrameInfo frameInfo = new FrameInfo();
        byte[] code = new byte[2];
        byte[] flag = new byte[1];
        byte[] camIndex = new byte[1];
        byte[] paseOnLineNum = new byte[1];
        byte[] reserve = new byte[3];
        byte[] frameTime = new byte[8];
        byte[] rects = new byte[96];
        System.arraycopy(byteArray, 0, code, 0, 2);
        frameInfo.codec_id = Packet.byteArrayToShort_Little(code,0);
        System.arraycopy(byteArray, 2, flag, 0, 1);
        frameInfo.flags = flag[0];
        System.arraycopy(byteArray, 3, camIndex, 0, 1);
        frameInfo.cam_index = camIndex[0];
        System.arraycopy(byteArray, 4, paseOnLineNum, 0, 1);
        frameInfo.onlineNum = paseOnLineNum[0];
        System.arraycopy(byteArray, 5, reserve, 0, 3);
        frameInfo.reserve1 = reserve;
        System.arraycopy(byteArray, 8, frameTime, 0, 8);
        frameInfo.timestamp = Packet.byteArrayToLong_Little(frameTime,0);
        System.arraycopy(byteArray, 16, rects, 0, 96);
        frameInfo.mRects = rects;

        return frameInfo;
    }
}
