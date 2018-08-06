package com.ptz.motorControl;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;

import java.util.ArrayList;

public class MotorManager {
    private static final String TAG = "MotorManger";

    private boolean stopFlag = true;
    private static final String Receiver_BroadCast_Filter = "com.foxconn.household.get.view";
    private static final String Send_BroadCast_Filter = "com.foxconn.household.request.view";
    private static final int Normal_Speed = 500;
    private static final int Slow_Speed = 250;

    private static MotorManager mManger = new MotorManager();

    public static MotorManager getManger() {
        return mManger;
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("motor_jni");
    }

    private native static int open();

    private native static int sendCmd(int type, int cmd, int parameter);

    private native void close();

    public void initMotor() {
        int ret = open();
        if (ret > 0)
            Log.e(TAG, "MotorManger: open motor sucess");
        else
            Log.e(TAG, "MotorManger: open motor fail");
        //register Appliances BroadcastReceiver
        AppliancesBroadcastReceiver broadcastReceiver = new AppliancesBroadcastReceiver();
        IntentFilter filter = new IntentFilter(Receiver_BroadCast_Filter);
        SmartCameraApplication.getContext().registerReceiver(broadcastReceiver, filter);
    }

    public void closeMotor() {
        close();
    }

    public void stopMove() {
        stopPath();
        Thread moveStopThread = new Thread(new Runnable() {
            @Override
            public void run() {
                sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_STOP, -1);
                Log.d(TAG, "stopMove: ");
            }
        });
        moveStopThread.start();
    }

    public void moveLeft(String isLongPress) {
        stopPath();
        final String isLongPressStr = isLongPress;
        Thread moveLeftThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (MotorCmd.DEVICE_POSE) {
                    if ("false".equals(isLongPressStr)) {
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_DEASIL, -1);
                    } else {
                        int maxStep = getHorizonMaxStep();
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, maxStep);
                    }
                } else {
                    if ("false".equals(isLongPressStr)) {
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_WIDDERSHINS, -1);
                    } else {
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, 0);
                    }
                }

            }
        });
        moveLeftThread.start();
    }

    public void moveRight(String isLongPress) {
        stopPath();
        final String isLongPressStr = isLongPress;
        Thread moveRightThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (MotorCmd.DEVICE_POSE) {
                    if ("false".equals(isLongPressStr)) {
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_WIDDERSHINS, -1);
                    } else {
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, 0);
                    }
                } else {
                    if ("false".equals(isLongPressStr)) {
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_DEASIL, -1);
                    } else {
                        int maxStep = getHorizonMaxStep();
                        sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, maxStep);
                    }
                }

            }
        });
        moveRightThread.start();
    }

    public void moveUp(String isLongPress) {
        stopPath();
        final String isLongPressStr = isLongPress;
        Thread moveUpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (MotorCmd.DEVICE_POSE) {
                    if ("false".equals(isLongPressStr)) {
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_WIDDERSHINS, -1);
                        Log.d(TAG, "moveUp:  " + ret);
                    } else {
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, 0);
                        Log.d(TAG, "moveUp long press:  " + ret);
                    }
                } else {
                    if ("false".equals(isLongPressStr)) {
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_DEASIL, -1);
                        Log.d(TAG, "moveDown: " + ret);
                    } else {
                        int maxStep = getVerticalMaxStep();
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, maxStep);
                        Log.d(TAG, "moveDown: " + ret);
                    }
                }

            }
        });
        moveUpThread.start();
    }

    public void moveDown(String isLongPress) {
        stopPath();
        final String isLongPressStr = isLongPress;
        Thread moveDownThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (MotorCmd.DEVICE_POSE) {
                    if ("false".equals(isLongPressStr)) {
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_DEASIL, -1);
                        Log.d(TAG, "moveDown: " + ret);
                    } else {
                        int maxStep = getVerticalMaxStep();
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, maxStep);
                        Log.d(TAG, "moveDown: " + ret);
                    }
                } else {
                    if ("false".equals(isLongPressStr)) {
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_WIDDERSHINS, -1);
                        Log.d(TAG, "moveUp:  " + ret);
                    } else {
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, 0);
                        Log.d(TAG, "moveUp long press:  " + ret);
                    }
                }

            }
        });
        moveDownThread.start();
    }

    public void movePosition(final int horizontal, final int vertical) {
        Thread movePositionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: " + "movePosition");
                sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, horizontal);
                sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, vertical);
            }
        });
        movePositionThread.start();
    }

    private int moveSyncPosition(int horizontal, int vertical) {
        int ret;
        int ret1 = sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, horizontal);
        int ret2 = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, vertical);
        if (ret1 >= 0 && ret2 >= 0)
            ret = 1;
        else
            ret = -1;
        return ret;
    }

//    public int[] getPosition() {
//        int mVerticalPosition = getVerticalCurrenStep();
//        int mHorizontalPosition = getHorizonCurrenStep();
//        int mPosition[] = {mHorizontalPosition, mVerticalPosition};
//        return mPosition;
//    }

    public int getHorizonCurrenStep() {
        return sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_CURRENT_STEP, 0);
    }

    public int getVerticalCurrenStep() {
        return sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_CURRENT_STEP, 0);
    }

    private int getHorizonMaxStep() {
        return sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_MAX_STEP, 0);
    }

    private int getVerticalMaxStep() {
        return sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_MAX_STEP, 0);
    }

    public void startHorizontalPath(){
        stopPath();
        if (sendCmd(MotorCmd.HORIZONTAL_MOTOR,MotorCmd.MOTOR_SET_INTERVAL,Slow_Speed) >= 0){
            LogTool.d(TAG,"set horizintal motor Frequency success");
        }
        if (startHoriziontalThread == null) {
            startHoriziontalThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    stopFlag = false;
                    while(!stopFlag){
                        int ret = sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, 0);
                        if (Thread.interrupted())
                            return;
                        if (ret >= 0){
                            int maxStep = getHorizonMaxStep();
                            int ret2 = sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_TURN_TO, maxStep);
                            if (Thread.interrupted())
                                return;
                            if (ret2 >= 0){
                                LogTool.d(TAG,"startHorizontalPath success");
                            }else {
                                stopFlag = true;
                                startHoriziontalThread = null;
                                LogTool.e(TAG,"startHorizontalPath error: turn to maxstep error, ret2="+ret2);
                                break;
                            }
                        }else {
                            stopFlag = true;
                            startHoriziontalThread = null;
                            LogTool.e(TAG,"startHorizontalPath error: turn to 0 error,ret = "+ret);
                            break;
                        }
                    }
                }
            });
            startHoriziontalThread.start();
        }
    }

    public void startVerticalPath(){
        stopPath();
        if (sendCmd(MotorCmd.VERTICAL_MOTOR,MotorCmd.MOTOR_SET_INTERVAL,Slow_Speed) >= 0){
            LogTool.d(TAG,"set horizintal motor Frequency success");
        }
        if (startVericalThread == null)
        {
            startVericalThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    stopFlag = false;
                    while(!stopFlag){
                        int ret = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, 0);
                        if (Thread.interrupted())
                            return;
                        if (ret >= 0){
                            int maxStep = getVerticalMaxStep();
                            int ret2 = sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_TURN_TO, maxStep);
                            if (Thread.interrupted())
                                return;
                            if (ret2 >= 0){
                                LogTool.d(TAG,"startVerticalPath success");
                            }else {
                                stopFlag = true;
                                startVericalThread = null;
                                LogTool.e(TAG,"startVerticalPath error: turn to maxstep error");
                                break;
                            }
                        } else {
                            stopFlag = true;
                            startVericalThread = null;
                            LogTool.e(TAG,"startVerticalPath error: turn to 0 error,ret = "+ret);
                            break;
                        }
                    }
                }
            });
            startVericalThread.start();
        }
    }

    private int pathFlag;
    private Thread startHoriziontalThread = null;
    private Thread startVericalThread = null;
    private Thread startPathThread = null;
    private ArrayList<PathPosition> pathPositionsArrayList = null;

    public void starPath(final ArrayList<PathPosition> arrayList) {
        stopPath();

        pathPositionsArrayList = arrayList;
        if (startPathThread == null) {
            startPathThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    stopFlag = false;
//                    LogTool.d(TAG,"x ="+arrayList.get(0).xAxis+",y= "+arrayList.get(0).yAxis+",sleepTime="+arrayList.get(0).sleepTime);
                    if (moveSyncPosition(arrayList.get(0).xAxis, arrayList.get(0).yAxis) >= 0){
                        LogTool.d(TAG,"starPath move to 0 position success");
                    }else {
                        stopFlag = true;
                        startPathThread = null;
                        LogTool.d(TAG,"starPath error");
                        return;
                    }
                    if (Thread.interrupted())
                        return;
                    try {
                        Thread.sleep(arrayList.get(0).sleepTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        stopFlag = true;
                        startPathThread = null;
                        return;
                    }
                    while (!stopFlag) {
                        for (int i = 1; i < arrayList.size(); i++) {
                            if (Thread.interrupted())
                                return;
                            if (moveSyncPosition(arrayList.get(i).xAxis, arrayList.get(i).yAxis) >= 0){
                                LogTool.d(TAG,"starPath move to "+i+" position success, xAxis="+arrayList.get(i).xAxis+",yAxis="+arrayList.get(i).yAxis);
                            }else {
                                LogTool.d(TAG,"starPath error");
                                stopFlag = true;
                                startPathThread = null;
                                return;
                            }
                            if (Thread.interrupted())
                                return;
                            try {
                                Thread.sleep(arrayList.get(i).sleepTime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                stopFlag = true;
                                startPathThread = null;
                                LogTool.d(TAG, "startPath stop sleep");
                                return;
                            }
                        }
                        for (int i = arrayList.size() - 1; i >= 0; i--) {
                            if (Thread.interrupted())
                                return;
                            if (moveSyncPosition(arrayList.get(i).xAxis, arrayList.get(i).yAxis) >= 0){
                                LogTool.d(TAG,"starPath move to "+i+" position success");
                            }else {
                                LogTool.d(TAG,"starPath error");
                                stopFlag = true;
                                startPathThread = null;
                                return;
                            }
                            if (Thread.interrupted())
                                return;
                            try {
                                Thread.sleep(arrayList.get(i).sleepTime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                stopFlag = true;
                                startPathThread = null;
                                return;
                            }
                        }
                    }
                }
            });
            startPathThread.start();
        }
    }

    public void stopPath() {
        stopFlag = true;
        if (startHoriziontalThread != null) {
            startHoriziontalThread.interrupt();
            startHoriziontalThread = null;
        }
        if (startVericalThread != null) {
            startVericalThread.interrupt();
            startVericalThread = null;
        }
        if (startPathThread != null) {
            startPathThread.interrupt();
            startPathThread = null;
            pathPositionsArrayList = null;
        }
        if(sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_STOP, -1) >= 0){
            LogTool.d(TAG,"stopPath  HORIZONTAL_MOTOR success");
        }else {
            LogTool.e(TAG,"stopPath VERTICAL_MOTOR  failed");
        }
        if(sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_STOP, -1) >= 0){
            LogTool.d(TAG,"stopPath VERTICAL_MOTOR  success");
        }else {
            LogTool.e(TAG,"stopPath  VERTICAL_MOTOR failed");
        }

        if (sendCmd(MotorCmd.HORIZONTAL_MOTOR,MotorCmd.MOTOR_SET_INTERVAL,Normal_Speed) >= 0){
            LogTool.d(TAG,"set horizintal motor Frequency success");
        }else {
            LogTool.e(TAG,"set horizintal motor Frequency  failed");
        }
        if (sendCmd(MotorCmd.VERTICAL_MOTOR,MotorCmd.MOTOR_SET_INTERVAL,Normal_Speed) >= 0){
            LogTool.d(TAG,"set vertical motor Frequency success");
        }else {
            LogTool.e(TAG,"set vertical motor Frequency  failed");
        }
    }

    public static class PathPosition {
        private int sleepTime;
        private int xAxis;
        private int yAxis;

        public int getSleepTime() {
            return sleepTime;
        }

        public void setSleepTime(int sleepTime) {
            this.sleepTime = sleepTime;
        }

        public int getxAxis() {
            return xAxis;
        }

        public void setxAxis(int xAxis) {
            this.xAxis = xAxis;
        }

        public int getyAxis() {
            return yAxis;
        }

        public void setyAxis(int yAxis) {
            this.yAxis = yAxis;
        }
    }

    private class AppliancesBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            WorkThread workThread = new WorkThread(intent);
            workThread.start();
        }
    }

    private class WorkThread extends Thread {
        private Intent intent;

        private WorkThread(Intent intent) {
            this.intent = intent;
        }

        @Override
        public void run() {
            super.run();
            String type = intent.getStringExtra("type");
            if (TextUtils.isEmpty(type)) {
                LogTool.d(TAG, "BroadCast WorkThread type is null");
                return;
            }
            if ("get".equals(type)) {
                Message msg = Message.obtain();
                msg.what = 1;//GET Position
                handler.sendMessage(msg);
            } else if (type.equals("set")) {
                int xAxis = intent.getIntExtra("horizontal", -1);
                int yAxis = intent.getIntExtra("vertical", -1);
                if (xAxis < 0 || yAxis < 0) {
                    Message msg = Message.obtain();
                    msg.what = 0;//ERROR
                    handler.sendMessage(msg);
                } else {
                    if (stopFlag) {          //path not moving
                        int xMoveBefore = getHorizonCurrenStep();
                        int yMoveBefore = getVerticalCurrenStep();
                        Message msg = Message.obtain();

                        if (moveSyncPosition(xAxis, yAxis) >= 0)
                            LogTool.d(TAG, "WorkThread moveSyncPosition  destination  success ");
                        else {
                            //send error
                            msg.what = -1;
                            handler.sendMessage(msg);
                            return;
                        }
                        //1.send message turnto destination
                        msg.what = 2;//Turn to success
                        handler.sendMessage(msg);
                        //2.sleep 5s
                        try {
                            sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //3.turn to origin
                        if (moveSyncPosition(xMoveBefore, yMoveBefore) >= 0)
                            LogTool.d(TAG, "WorkThread moveSyncPosition  origin  success ");
                        else {
                            msg.what = -1;
                            handler.sendMessage(msg);
                        }
                    } else {
                        //path stop begin
                        if (startHoriziontalThread != null) {
                            startHoriziontalThread.interrupt();
                            startHoriziontalThread = null;
                            stopFlag = true;
                            pathFlag = 1;
                        }
                        if (startVericalThread != null) {
                            startVericalThread.interrupt();
                            startVericalThread = null;
                            stopFlag = true;
                            pathFlag = 2;
                        }
                        if (startPathThread != null) {
                            startPathThread.interrupt();
                            startPathThread = null;
                            stopFlag = true;
                            pathFlag = 3;
                        }
                        if (sendCmd(MotorCmd.HORIZONTAL_MOTOR, MotorCmd.MOTOR_STOP, -1) >= 0) {
                            LogTool.d(TAG, "stopPath success");
                        } else {
                            LogTool.e(TAG, "stopPath failed");
                        }
                        if(sendCmd(MotorCmd.VERTICAL_MOTOR, MotorCmd.MOTOR_STOP, -1) >= 0){
                            LogTool.d(TAG,"stopPath VERTICAL_MOTOR  success");
                        }else {
                            LogTool.e(TAG,"stopPath  VERTICAL_MOTOR failed");
                        }
                        if (sendCmd(MotorCmd.HORIZONTAL_MOTOR,MotorCmd.MOTOR_SET_INTERVAL,Normal_Speed) >= 0){
                            LogTool.d(TAG,"set horizintal motor Frequency success");
                        }else {
                            LogTool.e(TAG,"set horizintal motor Frequency  failed");
                        }
                        if (sendCmd(MotorCmd.VERTICAL_MOTOR,MotorCmd.MOTOR_SET_INTERVAL,Normal_Speed) >= 0){
                            LogTool.d(TAG,"set vertical motor Frequency success");
                        }else {
                            LogTool.e(TAG,"set vertical motor Frequency  failed");
                        }
                        //path stop end

                        Message msg = Message.obtain();
                        if (moveSyncPosition(xAxis, yAxis) >= 0)
                            LogTool.d(TAG, "WorkThread moveSyncPosition  destination  success ");
                        else {
                            msg.what = -1;
                            handler.sendMessage(msg);
                            return;
                        }
                        msg.what = 2;//Turn to success
                        handler.sendMessage(msg);
                        try {
                            sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        msg.what = 3;//startPath
                        handler.sendMessage(msg);
                    }
                }

            }


        }
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Intent intent = new Intent();
            intent.setAction(Send_BroadCast_Filter);
            switch (msg.what) {
                case -1:
                    intent.putExtra("type", "set");
                    intent.putExtra("Error", "sendParameterError");
                    SmartCameraApplication.getContext().sendBroadcast(intent);
                    break;
                case 0:
                    intent.putExtra("type", "set");
                    intent.putExtra("Error", "movePositionError");
                    SmartCameraApplication.getContext().sendBroadcast(intent);
                    break;
                case 1:
                    int x = getHorizonCurrenStep();
                    int y = getVerticalCurrenStep();
                    intent.putExtra("type", "get");
                    intent.putExtra("horizontal", x);
                    intent.putExtra("vertical", y);
                    SmartCameraApplication.getContext().sendBroadcast(intent);
                    break;
                case 2:
                    intent.putExtra("type", "set");
                    intent.putExtra("horizontal", getHorizonCurrenStep());
                    intent.putExtra("vertical", getVerticalCurrenStep());
                    SmartCameraApplication.getContext().sendBroadcast(intent);
                    break;
                case 3:
                    if (pathFlag == 1)
                        startHoriziontalThread.start();
                    else if (pathFlag == 2)
                        startVericalThread.start();
                    else if (pathFlag == 3)
                        starPath(pathPositionsArrayList);
                    break;
            }
        }
    };
}
