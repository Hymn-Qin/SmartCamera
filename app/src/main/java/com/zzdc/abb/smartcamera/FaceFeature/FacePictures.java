package com.zzdc.abb.smartcamera.FaceFeature;

public class FacePictures {
    private int width;
    private int high;
    private byte[] NV21;
    private String name;
    private String direction;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHigh() {
        return high;
    }

    public void setHigh(int high) {
        this.high = high;
    }

    public byte[] getNV21() {
        return NV21;
    }

    public void setNV21(byte[] NV21) {
        this.NV21 = NV21;
    }
}
