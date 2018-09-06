package com.zzdc.abb.smartcamera.TutkBussiness;

import android.util.Log;
import com.tutk.IOTC.AVAPIs;
import com.tutk.IOTC.IOTCAPIs;
import com.tutk.IOTC.St_SInfo;
import com.zzdc.abb.smartcamera.controller.AvMediaTransfer;
import com.zzdc.abb.smartcamera.controller.MainActivity;
import com.zzdc.abb.smartcamera.info.FrameInfo;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.util.LogTool;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class TutkSession implements AvMediaTransfer.AvTransferLister {
    private static final String TAG = TutkSession.class.getSimpleName();
    private static final boolean DEBUG = true;

    private int mSessionID;
    private int mChannelID;
    private int mAudioRecChannelID;
    private static final int MEDIA_IOTC_CHANNEL_ID = 0;
    private static final int VOICE_IOTC_CHANNEL_ID = 1;

    private static final String gAcc = "admin";
    private static final String gPwd = "888888";

    private static final int MAX_BUF_SIZE = 1024;
    private static final int SERVTYPE_STREAM_SERVER = 16;
    private static final int AV_SERVER_RESEND_SIZE = 1024 * 4;

    private TutkCmdManager tutkCmdManger;
    private WriteThread mWriteThread;
    private Thread mReceiveAudioDataThread;
    private boolean ReceiveAudioRuning = false;

    private CopyOnWriteArrayList<RemoteAudioDataMonitor> mRemoteAudioDataMonitors = new CopyOnWriteArrayList<>();
    private int mState;

    private static final int SESSION_STATE_PREPARE = 1;
    private static final int SESSION_STATE_EXECUTING = 2;
    public static final int SESSION_STATE_IDLE = 3; //TODO should be private

    public int getState() {
        return mState;
    }

    public void setState(int mState) {
        this.mState = mState;
    }

    private TutkSessionThread mTutkSessionThread;

    public TutkSession(int sessionId) {
        super();
        mSessionID = sessionId;
        setState(SESSION_STATE_PREPARE);
        mTutkSessionThread = new TutkSessionThread("TUTK session thread sid = " + mSessionID);
        mTutkSessionThread.start();
        tutkCmdManger = new TutkCmdManager(this);
    }

    private class TutkSessionThread extends Thread {
        private TutkSessionThread(String name) {
            super(name);
        }
        public void run() {
            super.run();
            try {
                LogTool.d(TAG, this.getName() + ", start");

                int[] bResend = new int[1];

                mChannelID = AVAPIs.avServStart3(mSessionID, gAcc, gPwd, 2, SERVTYPE_STREAM_SERVER, MEDIA_IOTC_CHANNEL_ID, bResend);
                if (mChannelID >= 0) {
                    LogTool.d(TAG, "sid(" + mSessionID + ") Start avServer succeed, channel id = " + mChannelID + " resend = " + bResend[0]);
                } else {
                    IOTCAPIs.IOTC_Session_Close(mSessionID);
                    LogTool.w(TAG, "sid(" + mSessionID + ") Start avServer failed., Error code = " + mChannelID);
                }

                int[] srvType = new int[1];
                mAudioRecChannelID = AVAPIs.avClientStart(mSessionID, gAcc, gPwd, 2, srvType, VOICE_IOTC_CHANNEL_ID);
                if (mAudioRecChannelID >= 0) {
                    LogTool.d(TAG, "sid(" + mSessionID + ") Start avClientStart succeed, channel id = " + mAudioRecChannelID + ", sver type = " + srvType[0]);
                } else {
                    LogTool.w(TAG, "sid(" + mSessionID + ") Start avClientStart failed., Error code = " + mAudioRecChannelID);
                }

                AVAPIs.avServSetResendSize(mChannelID, AV_SERVER_RESEND_SIZE);
                St_SInfo info = new St_SInfo();
                String[] mode = {"P2P mode", "Relay mode", "Lan mode"};

                int tmpResult = IOTCAPIs.IOTC_Session_Check(mSessionID, info);
                if (tmpResult == IOTCAPIs.IOTC_ER_NoERROR) {
                    LogTool.d(TAG, "IP:Port= " + CommApis.ByteToString(info.RemoteIP) + ":" + info.RemotePort);
                    if (info.Mode >= 0 && info.Mode <= 2) {
                        LogTool.d(TAG, "    -> Mode=[" + mode[info.Mode] + "]");
                    }
                    LogTool.d(TAG, "    -> NatType=[" + info.NatType + "], Version=[" + info.IOTCVersion + "]");
                } else {
                    LogTool.e(TAG, "IOTC check session info failed. result = " + tmpResult);
                }

                mWriteThread = new WriteThread("TUTK session write thread sid = " + mSessionID);
                mWriteThread.start();

                byte[] ioCtrlBuf = new byte[MAX_BUF_SIZE];
                int[] ioType = new int[1];
                while (mState != SESSION_STATE_IDLE) {
                    tmpResult = AVAPIs.avRecvIOCtrl(mChannelID, ioType, ioCtrlBuf, MAX_BUF_SIZE, 1000);
                    if (tmpResult >= 0) {
                        debug("avRecvIOCtrl(), length = " + tmpResult);
                        byte[] jsonCmdBuf = new byte[tmpResult];
                        System.arraycopy(ioCtrlBuf, 0, jsonCmdBuf, 0, tmpResult);
                        setState(SESSION_STATE_EXECUTING);
                        try{
                            tutkCmdManger.HandleIOCTRLCmd(mSessionID, mChannelID, jsonCmdBuf, ioType[0]);
                        }catch (Exception e){
                            e.printStackTrace();
                            Log.e(TAG,"HandleIOCTRLCmd e="+e.getMessage());
                        }
                    } else if (tmpResult != AVAPIs.AV_ER_TIMEOUT) {
                        LogTool.w(TAG, "avRecvIOCtrl(), Erro code = " + tmpResult);
                        tutkCmdManger.closeIOCTRL();
                        break;
                    }
                }
                AVAPIs.avServStop(mChannelID);
                AVAPIs.avClientStop(mAudioRecChannelID);
                setState(SESSION_STATE_IDLE);
                LogTool.d(TAG, getName() + "stop");
            } catch (Exception e) {
                e.printStackTrace();
                LogTool.w(TAG, getName(), e);
            }
        }
    }

    public void registRemoteAudioDataMonitor(RemoteAudioDataMonitor aMonitor) {
        if (aMonitor != null && !mRemoteAudioDataMonitors.contains(aMonitor)) {
            mRemoteAudioDataMonitors.add(aMonitor);
        }
    }

    public void unregistRemoteAudioDataMonitor(RemoteAudioDataMonitor aMonitor) {
        mRemoteAudioDataMonitors.remove(aMonitor);
    }

    public void prepareForReceiveAudioData() {
        mReceiveAudioDataThread = new RemoteAudioReceiveThread("TUTK session receive voice thread");
        ReceiveAudioRuning = true;
        mReceiveAudioDataThread.start();
    }

    public void releaseRemoteCallRes() {
        ReceiveAudioRuning = false;
        if (mReceiveAudioDataThread != null) {
            mReceiveAudioDataThread.interrupt();
            mReceiveAudioDataThread = null;
        }
    }

    public void writeMessge(String res) {
        if (mWriteThread != null) {
            mWriteThread.write(res);
        }
    }

    private class WriteThread extends Thread {
        private LinkedBlockingQueue<String> mWriteList = new LinkedBlockingQueue<>();

        private WriteThread(String name) {
            super(name);
        }
        @Override
        public void run() {
            LogTool.d(TAG, getName() + ", start");
            while (mState != SESSION_STATE_IDLE && !interrupted()) {
                try {
                    String responseStr = mWriteList.take();
                    byte[] message = responseStr.getBytes();
                    int result = AVAPIs.avSendIOCtrl(mChannelID, -1, message, message.length);
                    LogTool.d(TAG, "Send IOCtrl, sid=" + mSessionID + ", result: " + result + ", message: " + responseStr);
                } catch (InterruptedException e) {
                    LogTool.w(TAG, getName() + ", exception ", e);
                }
            }
        }

        private void write(String res) {
            if (res == null) {
                return;
            }
            mWriteList.offer(res);
        }
    }

    @Override
    public void sendVideoTutkFrame(TutkFrame tutkFrame) {
        if (mState == SESSION_STATE_IDLE) {
            LogTool.w(TAG, "Send video frame failed, session closed");
            return;
        }
        if (tutkFrame == null || tutkFrame.getData() == null || tutkFrame.getFrameInfo() == null) {
            LogTool.w(TAG, "Send video frame failed, data not ready");
            return;
        }
        FrameInfo frameInfo = tutkFrame.getFrameInfo();
        byte[] buf_info = frameInfo.parseContent();
        int rst = AVAPIs.avSendFrameData(mChannelID, tutkFrame.getData(), tutkFrame.getDataLen(), buf_info, buf_info.length);
        if (rst == AVAPIs.AV_ER_NoERROR) {
            debug("Send video data succeed, sid=" + mSessionID + ", result=" + rst + ", channel=" + mChannelID + ", size=" + tutkFrame.getDataLen());
        } else {
            LogTool.w(TAG, "Send video data failed, sid=" + mSessionID + ", result=" + rst + ", channel=" + mChannelID + ", size=" + tutkFrame.getDataLen());
            switch (rst) {
                case AVAPIs.AV_ER_INVALID_ARG:
                case AVAPIs.AV_ER_CLIENT_NOT_SUPPORT:
                    break;
                case AVAPIs.AV_ER_SESSION_CLOSE_BY_REMOTE:
                case AVAPIs.AV_ER_REMOTE_TIMEOUT_DISCONNECT:
                case AVAPIs.AV_ER_INVALID_SID:
                    mState = SESSION_STATE_IDLE;
                    break;
                case AVAPIs.AV_ER_CLIENT_NO_AVLOGIN:
                case AVAPIs.AV_ER_EXCEED_MAX_SIZE:
                case AVAPIs.AV_ER_MEM_INSUFF:
                case AVAPIs.AV_ER_NO_PERMISSION:
                    break;
            }
        }
    }

    @Override
    public void sendAudioTutkFrame(TutkFrame tutkFrame) {
        if (mState == SESSION_STATE_IDLE) {
            LogTool.w(TAG, "Send audio frame failed, session closed");
            return;
        }
        if (tutkFrame == null || tutkFrame.getData() == null || tutkFrame.getFrameInfo() == null) {
            LogTool.w(TAG, "Send audio frame failed, data not ready");
            return;
        }
        FrameInfo frameInfo = tutkFrame.getFrameInfo();
        byte[] buf_info = frameInfo.parseContent();
        int rst = AVAPIs.avSendAudioData(mChannelID, tutkFrame.getData(), tutkFrame.getDataLen(), buf_info, buf_info.length);
        if (rst == AVAPIs.AV_ER_NoERROR) {
            debug("Send audio data succeed, sid=" + mSessionID + ", result=" + rst + ", channel=" + mChannelID + ", size=" + tutkFrame.getDataLen());
        } else {
            LogTool.w(TAG, "Send audio data failed, sid=" + mSessionID + ", result=" + rst + ", channel=" + mChannelID + ", size=" + tutkFrame.getDataLen());
            switch (rst) {
                case AVAPIs.AV_ER_INVALID_ARG:
                case AVAPIs.AV_ER_CLIENT_NOT_SUPPORT:
                    break;
                case AVAPIs.AV_ER_SESSION_CLOSE_BY_REMOTE:
                case AVAPIs.AV_ER_REMOTE_TIMEOUT_DISCONNECT:
                case AVAPIs.AV_ER_INVALID_SID:
                    mState = SESSION_STATE_IDLE;
                    break;
                case AVAPIs.AV_ER_CLIENT_NO_AVLOGIN:
                case AVAPIs.AV_ER_MEM_INSUFF:
                case AVAPIs.AV_ER_EXCEED_MAX_SIZE:
                case AVAPIs.AV_ER_NO_PERMISSION:
                    break;
            }
        }
    }

    public class RemoteAudioReceiveThread extends Thread {
        private static final int AUDIO_BUF_SIZE = 1024;

        private RemoteAudioReceiveThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            Log.d(TAG, getName() + " start");
            byte[] frameInfo = new byte[FrameInfo.dataLength];
            byte[] audioBuffer = new byte[AUDIO_BUF_SIZE];
            while (ReceiveAudioRuning) {
                int tmpRet = AVAPIs.avCheckAudioBuf(mAudioRecChannelID);
                LogTool.d(TAG, "Receive voice fram count = " + tmpRet);
                System.out.println("tmpRet = " + tmpRet);

                if (tmpRet < 2) {
                    try {
                        sleep(50);
                        continue;
                    } catch (InterruptedException e) {
                        LogTool.w(TAG, "", e);
                        break;
                    }
                }

                int[] frameNumber = new int[1];
                int ret = AVAPIs.avRecvAudioData(mAudioRecChannelID, audioBuffer, AUDIO_BUF_SIZE, frameInfo, FrameInfo.dataLength, frameNumber);
                if (ret > 0) {
                    debug("Receive remote voice, ret = " + ret + ", frameNumber = " + frameNumber[0]);
                    for (RemoteAudioDataMonitor aMonitor : mRemoteAudioDataMonitors) {
                        aMonitor.onAudioDataReceive(audioBuffer, ret);
                    }
                } else if (ret == AVAPIs.AV_ER_LOSED_THIS_FRAME) {
                    LogTool.w(TAG, getName() + "Receive remote voice losed frame");
                    continue;
                } else if(ret == AVAPIs.AV_ER_INCOMPLETE_FRAME){
                    LogTool.w(TAG, "Incomplete video frame number threadName=" + Thread.currentThread().getName() + ",frameNumber[0]=" + frameNumber[0]);
                    continue;
                } else if (ret == AVAPIs.AV_ER_SESSION_CLOSE_BY_REMOTE) {
                    LogTool.w(TAG, "AV_ER_SESSION_CLOSE_BY_REMOTE threadName=" + Thread.currentThread().getName());
                    break;
                } else if (ret == AVAPIs.AV_ER_REMOTE_TIMEOUT_DISCONNECT) {
                    LogTool.w(TAG, "AV_ER_REMOTE_TIMEOUT_DISCONNECT threadName=" + Thread.currentThread().getName());
                    break;
                } else if (ret == AVAPIs.AV_ER_INVALID_SID) {
                    LogTool.w(TAG, "Session cant be used anymore threadName=" + Thread.currentThread().getName());
                    break;
                } else if(ret == AVAPIs.AV_ER_DATA_NOREADY){
                    LogTool.w(TAG, "The data is not ready for receiving threadName=" + Thread.currentThread().getName());
                    continue;
                } else {
                    LogTool.w(TAG, getName() + "Receive remote voice failed, Error code = " + ret);
                    continue;
                }
            }
        }
    }

    public interface RemoteAudioDataMonitor {
        void onAudioDataReceive(byte[] data, int size);
    }

    public void destorySession() {
        Log.d(TAG, "Destroy session sid = " + mSessionID);
        if (tutkCmdManger != null) {
            tutkCmdManger.closeIOCTRL();
        }
        releaseRemoteCallRes();
        if (mWriteThread != null) {
            mWriteThread.interrupt();
            mWriteThread = null;
        }
        if (mTutkSessionThread != null) {
            mTutkSessionThread.interrupt();
            mTutkSessionThread = null;
        }
        //release server mode
        AVAPIs.avServStop(mChannelID);
        AVAPIs.avServExit(mSessionID, MEDIA_IOTC_CHANNEL_ID);

        //release client mode
        AVAPIs.avClientStop(mAudioRecChannelID);
        AVAPIs.avClientExit(mSessionID, VOICE_IOTC_CHANNEL_ID);

        IOTCAPIs.IOTC_Session_Close(mSessionID);
        tutkCmdManger.uninit();
    }

    private void debug(String msg) {
        if (DEBUG || MainActivity.TRANSFER_DEBUG) {
            LogTool.d(TAG, msg);
        }
    }
}
