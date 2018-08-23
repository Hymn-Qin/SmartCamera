package com.zzdc.abb.smartcamera.TutkBussiness;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.AudioManager;
import android.util.Log;
import com.google.gson.Gson;
import com.ptz.PTZControl.PTZControlCallBackContainer;
import com.ptz.PTZControl.PTZControlManager;
import com.ptz.motorControl.MotorCmd;
import com.ptz.motorControl.MotorManager;
import com.tutk.IOTC.AVIOCTRLDEFs;
import com.zzdc.abb.smartcamera.FaceFeature.FaceFromClient;
import com.zzdc.abb.smartcamera.FaceFeature.FacePictures;
import com.zzdc.abb.smartcamera.FaceFeature.Utils;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.controller.AACDecoder;
import com.zzdc.abb.smartcamera.controller.AudioEncoder;
import com.zzdc.abb.smartcamera.controller.AudioGather;
import com.zzdc.abb.smartcamera.controller.AvMediaRecorder;
import com.zzdc.abb.smartcamera.controller.AvMediaTransfer;
import com.zzdc.abb.smartcamera.controller.MP4Extrator;
import com.zzdc.abb.smartcamera.controller.MainActivity;
import com.zzdc.abb.smartcamera.controller.MediaStorageManager;
import com.zzdc.abb.smartcamera.controller.PCMAudioDataTransfer;
import com.zzdc.abb.smartcamera.controller.VideoEncoder;
import com.zzdc.abb.smartcamera.info.MediaDurationInfo;
import com.zzdc.abb.smartcamera.util.LogTool;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TutkCmdManager {
    private static final String TAG = TutkCmdManager.class.getSimpleName();
    private TutkSession mTutkSession;
    private AvMediaTransfer mMediaTransfer = new AvMediaTransfer();
    private MotorManager mMotorManager = MotorManager.getManger();
    private PTZControlManager mViewPathManger = PTZControlManager.getInstance();
    private AvMediaRecorder mAvMediaRecorder = null;
    private AACDecoder mAACDecoder;
    private ApplicationSetting mAplicationSetting = null;
    private MP4Extrator mExtractor = null;

    private static final int STATUS_IDEL = 0;
    private static final int STATUS_RUNTIME_TRANSFER = 1;
    private static final int STATUS_HISTORY_TRANSFER = 2;
    private static final int STATUS_WARNING_TRANSFER = 3;
    private int mTransmissionStatus = STATUS_IDEL;

    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }
    };

    private static final String PTZ_CONTRAL = "PTZ_CTL";
    private static final String PTZ_VIEW_GET = "PTZ_VIEW_GET";
    private static final String PTZ_VIEW_TURNTO = "PTZ_VIEW_TURNTO";
    private static final String PTZ_HORIZONTAL_PATH_START = "PTZ_HORIZONTAL_PATH_START";
    private static final String PTZ_VERTICAL_PATH_START = "PTZ_VERTICAL_PATH_START";
    private static final String PTZ_CUSTOM_PATH_START = "PTZ_CUSTOM_PATH_START";
    private static final String PTZ_PATH_STOP = "PTZ_PATH_STOP";
    private static final String START_ONE_KEY_CALL = "START_ONE_KEY_CALL";
    private static final String STOP_ONE_KEY_CALL = "STOP_ONE_KEY_CALL";
    private static final String START_MONITOR = "START_MONITOR";
    private static final String STOP_MONITOR = "STOP_MONITOR";
    private static final String GET_HISTORY_INFORMATION = "getHistoryInfoList";
    private static final String SET_HISTORY_FILE = "setHistoryVideo";
    private static final String CHANGE_POSE = "CHANGE_POSE";
    private static final String SET_FACES = "setFacePictures";

    private static final String GET_ALERT_INFORMATION = "getAlertInfoList";
    private static final String SET_ALERT_FILE = "setAlertVideo";

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
                handleIpCameraStart();
                break;
            case AVIOCTRLDEFs.IOTYPE_USER_IPCAM_STOP:
                handleIpCameraStop();
                break;
        }

        if (type != -1) return;

        String receiveJsonStr = null;
        JSONObject ptzObj = null;
        String mesgType = null;
        try {
            receiveJsonStr = new String(buf, "UTF-8");
            ptzObj = new JSONObject(receiveJsonStr);
            mesgType = ptzObj.getString("type");
            LogTool.d(TAG, "Receive JSON: " + receiveJsonStr + ", type: " + mesgType);
        } catch (JSONException | UnsupportedEncodingException e) {
            LogTool.w(TAG, "Parse receive the message type with exception, ", e);
            return;
        }

        switch (mesgType) {
            case PTZ_CONTRAL:
                handlePtzControl(receiveJsonStr);
                break;
            case PTZ_VIEW_GET:
                handlePtzViewGet();
                break;
            case PTZ_VIEW_TURNTO:
                handlePtzViewTurnTo(receiveJsonStr);
                break;
            case PTZ_HORIZONTAL_PATH_START:
                mViewPathManger.PTZHorizontalPathStart();
                break;
            case PTZ_VERTICAL_PATH_START:
                mViewPathManger.PTZVerticalPathStart();
                break;
            case PTZ_CUSTOM_PATH_START:
                mViewPathManger.PTZPathStart(receiveJsonStr);
                break;
            case PTZ_PATH_STOP:
                mMotorManager.stopPath();
                break;
            case START_ONE_KEY_CALL:
                handleStartOneKeyCall();
                break;
            case STOP_ONE_KEY_CALL:
                handleStopOneKeyCall();
                break;
            case GET_HISTORY_INFORMATION:
                handleGetHistoryInfo();
                break;
            case SET_HISTORY_FILE:
                handleSetHistoryFile(ptzObj);
                break;
            case CHANGE_POSE:
                handleChangePose(ptzObj);
                break;
            case SET_FACES:
                handleSetFacePictures(receiveJsonStr);
                break;
            case SET_ALERT_FILE:
                handleSetAlertFile(ptzObj);
                break;
            case GET_ALERT_INFORMATION:
                handleGetAlertInfo();
                break;
            default:
                break;
        }
    }

    private void handleIpCameraStart() {
        LogTool.d(TAG, "IOTYPE_USER_IPCAM_START");

        if (mTransmissionStatus == STATUS_HISTORY_TRANSFER || mTransmissionStatus == STATUS_WARNING_TRANSFER) {
            if (mExtractor != null) {
                mExtractor.start();
                mExtractor.unRegisterExtratorListener(mMediaTransfer);
                mExtractor = null;
            }
        }
        mTransmissionStatus = STATUS_RUNTIME_TRANSFER;
        AudioEncoder.getInstance().registerEncoderListener(mMediaTransfer);
        VideoEncoder.getInstance().registerEncoderListener(mMediaTransfer);
        mMediaTransfer.startAvMediaTransfer();
        mMediaTransfer.registerAvTransferListener(mTutkSession);
        mTutkSession.writeMessge("ok");
    }

    private void handleIpCameraStop() {
        LogTool.d(TAG, " LEON-IOTYPE_USER_IPCAM_STOP");
        mTutkSession.writeMessge("ok");

        if (mTransmissionStatus == STATUS_RUNTIME_TRANSFER) {
            AudioEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
            VideoEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
            mMediaTransfer.unRegisterAvTransferListener(mTutkSession);
            mMediaTransfer.stopAvMediaTransfer();
        } else if (mTransmissionStatus == STATUS_HISTORY_TRANSFER || mTransmissionStatus == STATUS_WARNING_TRANSFER) {
            if (mExtractor != null) {
                mExtractor.stop();
                mExtractor = null;
            }
        }
        mTransmissionStatus = STATUS_IDEL;
        mTutkSession.setState(TutkSession.SESSION_STATE_IDLE);
    }

    private void handlePtzControl(String receiveJsonStr) {
        LogTool.d(TAG, "Handle " + PTZ_CONTRAL);
        mViewPathManger.motorContorl(receiveJsonStr);
    }

    private void handlePtzViewGet() {
        LogTool.d(TAG, "Handle " + PTZ_VIEW_GET);
        mViewPathManger.PTZViewGet(new PTZControlCallBackContainer.ViewPathCallBack() {
            @Override
            public void onCompleted(String jsonString) {
                mTutkSession.writeMessge(jsonString);
            }
        });
    }

    private void handlePtzViewTurnTo(String receiveJsonStr) {
        LogTool.d(TAG, "Handle " + PTZ_VIEW_TURNTO);
        mViewPathManger.PTZViewTurnTo(receiveJsonStr);
    }

    private void handleStartOneKeyCall() {
        Log.d(TAG, "Handle " + START_ONE_KEY_CALL + ", CallState=" + MainActivity.CallState);
        PCMAudioDataTransfer.getInstance().changeVoiceMode(PCMAudioDataTransfer.TELEPHONY_ON_MODE);
        AudioManager audioManager = (AudioManager) MainActivity.getContext().getSystemService(Context.AUDIO_SERVICE);
        int mode = audioManager.getMode();
        Log.d(TAG, "mode=" + mode);
        if (MainActivity.CallState == MainActivity.CallStateOff) {
            MainActivity.CallState = MainActivity.CallStateOn;
        } else if (MainActivity.CallState == MainActivity.CallStateOn) {
            if (mTutkSession != null && mAACDecoder != null) {
                mTutkSession.unregistRemoteAudioDataMonitor(mAACDecoder);
                mAACDecoder.deinit();
                mAACDecoder = null;
            }
        }
        mAACDecoder = new AACDecoder();
        mAACDecoder.SetAudioTrackTypeForCall();
        mAACDecoder.init();
        mTutkSession.registRemoteAudioDataMonitor(mAACDecoder);
        mTutkSession.prepareForReceiveAudioData();
        AudioGather.getInstance().stopRecord();
        AudioGather.getInstance().SetAudioSourceTypeForCall();
        AudioGather.getInstance().prepareAudioRecord();
        AudioGather.getInstance().startRecord();
    }

    private void handleStopOneKeyCall() {
        LogTool.d(TAG, "Handle " + STOP_ONE_KEY_CALL + ", CallState=" + MainActivity.CallState);
        MainActivity.CallState = MainActivity.CallStateOff;
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
        PCMAudioDataTransfer.getInstance().changeVoiceMode(PCMAudioDataTransfer.TELEPHONY_OFF_MODE);
    }

    private void handleGetHistoryInfo() {
        try {
            LogTool.d(TAG, "Handle " + GET_HISTORY_INFORMATION);
            List<MediaDurationInfo> infos = MediaStorageManager.getInstance().getHistoryDurationInfo();
            JSONObject jObj = new JSONObject();
            if (infos == null || infos.size() == 0) {
                LogTool.w(TAG, "No history duration info");
                jObj.put("type", GET_HISTORY_INFORMATION);
                jObj.put("ret", "-1");
                mTutkSession.writeMessge(jObj.toString());
            } else {
                JSONArray blkQuantum = new JSONArray();
                for (int i = 0; i < infos.size(); i++) {
                    MediaDurationInfo info = infos.get(i);
                    String start = TIME_FORMAT.get().format(new Date(info.getStart()));
                    String end = TIME_FORMAT.get().format(new Date(info.getEnd()));
                    JSONObject quantum = new JSONObject();
                    quantum.put("mStart", start);
                    quantum.put("mEnd", end);
                    blkQuantum.put(quantum);
                }
                jObj.put("type", GET_HISTORY_INFORMATION);
                jObj.put("ret", "0");
                jObj.put("DateArray", blkQuantum);
                mTutkSession.writeMessge(jObj.toString());

            }
            LogTool.d(TAG,"Return cliend history info = "+jObj.toString());
        } catch (JSONException e) {
            LogTool.w(TAG, "Handle " + GET_HISTORY_INFORMATION + " with exception, ", e);
        }
    }

    private void handleSetHistoryFile(JSONObject jObj) {
        try {
            String stringTime = jObj.getString("time");
            LogTool.w(TAG, "Handle " + SET_HISTORY_FILE + " time: " + stringTime);
            long longTime = TIME_FORMAT.get().parse(stringTime).getTime();
            String file = MediaStorageManager.getInstance().getHistoryFile(longTime);
            JSONObject j = new JSONObject();
            if ( file == null) {
                j.put("type","setHistoryVideo");
                j.put("ret", "-1");
                j.put("desc", "File not exist");
                mTutkSession.writeMessge(j.toString());
            } else {
                j.put("type","setHistoryVideo");
                j.put("ret", "0");
                j.put("desc", " ");
                mTutkSession.writeMessge(j.toString());

                if (mTransmissionStatus == STATUS_RUNTIME_TRANSFER) {
                    AudioEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                    VideoEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                } else if (mTransmissionStatus == STATUS_HISTORY_TRANSFER || mTransmissionStatus == STATUS_WARNING_TRANSFER){
                    if (mExtractor != null) {
                        mExtractor.unRegisterExtratorListener(mMediaTransfer);
                        mExtractor.stop();
                    }
                }
                long videoStartTime = MediaStorageManager.getHistoryMediaStartTime(file);
                mExtractor = new MP4Extrator(file, longTime, videoStartTime);
                mExtractor.registerExtratorListener(mMediaTransfer);
                mExtractor.init();
                mExtractor.start();
                mTransmissionStatus = STATUS_HISTORY_TRANSFER;
            }
        } catch (JSONException | ParseException e) {
            LogTool.w(TAG, "Handle " + SET_HISTORY_FILE + " with exception, ", e);
        }
    }

    private void handleChangePose(JSONObject jObj) {
        try {
            boolean POSE = jObj.getBoolean("POSE");
            LogTool.d(TAG, "Handle " + CHANGE_POSE + ", pose = " + POSE);

            MotorCmd.DEVICE_POSE = POSE;

            mAplicationSetting = ApplicationSetting.getInstance();
            mAplicationSetting.setDevicePose(POSE);
        } catch (JSONException e) {
            LogTool.w(TAG, "Handle " + CHANGE_POSE + " with exception, ", e);
        }
    }

    private void handleSetFacePictures(String jObj) {

            Gson gson = new Gson();
            FaceFromClient fromClient = gson.fromJson(jObj, FaceFromClient.class);
            if (fromClient == null) {
                return;
            }
            String name = fromClient.getName();
            ArrayList<FaceFromClient.Data> dataArrayList = fromClient.getDataArrayList();
            if (dataArrayList == null || dataArrayList.size() == 0) {
                return;
            }
            List<FacePictures> facePictures = new ArrayList<>();
            for (int i = 0; i < dataArrayList.size(); i++) {
                FaceFromClient.Data data = dataArrayList.get(i);
                byte[] faceData = data.getFaceData();
                String direction = data.getDirection();
                FacePictures facePicture = Utils.getFaceImage(faceData, name, direction);
                facePictures.add(facePicture);
            }
            //提取人脸数据
            Utils.startGetFeature(facePictures);
    }

    private void handleGetAlertInfo() {

        Log.d(TAG, "Handle " + GET_ALERT_INFORMATION);
        List<MediaDurationInfo> alertVideoDataList = MediaStorageManager.getInstance().getWarningDurationInfo();
        Map<String, Object> map = new HashMap<>();
        map.put("type", GET_ALERT_INFORMATION);
        if (alertVideoDataList.size() == 0) {
            map.put("ret", "-1");
        } else {
            map.put("ret", "0");
            map.put("DateArray", alertVideoDataList);
        }
        mTutkSession.writeMessge(new Gson().toJson(map));
    }

    private void handleSetAlertFile(JSONObject jObj) {
        try {
            String stringTime = jObj.getString("time");
            LogTool.w(TAG, "Handle " + SET_ALERT_FILE + " time: " + stringTime);
            long longTime = TIME_FORMAT.get().parse(stringTime).getTime();
            String file = MediaStorageManager.getInstance().getWarningFile(longTime);
            if (file != null) {
                JSONObject j = new JSONObject();
                j.put("ret", "0");
                j.put("desc", " ");
                mTutkSession.writeMessge(j.toString());

                if (mTransmissionStatus == STATUS_RUNTIME_TRANSFER) {
                    AudioEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                    VideoEncoder.getInstance().unRegisterEncoderListener(mMediaTransfer);
                } else if (mTransmissionStatus == STATUS_HISTORY_TRANSFER || mTransmissionStatus == STATUS_WARNING_TRANSFER) {
                    if (mExtractor != null) {
                        mExtractor.unRegisterExtratorListener(mMediaTransfer);
                        mExtractor.stop();
                    }
                }
                long videoStartTime = MediaStorageManager.getWarningMediaStartTime(file);
                mExtractor = new MP4Extrator(file, longTime, videoStartTime);
                mExtractor.registerExtratorListener(mMediaTransfer);
                mExtractor.init();
                mExtractor.start();
                mTransmissionStatus = STATUS_HISTORY_TRANSFER;
            } else  {
                JSONObject j = new JSONObject();
                j.put("ret", "-1");
                j.put("desc", "File not exist");
                mTutkSession.writeMessge(j.toString());
            }
        } catch (JSONException | ParseException e) {
            LogTool.w(TAG, "Handle " + SET_HISTORY_FILE + " with exception, ", e);
        }
    }

    private void handleGetAlertPictures() {}

}
