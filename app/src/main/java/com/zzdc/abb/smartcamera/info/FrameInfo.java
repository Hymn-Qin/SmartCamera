package com.zzdc.abb.smartcamera.info;

import com.tutk.IOTC.Packet;

import java.io.Serializable;

/***
 *
 * filename：FrameInfo.java
 * 版本：1.0
 * Frame 相关信息
 *
 * */
public class FrameInfo implements Serializable{
    public short codec_id;
    public byte flags;
    public byte cam_index;
    public byte onlineNum;
    public byte[] reserve1 = new byte[3];
    public int reserve2;
    public long timestamp;

    public byte[] parseContent(short _codec_id, byte _flags,long aTimestamp) {

        byte[] result = new byte[16];
        byte[] arg1 = Packet.shortToByteArray_Little(_codec_id);
        byte[] arg2 = new byte[1];
        arg2[0] = _flags;

        byte[] arg3 = com.tutk.IOTC.Packet.longToByteArray_Little(aTimestamp);

        System.arraycopy(arg1, 0, result, 0, 2);
        System.arraycopy(arg2, 0, result, 2, 1);
        System.arraycopy(arg3, 0, result, 3, 4);
        return result;
    }
}
