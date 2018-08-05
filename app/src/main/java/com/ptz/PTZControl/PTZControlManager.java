package com.ptz.PTZControl;

import com.ptz.motorControl.MotorManager;
import com.zzdc.abb.smartcamera.util.LogTool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;

public class PTZControlManager {
    private static final String TAG = PTZControlManager.class.getSimpleName();

    private MotorManager mMotorManager = MotorManager.getManger();

    private static PTZControlManager ptzControlManager = new PTZControlManager();

    public static PTZControlManager getInstance() {
        return ptzControlManager;
    }

    public void motorContorl(String jsonStr) {
        LogTool.d(TAG, "motorContorl:command, " + jsonStr);

        JSONTokener jsonTokener = new JSONTokener(jsonStr);
        JSONObject ptzObject;
        String direction = null;
        String isLongPress = null;
        int viewId;

        try {
            ptzObject = (JSONObject) jsonTokener.nextValue();
            direction = ptzObject.getString("direction");
            isLongPress = ptzObject.getString("isLongPress");
            viewId = ptzObject.getInt("viewId");
            LogTool.d(TAG, "motorContorl: " + direction + ",islongpress:" + isLongPress + ",viewId:" + viewId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (direction == null){
            LogTool.e(TAG, "PTZ_CTL  direction=null");
            return;
        }
        switch (direction) {
            case PTZCommand.PTZ_DIRECTION_LEFT:
                mMotorManager.moveLeft(isLongPress);
                break;
            case PTZCommand.PTZ_DIRECTION_RIGHT:
                mMotorManager.moveRight(isLongPress);
                break;
            case PTZCommand.PTZ_DIRECTION_UP:
                mMotorManager.moveUp(isLongPress);
                break;
            case PTZCommand.PTZ_DIRECTION_DOWN:
                mMotorManager.moveDown(isLongPress);
                break;
            case PTZCommand.PTZ_DIRECTION_STOP:
                mMotorManager.stopMove();
                break;
            case PTZCommand.PTZ_DIRECTION_VIEW:
                LogTool.d(TAG,"not use command");
                break;
            default:
                break;
        }
    }

    public void PTZViewGet(PTZControlCallBackContainer.ViewPathCallBack callBack) {
        int xAxis = mMotorManager.getHorizonCurrenStep();
        int yAxis = mMotorManager.getVerticalCurrenStep();

        JSONObject temp = new JSONObject();
        try {
            temp.put("type", "PTZ_VIEW_GET_RESPONSE");
            temp.put("xAxis", xAxis);
            temp.put("yAxis", yAxis);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        callBack.onCompleted(temp.toString());
    }

    public void PTZViewTurnTo(String receiveJsonStr) {
        JSONObject ptzObj;
        int xAxis = 0,yAxis = 0;
        try {
            ptzObj = new JSONObject(receiveJsonStr);
            xAxis  = ptzObj.getInt("xAxis");
            yAxis = ptzObj.getInt("yAxis");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        LogTool.d(TAG,"xAxis:"+xAxis+",yAxis:"+yAxis);
        mMotorManager.movePosition(xAxis,yAxis);
    }

    public void PTZHorizontalPathStart(){
        mMotorManager.startHorizontalPath();
    }

    public void PTZVerticalPathStart(){
        mMotorManager.startVerticalPath();
    }

    public void PTZPathStart(String receiveJsonStr) {
        LogTool.e(TAG,"PTZPathStart");
        JSONTokener jsonTokener = new JSONTokener(receiveJsonStr);
        JSONObject ptzObject;
        JSONArray array = null;
        int sleepTime = 0;
        try {
            ptzObject = (JSONObject) jsonTokener.nextValue();
            array = ptzObject.getJSONArray("pathView");
            sleepTime = ptzObject.getInt("sleepTime");
            LogTool.d(TAG,"PTZPathStart sleepTime:"+sleepTime);
        } catch (JSONException e) {
            LogTool.e(TAG,"PTZPathStart jiexi error");
            e.printStackTrace();
        }
        if (array == null){
            LogTool.e(TAG,"PTZPathStart pathview array  prase Error");
            return;
        }
        ArrayList<MotorManager.PathPosition> pathPositions = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                MotorManager.PathPosition pathPosition = new MotorManager.PathPosition();
                JSONObject temp = (JSONObject) array.get(i);
                pathPosition.setxAxis(temp.getInt("xAxis"));
                pathPosition.setyAxis(temp.getInt("yAxis"));
                pathPosition.setSleepTime(sleepTime*1000);
                pathPositions.add(pathPosition);
                LogTool.d(TAG,"xAxis="+pathPosition.getxAxis()+",yAxis="+pathPosition.getyAxis()+",sleepTime="+pathPosition.getSleepTime());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        mMotorManager.starPath(pathPositions);
    }
}
