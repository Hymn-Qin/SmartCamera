package com.zzdc.abb.smartcamera.FaceFeature;

import com.arcsoft.facerecognition.AFR_FSDKFace;

public class FaceFRBean {
    private AFR_FSDKFace AFRFace;
    private String fileName;

    public AFR_FSDKFace getAFRFace() {
        return AFRFace;
    }

    public void setAFRFace(AFR_FSDKFace AFRFace) {
        this.AFRFace = AFRFace;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
