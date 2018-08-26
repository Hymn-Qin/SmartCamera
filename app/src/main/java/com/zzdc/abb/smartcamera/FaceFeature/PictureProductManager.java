package com.zzdc.abb.smartcamera.FaceFeature;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import com.zzdc.abb.smartcamera.controller.VideoGather;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class PictureProductManager implements VideoGather.VideoRawDataListener {


    private String filePath = null;

    private boolean isCreate = false;

    private PictureProductManager() {

    }

    private static class PictureProductManagerHolder {
        private static final PictureProductManager INSTANCE = new PictureProductManager();
    }

    public static final PictureProductManager getInstance() {
        return PictureProductManagerHolder.INSTANCE;
    }

    public void startCreatePicture(String filePath, boolean isCreate) {
        this.filePath = filePath;
        this.isCreate = isCreate;
    }

    @Override
    public void onVideoRawDataReady(VideoGather.VideoRawBuf buf) {
        byte[] data = buf.getData();
        if (isCreate) {
            isCreate = false;
            createImage(filePath, data, 1920, 1080);
        }

    }

    private void createImage(String imagePath, byte[] imageData, int width, int height) {
        try {
            if (imagePath == null) return;
            File file = new File(imagePath);
            FileOutputStream outputStream = new FileOutputStream(file);

            YuvImage image = new YuvImage(imageData, ImageFormat.NV21, width, height, null);
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }
}
