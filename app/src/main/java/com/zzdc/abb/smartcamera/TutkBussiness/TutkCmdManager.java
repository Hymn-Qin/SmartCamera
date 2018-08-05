package com.zzdc.abb.smartcamera.TutkBussiness;

import android.util.Log;

import com.ptz.PTZControl.PTZControlCallBackContainer;
import com.ptz.PTZControl.PTZControlManager;
import com.ptz.motorControl.MotorManager;
import com.tutk.IOTC.AVIOCTRLDEFs;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.controller.AACDecoder;
import com.zzdc.abb.smartcamera.controller.AudioGather;
import com.zzdc.abb.smartcamera.controller.AvMediaRecorder;
import com.zzdc.abb.smartcamera.controller.AvMediaTransfer;
import com.zzdc.abb.smartcamera.util.LogTool;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

public class TutkCmdManager {
    private static final String TAG = TutkCmdManager.class.getSimpleName();
    private TutkSession mTutkSession;
    private AvMediaTransfer avMediaTransfer = AvMediaTransfer.getInstance();
    private MotorManager mMotorManager = MotorManager.getManger();
    private PTZControlManager mViewPathManger = PTZControlManager.getInstance();
    private AvMediaRecorder mAvMediaRecorder = null;
    private AACDecoder mAACDecoder;
    private ApplicationSetting mAplicationSetting = null;

    public TutkCmdManager(TutkSession tutkSession) {
        mTutkSession = tutkSession;
    }

    public void closeIOCTRL() {
        avMediaTransfer.unRegisterAvTransferListener(mTutkSession);
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
                LogTool.d(TAG, "  IOTYPE_USER_IPCAM_START start");
                try {
                    in = new ByteArrayInputStream(buf, 0, 1);
                    int data = in.read();
                    while (data != -1) {
                        data = in.read();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                LogTool.d(TAG, "read cmd:IOTYPE_USER_IPCAM_START  data end.");
                avMediaTransfer.registerAvTransferListener(mTutkSession);
                mTutkSession.writeMessge("ok");
                break;

            case AVIOCTRLDEFs.IOTYPE_USER_IPCAM_STOP:
                LogTool.d(TAG, " IOTYPE_USER_IPCAM_STOP start");
                try {
                    in = new ByteArrayInputStream(buf, 0, 1);
                    int data = in.read();
                    while (data != -1) {
                        data = in.read();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                avMediaTransfer.unRegisterAvTransferListener(mTutkSession);
                LogTool.d(TAG, "read cmd:IOTYPE_USER_IPCAM_STOP  data end.");
//                mTutkSession.destorySession();
                mTutkSession.setState(TutkSession.SESSION_STATE_IDLE);
//                closeIOCTRL();
                break;

            case AVIOCTRLDEFs.IOTYPE_USER_IPCAM_AUDIOSTART:
                //               mAACDecoder = new AACDecoder();
//                mTutkSession.registRemoteAudioDataMonitor(mAACDecoder);
                break;
            case AVIOCTRLDEFs.IOTYPE_USER_IPCAM_AUDIOSTOP:
//                mTutkSession.unregistRemoteAudioDataMonitor(mAACDecoder);
                //               mAACDecoder = null;
                break;
        }

        if (type != -1) return;

        String receiveJsonStr = null;
        String mesgType = null;
        try {
            receiveJsonStr = new String(buf, "UTF-8");
            LogTool.d(TAG, "receiveJsonStr: " + receiveJsonStr);
            JSONObject ptzObj = new JSONObject(receiveJsonStr);
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


        }

    }

}
