package com.zzdc.abb.smartcamera.controller;

import android.media.MediaCodec;
import android.util.Log;

import com.zzdc.abb.smartcamera.util.BufferPool;

import java.nio.ByteBuffer;

public class EncoderBuffer extends BufferPool.Buf {
    private static final String TAG =  EncoderBuffer.class.getSimpleName();
    private MediaCodec mEncoder;
    private ByteBuffer mOutputBuffer;
    private MediaCodec.BufferInfo mOutputBufferInfo;
    private int mOutputBufferIndex;

    private int mTrack;

    public void setEncoder(MediaCodec encoder) {
        mEncoder = encoder;
    }

    public MediaCodec getEncoder() {
        return mEncoder;
    }

    public void setOutputBuffer(ByteBuffer buf) {
        mOutputBuffer = buf;
    }

    public ByteBuffer getOutputBuffer() {
        return mOutputBuffer;
    }

    public void setOutputBufferInfo(MediaCodec.BufferInfo info) {
        mOutputBufferInfo = info;
    }

    public MediaCodec.BufferInfo getOutputBufferInfo() {
        return mOutputBufferInfo;
    }

    public void setOutputBufferIndex(int i) {
        mOutputBufferIndex = i;
    }

    public int getOutputBufferIndex() {
        return mOutputBufferIndex;
    }

    public void setTrack(int track) {
        mTrack = track;
    }

    public int getTrack() {
        return mTrack;
    }
}
