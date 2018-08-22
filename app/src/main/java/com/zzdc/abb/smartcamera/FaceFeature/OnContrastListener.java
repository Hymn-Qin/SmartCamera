package com.zzdc.abb.smartcamera.FaceFeature;

import android.graphics.Rect;

import java.util.List;

public interface OnContrastListener {
    void OnContrastSuccee(List<Rect> rects);
    void OnContrastFiled(String msg, String name);
    void OnContrastError(String msg);
}
