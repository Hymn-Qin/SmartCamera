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

    public void put(ByteBuffer buffer) {
        if (buffer != null) {
            mBuffer.put(buffer);
        }
    }

    public ByteBuffer getByteBuffer() {
        return mBuffer;
    }

    public void setBufferInfo(MediaCodec.BufferInfo info) {
        mBufferInfo = info;
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
