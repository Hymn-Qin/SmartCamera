package com.zzdc.abb.smartcamera.FaceFeature;

import java.util.ArrayList;

public class FaceFromClient {
    String name;
    String type;
    ArrayList<Data> dataArrayList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ArrayList<Data> getDataArrayList() {
        return dataArrayList;
    }

    public void setDataArrayList(ArrayList<Data> dataArrayList) {
        this.dataArrayList = dataArrayList;
    }

    public class Data{
        String direction;
        byte[] faceData;

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public byte[] getFaceData() {
            return faceData;
        }

        public void setFaceData(byte[] faceData) {
            this.faceData = faceData;
        }
    }
}
