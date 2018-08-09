package com.zzdc.abb.smartcamera.controller;

import android.media.MediaCodec;

import com.zzdc.abb.smartcamera.util.BufferPool;

import java.nio.ByteBuffer;

public class EncoderBuffer extends BufferPool.Buf {
    private static final String TAG =  EncoderBuffer.class.getSimpleName();
    private static final int ALIGN_EDGE = 1024 * 4;

    private ByteBuffer mBuffer;
    private MediaCodec.BufferInfo mBufferInfo;
    private int mTrack;

    public EncoderBuffer() {
        mBuffer = ByteBuffer.allocate(ALIGN_EDGE);
        mBufferInfo = new MediaCodec.BufferInfo();
    }

    @Override
    protected void updateSize(int size) {
        if (mBuffer.array().length < size) {
            int allocateSize = size + ALIGN_EDGE;
            allocateSize = allocateSize - allocateSize % ALIGN_EDGE;
            mBuffer = ByteBuffer.allocate(allocateSize);
        }
    }

    @Override
    protected void clear() {
        mBuffer.clear();
    }

    public void put(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        mBuffer.clear();
        mBuffer.put(buffer);
        mBufferInfo.set(0, info.size, info.presentationTimeUs, info.flags);
      //  mBuffer.position(mBufferInfo.offset);
        mBuffer.position(0);
        mBuffer.limit(info.size);
        buffer.position(info.offset);
    }

    public ByteBuffer getByteBuffer() {
        return mBuffer;
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        return mBufferInfo;
    }

    public void setTrack(int track) {
        mTrack = track;
    }

    public int getTrack() {
        return mTrack;
    }
}
