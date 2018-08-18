package com.zzdc.abb.smartcamera.info;

public class MediaDurationInfo {
    long mStart;
    long mEnd;

    public MediaDurationInfo(long start, long end) {
        mStart = start;
        mEnd = end;
    }

    public long getStart() {
        return mStart;
    }

    public long getEnd() {
        return mEnd;
    }
}
