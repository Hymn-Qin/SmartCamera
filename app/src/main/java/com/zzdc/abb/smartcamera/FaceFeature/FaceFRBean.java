package com.zzdc.abb.smartcamera.FaceFeature;

import com.arcsoft.facerecognition.AFR_FSDKFace;

public class FaceFRBean {
    public static final int NULL = 0;
    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 3;
    public static final int RIGHT = 4;
    public static final int MIDDLE = 5;

    private AFR_FSDKFace AFRFace;
    private String mName;
    private int mDirection = NULL;
    private boolean mFocus = false;

    public String getmName() {
        return mName;
    }

    public void setmName(String mName) {
        this.mName = mName;
    }

    public int getmDirection() {
        return mDirection;
    }

    public void setmDirection(int mDirection) {
        this.mDirection = mDirection;
    }

    public boolean ismFocus() {
        return mFocus;
    }

    public void setmFocus(boolean mFocus) {
        this.mFocus = mFocus;
    }

    public AFR_FSDKFace getAFRFace() {
        return AFRFace;
    }

    public void setAFRFace(AFR_FSDKFace AFRFace) {
        this.AFRFace = AFRFace;
    }

}
