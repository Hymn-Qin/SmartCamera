package com.zzdc.abb.smartcamera.TutkBussiness;

import android.text.TextUtils;
import android.util.Log;

import com.ptz.PTZControl.PTZControlCallBackContainer;
import com.ptz.PTZControl.PTZControlManager;
import com.ptz.motorControl.MotorCmd;
import com.ptz.motorControl.MotorDevicePose;
import com.ptz.motorControl.MotorManager;
import com.tutk.IOTC.AVIOCTRLDEFs;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.controller.AACDecoder;
import com.zzdc.abb.smartcamera.controller.AlertVideoManager;
import com.zzdc.abb.smartcamera.controller.AudioEncoder;
import com.zzdc.abb.smartcamera.controller.AudioGather;
import com.zzdc.abb.smartcamera.controller.AvMediaRecorder;
import com.zzdc.abb.smartcamera.controller.AvMediaTransfer;
import com.zzdc.abb.smartcamera.controller.HistoryManager;
import com.zzdc.abb.smartcamera.controller.VideoEncoder;
import com.zzdc.abb.smartcamera.util.LogTool;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TutkCmdManager {
    private static final String TAG = TutkCmdManager.class.getSimpleName();
    private TutkSession mTutkSession;
    private AvMediaTransfer mMediaTransfer = new AvMediaTransfer();
    private MotorManager mMotorManager = MotorManager.getManger();
    private PTZControlManager mViewPathManger = PTZControlManager.getInstance();
    private AvMediaRecorder mAvMediaRecorder = null;
    private AACDecoder mAACDecoder;
    private ApplicationSetting mAplicationSetting = null;
    private HistoryManager mHistoryManager;

    private AlertVideoManager alertVideoManager;

    public TutkCmdManager(TutkSession tutkSession) {
        mTutkSession = tutkSession;
    }

    public void closeIOCTRL() {
        mMediaTransfer.unRegisterAvTransferListener(mTutkSession);
    }

    public void uninit() {
        Log.d("TutkCmdManager", "uninit");
        if (mTutkSession != null) {
            mTutkSession.releaseRemoteCallRes();
            mTutkSession.unregistRemoteAudioDataMonitor(mAACDecoder);
        }
        if (mAACDecoder != null) {
            mAACDecoder.SetAudioTrackTypeForMonitor();
            mAACDecoder.deinit();
            mAACDecoder = null;

        }
        AudioGather.getInstance().stopRecord();
        AudioGather.getInstance().SetAudioSourceTypeForMonitor();
        AudioGather.getInstance().prepareAudioRecord();
        AudioGather.getInstance().startRecord();


    }

    public void HandleIOCTRLCmd(int sid, int avIndex, byte[] buf, int type) {
        LogTool.d(TAG, "HandleIOCTRLCmd");
        ByteArrayInputStream in = null;
        switch (type) {
            case AVIOCTRLDEFs.IOTYPE_USER_IPCAM_START:
                LogTool.d(TAG, "  IOTYPE_USER_IPCAM_START");
                AudioEncoder.getInstance().registerEncoderListener(mMediaTransfer);
                VideoEncoder.getInstance().registerEncoderListener(mMediaTransfer);
                mMediaTransfer.startAvMediaTransfer();
                mMediaTransfer.registerAvTransferListener(mTutkSession);
                mTutkSession.writeMessge("ok");
                break;

            case AVIOCTRLDEFs.IOTYPE_USER_IPCAM_STOP:
                LogTool.d(TAG, " IOTYPE_USER_IPCAM_STOP start");
                mTutkSession.writeMessge("ok");
                AudioEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                VideoEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                if (mHistoryManager != null) {
                    mHistoryManager.mExtrator.unRegisterExtratorListener(mMediaTransfer);
                }
                mMediaTransfer.unRegisterAvTransferListener(mTutkSession);
                mMediaTransfer.stopAvMediaTransfer();
                LogTool.d(TAG, "read cmd:IOTYPE_USER_IPCAM_STOP  data end.");
                mTutkSession.setState(TutkSession.SESSION_STATE_IDLE);
                break;

        }

        if (type != -1) return;

        String receiveJsonStr = null;
        String mesgType = null;
        JSONObject ptzObj = null;
        try {
            receiveJsonStr = new String(buf, "UTF-8");
            LogTool.d(TAG, "receiveJsonStr: " + receiveJsonStr);
            ptzObj = new JSONObject(receiveJsonStr);
            mesgType = ptzObj.getString("type");

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (mesgType == null) {
            Log.e(TAG, "receiveJsonStr  parse JSON mesgTyep Error");
            return;
        }
        Log.d(TAG, "receiveJsonStr: mesgTyep " + mesgType);
        switch (mesgType) {
            case "PTZ_CTL":
                mViewPathManger.motorContorl(receiveJsonStr);
                break;
            case "PTZ_VIEW_GET":
                mViewPathManger.PTZViewGet(new PTZControlCallBackContainer.ViewPathCallBack() {
                    @Override
                    public void onCompleted(String jsonString) {
                        mTutkSession.writeMessge(jsonString);
                    }
                });
                break;
            case "PTZ_VIEW_TURNTO":
                mViewPathManger.PTZViewTurnTo(receiveJsonStr);
                break;
            case "PTZ_HORIZONTAL_PATH_START":
                mViewPathManger.PTZHorizontalPathStart();
                break;
            case "PTZ_VERTICAL_PATH_START":
                mViewPathManger.PTZVerticalPathStart();
                break;
            case "PTZ_CUSTOM_PATH_START":
                mViewPathManger.PTZPathStart(receiveJsonStr);
                break;
            case "PTZ_PATH_STOP":
                mMotorManager.stopPath();
                break;
            case "START_ONE_KEY_CALL":
                Log.d(TAG, "START_ONE_KEY_CALL");
                mAACDecoder = new AACDecoder();
                mAACDecoder.SetAudioTrackTypeForCall();
                mAACDecoder.init();
                mTutkSession.registRemoteAudioDataMonitor(mAACDecoder);
                mTutkSession.prepareForReceiveAudioData();
                AudioGather.getInstance().stopRecord();
                AudioGather.getInstance().SetAudioSourceTypeForCall();
                AudioGather.getInstance().prepareAudioRecord();
                AudioGather.getInstance().startRecord();
//                //创建Intent
//                Intent intent = new Intent();
//                intent.setAction("com.zzdc.abb.smartcamera.action.CallOn");
//                intent.putExtra("msg", "简单的消息");
//                //发送广播
//               // sendBroadcast(intent);

                break;
            case "STOP_ONE_KEY_CALL":
                Log.d(TAG, "STOP_ONE_KEY_CALL");
                mTutkSession.releaseRemoteCallRes();
                mTutkSession.unregistRemoteAudioDataMonitor(mAACDecoder);
                mAACDecoder.SetAudioTrackTypeForMonitor();
                mAACDecoder.deinit();
                mAACDecoder = null;
                AudioGather.getInstance().stopRecord();
                AudioGather.getInstance().SetAudioSourceTypeForMonitor();
                AudioGather.getInstance().prepareAudioRecord();
                AudioGather.getInstance().startRecord();
                break;
            case "START_MONITOR":
                Log.d(TAG, "START_MONITOR");
                if (mAvMediaRecorder == null) {
                    mAvMediaRecorder = AvMediaRecorder.getInstance();
                }
                mAplicationSetting = ApplicationSetting.getInstance();
                mAplicationSetting.setSystemMonitorSetting(true);
                mAvMediaRecorder.init();
                mAvMediaRecorder.avMediaRecorderStart();
                break;
            case "STOP_MONITOR":
                Log.d(TAG, "STOP_MONITOR");
                mAplicationSetting = ApplicationSetting.getInstance();
                mAplicationSetting.setSystemMonitorSetting(false);
                if (mAvMediaRecorder == null) {
                    mAvMediaRecorder = AvMediaRecorder.getInstance();
                }
                mAvMediaRecorder.avMediaRecorderStop();
                break;

            case "getHistoryInfoList":
                Log.d(TAG, " getHistoryInfoList ");
                mHistoryManager = HistoryManager.getInstance();
                ArrayList<String> tmpResponce = mHistoryManager.getHistoryVideoFileInfo();

                for (String tmpRes : tmpResponce) {
                    Log.d(TAG, "getHistoryInfoList " + tmpRes);
                    mTutkSession.writeMessge(tmpRes);
                }

                String tmpEnd = "{" +
                        "   \"type\" : \"getHistoryInfoList\"," +
                        "   \"ret\" :\" 1\" " +
                        "}";
                mTutkSession.writeMessge(tmpEnd);

                break;
            case "setHistoryVideo":
                LogTool.d(TAG, " setHistoryVideo start");
                String tmpTime = null;
                try {
                    tmpTime = ptzObj.getString("time");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "setHistoryVideo Exception " + e.toString());
                }
                if (isDoStopHistory(tmpTime)) {
                    Log.d(TAG, "DoStopHistory");
                    if (null == mHistoryManager)
                        return;
                    mHistoryManager.mExtrator.unRegisterExtratorListener(mMediaTransfer);
                    mHistoryManager.release();
                    break;
                } else {
//                    closeIOCTRL();
                    Log.d(TAG, "DoHistory");
                    mHistoryManager = HistoryManager.getInstance();

                    if (mHistoryManager.handleHistoryVideo(tmpTime) < 0) {
                        String tmpRet = "{" +
                                "   \"ret\" : \"-1\"," +
                                "   \"desc\" :\" 文件不存在\" " +
                                "}";
                        mTutkSession.writeMessge(tmpRet);
                    } else {
                        String tmpRet = "{" +
                                "   \"ret\" : \"0\"," +
                                "   \"desc\" :\" \" " +
                                "}";
                        mTutkSession.writeMessge(tmpRet);
                        //canel real time data
                        AudioEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                        VideoEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                        //register history
                        mHistoryManager.mExtrator.registerExtratorListener(mMediaTransfer);
                    }

                    break;
                }
            case "CHANGE_POSE":
                try {
                    JSONObject jsonObject = new JSONObject(receiveJsonStr);
                    boolean POSE = jsonObject.getBoolean("POSE");
                    MotorCmd.DEVICE_POSE = POSE;
                    MotorDevicePose motorDevicePose = MotorDevicePose.getInstance();
                    motorDevicePose.setDevicePose(POSE);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;
            case "getAlertInfoList":
                Log.d("qxj", " getAlertInfoList ");
                alertVideoManager = AlertVideoManager.getInstance();
                ArrayList<String> tmpAlertResponce = alertVideoManager.getHistoryVideoFileInfo();

                for (String tmpRes : tmpAlertResponce) {
                    Log.d("qxj", "getAlertInfoList " + tmpRes);
                    mTutkSession.writeMessge(tmpRes);
                }

                String tmpAlertEnd = "{" +
                        "   \"type\" : \"getAlertInfoList\"," +
                        "   \"ret\" :\" 1\" " +
                        "}";
                mTutkSession.writeMessge(tmpAlertEnd);

                break;
            case "setAlertVideo":
                LogTool.d("qxj", " setAlertVideo start");
                String tmpTime1 = null;
                try {
                    tmpTime1 = ptzObj.getString("time");
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d("qxj", "setAlertVideo Exception " + e.toString());
                }
                if (isDoStopHistory(tmpTime1)) {
                    Log.d("qxj", "DoStop setAlertVideo");
                    if (null == alertVideoManager)
                        return;
                    alertVideoManager.mExtrator.unRegisterExtratorListener(mMediaTransfer);
                    alertVideoManager.release();
                } else {
                    Log.d("qxj", "Do setAlertVideo");
                    alertVideoManager = AlertVideoManager.getInstance();
                    if (alertVideoManager.handleHistoryVideo(tmpTime1) < 0) {
                        String tmpRet = "{" +
                                "   \"ret\" : \"-1\"," +
                                "   \"desc\" :\" 文件不存在\" " +
                                "}";
                        mTutkSession.writeMessge(tmpRet);
                    } else {
                        String tmpRet = "{" +
                                "   \"ret\" : \"0\"," +
                                "   \"desc\" :\" \" " +
                                "}";
                        mTutkSession.writeMessge(tmpRet);
                        //canel real time data
                        AudioEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                        VideoEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                        //register history
                        alertVideoManager.mExtrator.registerExtratorListener(mMediaTransfer);
                    }


                }
                break;
        }

    }

    private boolean isDoStopHistory(String aDateString) {
        if (TextUtils.isEmpty(aDateString)) {
            return true;
        }

        Log.d(TAG, "aDateString = " + aDateString);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date tmpDate1 = sdf.parse(aDateString);
            Date tmpDate2 = new Date(0);
            Log.d(TAG, "tmpDate2 = " + tmpDate2 + " tmpDate1 = " + tmpDate1);
            if (tmpDate1.getTime() <= tmpDate2.getTime()) {
                Log.d(TAG, "无效时间参数，停止历史监控");
                return true;
            } else {
                Log.d(TAG, "时间参数为 " + tmpDate1);
                return false;
            }

        } catch (Exception e) {
            Log.d(TAG, "dateFormat.parse Exception " + e.toString());
            return true;
        }

    }

}
