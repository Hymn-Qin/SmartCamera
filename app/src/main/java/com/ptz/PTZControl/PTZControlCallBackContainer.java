package com.ptz.PTZControl;

public class PTZControlCallBackContainer {
    public interface ViewPathCallBack {
        void onCompleted(String jsonString);
    }
}
