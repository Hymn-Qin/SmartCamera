package com.zzdc.abb.smartcamera.info;

import com.zzdc.abb.smartcamera.util.BufferPool;

public class TutkFrame extends BufferPool.Buf{
    private static final int ALIGN_EDGE = 1024 * 4;

    private byte[] mData = new byte[ALIGN_EDGE];
    private int mDataLen;
    private FrameInfo mFrameInfo = new FrameInfo();

    public byte[] getData() {
        return mData;
    }

    public void setDataLen(int len) {
        mDataLen = len;
    }

    public int getDataLen() {
        return mDataLen;
    }

    public FrameInfo getFrameInfo() {
        return mFrameInfo;
    }

    @Override
    protected void updateSize(int size) {
        if (mData.length < size) {
            int allocateSize = (size + ALIGN_EDGE);
            allocateSize = allocateSize - allocateSize % ALIGN_EDGE;
            mData = new byte[allocateSize];
        }
    }
}
