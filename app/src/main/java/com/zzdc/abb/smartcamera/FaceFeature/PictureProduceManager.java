package com.zzdc.abb.smartcamera.FaceFeature;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;

import com.zzdc.abb.smartcamera.controller.VideoGather;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class PictureProduceManager implements VideoGather.VideoRawDataListener {

    private static final String TAG = PictureProduceManager.class.getSimpleName();
    private String type;
    private String fileName;
    private long loopTime = 30 * 60 * 1000;
    private boolean isCreate = false;
    private static class PictureProduceManagerHolder {
        private static final PictureProduceManager INSTANCE = new PictureProduceManager();
    }
    public static final PictureProduceManager getInstance() {
        return PictureProduceManagerHolder.INSTANCE;
    }


    private PictureProduceManager() {

    }

    public void startCreatePicture(String picturePath, boolean isCreate) {
        this.fileName = picturePath;
        this.isCreate = isCreate;
    }

    @Override
    public void onVideoRawDataReady(VideoGather.VideoRawBuf buf) {

        byte[] data = buf.getData();

        if (isCreate) {
            isCreate = false;
            LogTool.d(TAG, "qxj-------- Creating Picture");
            createImage(fileName, data, 1920, 1080);
        }

    }
    private void createImage(String imagePath, byte[] data, int width, int height) {
        try {
            File file = new File(imagePath);
            FileOutputStream outputStream = new FileOutputStream(file);

            YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);
            image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 70, outputStream);
            LogTool.d(TAG, "qxj--------over Create Picture, this name : " + imagePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

}
