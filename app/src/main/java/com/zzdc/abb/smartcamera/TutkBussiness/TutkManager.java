package com.zzdc.abb.smartcamera.TutkBussiness;

import android.util.Log;

import com.tutk.IOTC.AVAPIs;
import com.tutk.IOTC.IOTCAPIs;
import com.tutk.IOTC.St_SInfo;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TutkManager extends IOTCAPIs {
    private static final String TAG = TutkManager.class.getSimpleName();
    private static final TutkManager ourInstance = new TutkManager();

    public static final int COMMAPIS_STOPPED = -1001;
    boolean m_bStoped = true;
    protected St_SInfo m_stSInfo = new St_SInfo();
    public static int ms_nIOTCInit = IOTCAPIs.IOTC_ER_TIMEOUT;

    public static final int STATUS_INIT_SEARCH_DEV = 10;

    protected String mUID;
    protected LoginThread mLoginThread = null;
    private MainThread mMainThread = null;
    private RecycleThread mRecycleThread = null;
    private ConcurrentHashMap<String,TutkSession> mSessions = new ConcurrentHashMap<>();

    private static final int MAX_CLIENT = 12;

    public static TutkManager getInstance() {
        return ourInstance;
    }

    private TutkManager() {
    }

    public void init() {
        mUID = Constant.TUTK_DEVICE_UID;

        int[] tmpVersion = new int[1];
        IOTCAPIs.IOTC_Get_Version(tmpVersion);
        String tmpVer = verN2Str(tmpVersion[0]);
        LogTool.d(TAG, "init TUTK version =  " + tmpVer);

        if (ms_nIOTCInit != IOTCAPIs.IOTC_ER_NoERROR) {
            ms_nIOTCInit = IOTC_Initialize2(0);
        }
        IOTC_Set_Max_Session_Number(MAX_CLIENT);
        AVAPIs.avInitialize(MAX_CLIENT);

        startSession();
    }

    private void create_streamout_thread() {
//        mVideoSendThread = new VideoThread();
//        mVideoSendThread.start();
    }
    public void StartCall(){
        String jsonStr ="{" +
                "   \"type\" : \"START_ONE_KEY_CALL\"" +
                "}";
        for(TutkSession mSession : mSessions.values()) {
            Log.d("keming14","Send call message" + jsonStr);
            mSession.writeMessge(jsonStr);
        }
    }
    public void StopCall(){
        String jsonStr ="{" +
                "   \"type\" : \"STOP_ONE_KEY_CALL\"" +
                "}";
        for(TutkSession mSession : mSessions.values()) {
            Log.d("keming14","Send call message" + jsonStr);
            mSession.writeMessge(jsonStr);

        }

    }

    private void startSession() {
        m_bStoped = false;
        //start login thread
        if (mLoginThread == null) {
            mLoginThread = new LoginThread();
            mLoginThread.start();
        }
        //start Listen thread
        if (mMainThread == null) {
            mMainThread = new MainThread();
            mMainThread.start();
        }
        //start RecyclyThread thread
        if(mRecycleThread == null){
            mRecycleThread = new RecycleThread();
            mRecycleThread.start();
        }
    }

    class RecycleThread extends Thread{
        public void run(){

            while(true){
                synchronized (this) {
                    if (mSessions == null || mSessions.size()==0){
                        try {
                            Thread.sleep(2000);
                        } catch (Exception e) {
                            Log.d(TAG,"RecycleThread Exception " + e.toString());
                        }
                        continue;
                    }


                    Iterator<Map.Entry<String, TutkSession>> entries = mSessions.entrySet().iterator();
                    while (entries.hasNext()){

                        Map.Entry<String, TutkSession> entry = entries.next();
                        TutkSession tmpSession = (TutkSession)entry.getValue();
                        if (tmpSession.getState() == TutkSession.SESSION_STATE_IDLE){
                            String tmpSID = entry.getKey();
                            Log.d(TAG,"TutkManager destorySession IOTC_Session_Close " + Integer.valueOf(tmpSID).intValue());

                            tmpSession.destorySession();
                            tmpSession = null;
                            mSessions.remove(tmpSID);
                            IOTCAPIs.IOTC_Session_Close(Integer.valueOf(tmpSID).intValue());

                        }
                    }
                }
            }

        }
    }

    class MainThread extends Thread {

        boolean mbStopedSure = false;
        int nSID = -1;

        public MainThread() {
            mbStopedSure = false;
        }

        public void run() {
            do {
                nSID = IOTC_Listen(0);

                if (m_bStoped) break;
                if (clientConnectDev(nSID) < 0) continue;

                synchronized (this) {
                    int nCheck = IOTC_Session_Check(nSID, m_stSInfo);
                    if (nCheck >= 0) {
                        //creat  TutkSession Thread
                        LogTool.d(TAG, "session id:" + nSID);
                        TutkSession tutkSession = new TutkSession(nSID);
                        mSessions.put(String.valueOf(nSID),tutkSession);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

            } while (!m_bStoped);
            mbStopedSure = true;
        }

        private int clientConnectDev(int SID) {
            nSID = SID;
            String str = null;
            if (nSID < 0) {
                switch (nSID) {
                    case IOTCAPIs.IOTC_ER_NOT_INITIALIZED:
                        str = String.format("Don't call IOTC_Initialize() when connecting.(%d)", STATUS_INIT_SEARCH_DEV);
                        break;

                    case IOTCAPIs.IOTC_ER_CONNECT_IS_CALLING:
                        str = String.format("IOTC_Connect_ByXX() is calling when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_FAIL_RESOLVE_HOSTNAME:
                        str = String.format("Can't resolved server's Domain name when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_SERVER_NOT_RESPONSE:
                        str = String.format("Server not response when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_FAIL_GET_LOCAL_IP:
                        str = String.format("Can't Get local IP when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_UNKNOWN_DEVICE:
                        str = String.format("Wrong UID when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_UNLICENSE:
                        str = String.format("UID is not registered when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_CAN_NOT_FIND_DEVICE:
                        str = String.format("Device is NOT online when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_EXCEED_MAX_SESSION:
                        str = String.format("Exceed the max session number when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_TIMEOUT:
                        str = String.format("Timeout when connecting.(%d)", nSID);
                        break;

                    case IOTCAPIs.IOTC_ER_DEVICE_NOT_LISTENING:
                        str = String.format("The device is not on listening when connecting.(%d)", nSID);
                        break;

                    default:
                        str = String.format("Failed to connect device when connecting.(%d)", nSID);
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                IOTCAPIs.IOTC_Session_Check(nSID, m_stSInfo);
                str = "  " + ((m_stSInfo.Mode == 0) ? "P2P" : "Relay") + ", NAT=type" + IOTCAPIs.IOTC_Get_Nat_Type();
            }
            LogTool.d(TAG, "clientConnectDev:" + str);
            if (m_bStoped) return COMMAPIS_STOPPED;
            else return nSID;
        }

    }

    class LoginThread extends Thread {

        int nRet = -1;
        boolean mStopedSure = false;

        public void run() {
            super.run();
            LogTool.d(TAG, "LoginThread start");
            int i = 0;
            String tmpUID = Constant.TUTK_DEVICE_UID;
            do {
                nRet = IOTC_Device_Login(tmpUID, null, null);
                System.out.println("IOTC_Device_Login(...) = " + nRet);
                LogTool.d(TAG, "tmpUID = " + tmpUID + " IOTC_Device_Login return " + nRet);

                if (nRet == IOTCAPIs.IOTC_ER_NoERROR) {

                    LogTool.d(TAG, "IOTC_Device_Login(...)  sucess");
                    break;
                } else if (nRet == IOTCAPIs.IOTC_ER_NETWORK_UNREACHABLE) {
                    LogTool.d(TAG, "Network is unreachable, please check the network settings");
                }

            } while (!m_bStoped);
            LogTool.d(TAG, "LoginThread exit");
            mStopedSure = true;
        }
    }

    public String verN2Str(long nVer) {
        String strVer = String.format("%d.%d.%d.%d", (nVer >> 24) & 0xff, (nVer >> 16) & 0xff, (nVer >> 8) & 0xff, nVer & 0xff);
        return strVer;
    }

    public void unInit() {
        LogTool.d(TAG, "***********DeInitialize***************");
        int rc = 0;
        rc = AVAPIs.avDeInitialize();
        LogTool.d(TAG, "avDeInitialize rc = " + rc);
        rc = IOTCAPIs.IOTC_DeInitialize();
        LogTool.d(TAG, "IOTC_DeInitialize rc = " + rc);
    }


//    private void sendBroadcast(boolean aIsstartIpCamera) {
//
//        Intent tmpIntent = new Intent();
//        String tmpAction = null;
//        if (aIsstartIpCamera) {
//            tmpAction = "com.zzdc.action.START_IP_CAMERA";
//        } else {
//            tmpAction = "com.zzdc.action.STOP_IP_CAMERA";
//        }
//        LogTool.d(TAG, "sendBroadcast: " + tmpAction);
//        tmpIntent.setAction(tmpAction);
////        tmpIntent.setComponent(new ComponentName("com.zzdc.zhipengli.smartcamera","MyReceiver"));
//        try {
//
//            SmartCameraApplication.getContext().sendBroadcast(tmpIntent);
//        } catch (Exception e) {
//            LogTool.d(TAG, "Exception :" + e.toString());
//        }
//
//
//    }


}
