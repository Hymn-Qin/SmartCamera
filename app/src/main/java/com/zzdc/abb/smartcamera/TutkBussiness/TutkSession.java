package com.zzdc.abb.smartcamera.TutkBussiness;

import android.text.TextUtils;
import android.util.Log;

import com.tutk.IOTC.AVAPIs;
import com.tutk.IOTC.IOTCAPIs;
import com.tutk.IOTC.St_SInfo;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.controller.AvMediaTransfer;
import com.zzdc.abb.smartcamera.controller.MainActivity;
import com.zzdc.abb.smartcamera.info.FrameInfo;
import com.zzdc.abb.smartcamera.info.TutkFrame;
import com.zzdc.abb.smartcamera.util.LogTool;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class TutkSession implements AvMediaTransfer.AvTransferLister{
    private static final String TAG = TutkSession.class.getSimpleName();
    private static final boolean DEBUG = true;

    private int mSessionID;
    private int mChannelID;
    private int mAudioRecChannelID;

    private String gAcc = "admin";
    private String gPwd = "888888";
    protected CommApis main;

    private static final int MAX_BUF_SIZE = 1024;
    private static final int SERVTYPE_STREAM_SERVER = 16;
    private TutkSession tutkSession; //TODO ??  need  check

    private TutkCmdManager tutkCmdManger;
    private WriteThread mWriteThread;
    private Thread mReceiveAudioDataThread;
    private boolean ReceiveAudioRuning = false;

    private ArrayList<RemoteAudioDataMonitor> mRemoteAudioDataMonitors = new ArrayList<>();
    private int mState;

    public static final int SESSION_STATE_PREPARE = 1;
    public static final int SESSION_STATE_EXECUTING = 2;
    public static final int SESSION_STATE_IDLE = 3;
    private int mfailCount = 0;

    public int getState() {
        return mState;
    }

    public void setState(int mState) {
        this.mState = mState;
    }

    public int getSessionID() {
        return mSessionID;
    }

    public TutkSession(int sessionId) {
        super();
        mSessionID = sessionId;
        //TODO check encoderlistener counts
        tutkSession = this;
//        avMediaTransfer.registerAvTransferListener(this);
        setState(SESSION_STATE_PREPARE);
        TutkSessionThread tutkSessionThread = new TutkSessionThread(mSessionID);
        tutkSessionThread.start();
        tutkCmdManger  = new TutkCmdManager(this);

    }

    private class TutkSessionThread extends Thread {

        int mSID;

        public TutkSessionThread(int aSid) {
            mSID = aSid;
        }

        public void run() {

            super.run();
            int tmpResult = 0;
            try {
                LogTool.d(TAG, "ListenThread start");
                LogTool.d(TAG, "SID = " + mSID);

                int tmpAVIndex = 0;
                int[] bResend = new int[1];
                int[] srvType = new int[1];
                tmpAVIndex = AVAPIs.avServStart3(mSID, gAcc, gPwd, 2, SERVTYPE_STREAM_SERVER, 0, bResend);
//                LogTool.d(TAG, "avServStart3(), tmpAVIndex=" + tmpAVIndex + " sid = " + mSID + " bResend=" + bResend[0]);
                //用于接收client 发送的音频
                int avIndex = AVAPIs.avClientStart(mSID, "admin", "888888", 2, srvType, 1);
                LogTool.d(TAG, "avClientStart(), avIndex=" + avIndex + " sid = " + mSID + " bResend=" + bResend[0]);
                if (tmpAVIndex < 0) {
                    LogTool.d(TAG, "FAIL ... ");
                    IOTCAPIs.IOTC_Session_Close(mSID);
                }
                AVAPIs.avServSetResendSize(tmpAVIndex, 4096);
                St_SInfo info = new St_SInfo();
                String[] mode = {"P2P mode", "Relay mode", "Lan mode"};

                tmpResult = IOTCAPIs.IOTC_Session_Check(mSID, info);
                if (tmpResult == IOTCAPIs.IOTC_ER_NoERROR) {
                    LogTool.d(TAG, "   -> IP:Port= " + main.ByteToString(info.RemoteIP) + ":" + info.RemotePort);
                    if (info.Mode >= 0 && info.Mode <= 2) {
                        LogTool.d(TAG, "   -> Mode=[" + mode[info.Mode] + "]");
                    }
                    LogTool.d(TAG, "   -> NatType=[" + info.NatType + "]");
                    LogTool.d(TAG, "   -> Version=[" + info.IOTCVersion + "]");
                }
                mChannelID = tmpAVIndex;
                mAudioRecChannelID = avIndex;

                mWriteThread = new WriteThread("TUTK write thread sid=" + mSessionID);
                mWriteThread.start();

                byte[] ioCtrlBuf = new byte[MAX_BUF_SIZE];
                int[] ioType = new int[1];
                while (mState != SESSION_STATE_IDLE) {
                    tmpResult = AVAPIs.avRecvIOCtrl(tmpAVIndex, ioType, ioCtrlBuf, MAX_BUF_SIZE, 1000);
                    if (tmpResult >= 0) {
                        LogTool.d(TAG, "avRecvIOCtrl(), ioCtrlBuf_length= " + tmpResult);
                        byte[] jsonCmdBuf = new byte[tmpResult];
                        System.arraycopy(ioCtrlBuf,0,jsonCmdBuf,0,tmpResult);
                        setState(SESSION_STATE_EXECUTING);
                        tutkCmdManger.HandleIOCTRLCmd(mSessionID,mChannelID,jsonCmdBuf,ioType[0]);
                    }else if (tmpResult != AVAPIs.AV_ER_TIMEOUT) {
                        LogTool.d(TAG, "avRecvIOCtrl(), rc= " + tmpResult);
                        tutkCmdManger.closeIOCTRL();
                        break;
                    }
                }
//                avMediaTransfer.unRegisterAvTransferListener(tutkSession);
                AVAPIs.avServStop(tmpAVIndex);
                setState(SESSION_STATE_IDLE);
                LogTool.d(TAG, "SID = " + mSID + " avIndex = " + tmpAVIndex + " listen_ForAVServerStart Exit !!");
            } catch (Exception e) {
                LogTool.d(TAG, "ListenThread EXCEPTION " + e.toString());
            }
        }
    }

    public void registRemoteAudioDataMonitor(RemoteAudioDataMonitor aMonitor){
        if(aMonitor!=null && !mRemoteAudioDataMonitors.contains(aMonitor)){
            mRemoteAudioDataMonitors.add(aMonitor);
        }

    }

    public void unregistRemoteAudioDataMonitor(RemoteAudioDataMonitor aMonitor){
        mRemoteAudioDataMonitors.remove(aMonitor);
    }

    public void prepareForReceiveAudioData(){
        mReceiveAudioDataThread = new RemoteAudioReceiveThread();
        ReceiveAudioRuning = true;
        mReceiveAudioDataThread.start();
    }

    public void releaseRemoteCallRes(){
        ReceiveAudioRuning = false;
        if(mReceiveAudioDataThread != null){
            mReceiveAudioDataThread.interrupt();
            mReceiveAudioDataThread = null;
        }
    }


    public void writeMessge(String res){
        if (mWriteThread != null){
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
            LogTool.d(TAG, "TUTK write thread start, sid=" + mSessionID);
            while (mState != SESSION_STATE_IDLE && !interrupted()) {
                try {
                    String responseStr = mWriteList.take();
                    byte[] message = (byte[]) responseStr.getBytes();
                    int result =  AVAPIs.avSendIOCtrl(mChannelID, -1, message, message.length);
                    LogTool.d(TAG,"Send IOCtrl, sid=" + mSessionID +", result: " + result + ", message: " + responseStr);
                } catch (InterruptedException e) {
                    LogTool.w(TAG, "TUTK write thread exception, sid=" + mSessionID, e);
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
        if (tutkFrame == null || tutkFrame.getData() == null || tutkFrame.getFrameInfo() == null) {
            LogTool.w(TAG, "Video data not ready");
            return;
        }

        FrameInfo frameInfo = tutkFrame.getFrameInfo();
        byte[] buf_info = frameInfo.parseContent(frameInfo.codec_id, frameInfo.flags, frameInfo.timestamp);
        int rst = AVAPIs.avSendFrameData(mChannelID, tutkFrame.getData(), tutkFrame.getDataLen(), buf_info, buf_info.length);
        debug("Send video data, sid=" + mSessionID + ", result=" + rst + ", channel=" + mChannelID + ", size=" + tutkFrame.getDataLen());
        if (rst == AVAPIs.AV_ER_REMOTE_TIMEOUT_DISCONNECT) {
            LogTool.w(TAG,"AV_ER_REMOTE_TIMEOUT_DISCONNECT");
        } else if (rst == AVAPIs.AV_ER_SESSION_CLOSE_BY_REMOTE) {
            LogTool.w(TAG,"AV_ER_SESSION_CLOSE_BY_REMOTE");
        } else if (rst == IOTCAPIs.IOTC_ER_INVALID_SID) {
            LogTool.w(TAG,"IOTC_ER_INVALID_SID");
        } else if (rst < 0) {
            mfailCount = mfailCount + 1;
        }

        if (rst == AVAPIs.AV_ER_NoERROR){
            mfailCount = 0;
        }

        if(mfailCount >= 800 ){
            LogTool.e(TAG,"Send video data fail 800");
            setState(SESSION_STATE_IDLE);
        }
    }

    @Override
    public void sendAudioTutkFrame(TutkFrame tutkFrame) {
        if (tutkFrame == null || tutkFrame.getData() == null || tutkFrame.getFrameInfo() == null) {
            LogTool.w(TAG, "Audio data not ready");
            return;
        }

        FrameInfo frameInfo = tutkFrame.getFrameInfo();
        byte[] buf_info = frameInfo.parseContent(frameInfo.codec_id, frameInfo.flags,frameInfo.timestamp);
        int rst = AVAPIs.avSendAudioData(mChannelID, tutkFrame.getData(), tutkFrame.getDataLen(), buf_info, buf_info.length);
        debug("Send audio data, sid=" + mSessionID + ", result=" + rst + ", channel=" + mChannelID + ", size=" + tutkFrame.getDataLen());
        if (rst == AVAPIs.AV_ER_REMOTE_TIMEOUT_DISCONNECT) {
            LogTool.w(TAG,"AV_ER_REMOTE_TIMEOUT_DISCONNECT");
            setState(SESSION_STATE_IDLE);
        } else if (rst == AVAPIs.AV_ER_SESSION_CLOSE_BY_REMOTE) {
            LogTool.w(TAG,"AV_ER_SESSION_CLOSE_BY_REMOTE");
            setState(SESSION_STATE_IDLE);
        } else if (rst == IOTCAPIs.IOTC_ER_INVALID_SID) {
            LogTool.w(TAG,"IOTC_ER_INVALID_SID");
            setState(SESSION_STATE_IDLE);
        } else if (rst < 0) {
            LogTool.w(TAG,"IOTC_ER_xxx");
        }
    }

    public class RemoteAudioReceiveThread extends Thread{

        private static final int AUDIO_BUF_SIZE = 1024;
        private static final int FRAME_INFO_SIZE = 16;

        public RemoteAudioReceiveThread(){}

        @Override
        public void run() {
            Log.d(TAG,Thread.currentThread().getName() + " start");
            AVAPIs av = new AVAPIs();
            byte[] frameInfo = new byte[FRAME_INFO_SIZE];
            byte[] audioBuffer = new byte[AUDIO_BUF_SIZE];
            while (ReceiveAudioRuning) {

                int tmpRet = AVAPIs.avCheckAudioBuf(mAudioRecChannelID);
                System.out.println("tmpRet = " + tmpRet);

                if(tmpRet < 0){
                    System.out.printf("[%s] avCheckAudioBuf() failed: %d\n",
                            Thread.currentThread().getName(), tmpRet);
                    break;
                }else if(tmpRet < 3){
                    try {
                        Thread.sleep(40);
                        continue;
                    }
                    catch (InterruptedException e) {
                        System.out.println(e.getMessage());
                        break;
                    }

                }


                int[] frameNumber = new int[1];
                int ret =  AVAPIs.avRecvAudioData(mAudioRecChannelID, audioBuffer,
                        AUDIO_BUF_SIZE, frameInfo, FRAME_INFO_SIZE,
                        frameNumber);

              //  System.out.println("avRecvAudioData ret " + ret + " AUDIO_BUF_SIZE = " + AUDIO_BUF_SIZE  +" frameNumber = " +frameNumber[0]);
                if (ret == AVAPIs.AV_ER_SESSION_CLOSE_BY_REMOTE) {
                    System.out.printf("[%s] AV_ER_SESSION_CLOSE_BY_REMOTE\n",
                            Thread.currentThread().getName());
                    break;
                }
                else if (ret == AVAPIs.AV_ER_REMOTE_TIMEOUT_DISCONNECT) {
                    System.out.printf("[%s] AV_ER_REMOTE_TIMEOUT_DISCONNECT\n",
                            Thread.currentThread().getName());
                    break;
                }
                else if (ret == AVAPIs.AV_ER_INVALID_SID) {
                    System.out.printf("[%s] Session cant be used anymore\n",
                            Thread.currentThread().getName());
                    break;
                }
                else if (ret == AVAPIs.AV_ER_LOSED_THIS_FRAME) {
                    //System.out.printf("[%s] Audio frame losed\n",
                    //        Thread.currentThread().getName());
                    continue;
                }

                // Now the data is ready in audioBuffer[0 ... ret - 1]
                // Do something here
                if (ret > 0){
                    System.out.println("avRecvAudioData ret " + ret + " audioBuffer " + audioBuffer + " AUDIO_BUF_SIZE = " + AUDIO_BUF_SIZE  + "frameInfo "+ frameInfo+" frameNumber = " +frameNumber[0]);
                    for (RemoteAudioDataMonitor aMonitor : mRemoteAudioDataMonitors){
                        aMonitor.onAudioDataReceive(audioBuffer, ret);
                    }

                }


            }

//            System.out.printf("[%s] Exit\n",
//                    Thread.currentThread().getName());
        }
    }

    public interface RemoteAudioDataMonitor{
        public void onAudioDataReceive(byte[] data, int size);
    }


    public void destorySession(){

        Log.d(TAG,"destorySession sid = " + mSessionID);
        if(tutkCmdManger != null){
            tutkCmdManger.closeIOCTRL();
        }

        if (mReceiveAudioDataThread != null && !mReceiveAudioDataThread.isInterrupted()){
            mReceiveAudioDataThread.interrupt();
            mReceiveAudioDataThread = null;
        }

        if(mWriteThread != null && !mWriteThread.isInterrupted()){
            mWriteThread.interrupt();
            mWriteThread = null;
        }
        //release server mode
        AVAPIs.avServStop(mChannelID);
        AVAPIs.avServExit(mSessionID,0);
        //release client mode
        AVAPIs.avClientStop(mAudioRecChannelID);
        AVAPIs.avClientExit(mSessionID,1);

        IOTCAPIs.IOTC_Session_Close(mSessionID);
        tutkCmdManger.uninit();
    }

    private void debug(String msg) {
        if (DEBUG || MainActivity.TRANSFER_DEBUG) {
            LogTool.d(TAG, msg);
        }
    }
}
