package com.zzdc.abb.smartcamera.TutkBussiness;

import android.util.Log;

import com.tutk.IOTC.AVAPIs;
import com.tutk.IOTC.IOTCAPIs;
import com.tutk.IOTC.St_SInfo;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.util.LogTool;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TutkManager extends IOTCAPIs {
    private static final String TAG = TutkManager.class.getSimpleName();

    private boolean m_bStoped = true;
    private St_SInfo m_stSInfo = new St_SInfo();

    private final ConcurrentHashMap<String,TutkSession> mSessions = new ConcurrentHashMap<>();
    private LoginThread mLoginThread = null;
    private MainThread mMainThread = null;
    private RecycleThread mRecycleThread = null;

    private static final int MAX_CLIENT = 12;

    private static final TutkManager ourInstance = new TutkManager();
    private TutkManager() {}
    public static TutkManager getInstance() {
        return ourInstance;
    }

    public void init() {
        LogTool.d(TAG, "Init TUTK, Device UID = " + Constant.TUTK_DEVICE_UID);
        int[] tmpVersion = new int[1];
        IOTCAPIs.IOTC_Get_Version(tmpVersion);
        LogTool.d(TAG, "TUTK version =  " + verN2Str(tmpVersion[0]));

        int io_error = IOTC_Initialize2(0);
        LogTool.d(TAG, "Initialize IOTC with result  " + io_error);

        IOTC_Set_Max_Session_Number(MAX_CLIENT);
        AVAPIs.avInitialize(MAX_CLIENT);

        startSession();
    }

    public void unInit() {
        LogTool.d(TAG, "DeInitialize TUTK");
        int rc = AVAPIs.avDeInitialize();
        LogTool.d(TAG, "avDeInitialize rc = " + rc);
        rc = IOTCAPIs.IOTC_DeInitialize();
        LogTool.d(TAG, "IOTC_DeInitialize rc = " + rc);
    }

    public void StartCall(){
        String jsonStr ="{ \"type\" : \"START_ONE_KEY_CALL\"}";
        for(TutkSession mSession : mSessions.values()) {
            LogTool.d(TAG,"Send call message: " + jsonStr);
            mSession.writeMessge(jsonStr);
        }
    }

    public void StopCall(){
        String jsonStr ="{\"type\" : \"STOP_ONE_KEY_CALL\"}";
        for(TutkSession mSession : mSessions.values()) {
            LogTool.d(TAG,"Send call message: " + jsonStr);
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
                try {
                    Thread.sleep(2000);
                } catch (Exception e) {
                    LogTool.w(TAG,"RecycleThread sleep Exception. ", e);
                }
                if (mSessions.size()==0){
                    continue;
                }
                Iterator<Map.Entry<String, TutkSession>> entries = mSessions.entrySet().iterator();
                while (entries.hasNext()){
                    Map.Entry<String, TutkSession> entry = entries.next();
                    TutkSession session = entry.getValue();
                    if (session.getState() == TutkSession.SESSION_STATE_IDLE){
                        String sid = entry.getKey();
                        session.destorySession();
                        mSessions.remove(sid);
                        Log.d(TAG,"Close session " + Integer.valueOf(sid));
                    }
                }
            }

        }
    }

    class MainThread extends Thread {
        public void run() {
            do {
                int sid = IOTC_Listen(0);
                checkConnect(sid);
                if (m_bStoped) break;
                if (sid < 0) continue;

                int nCheck = IOTC_Session_Check(sid, m_stSInfo);
                if (nCheck >= 0) {
                    TutkSession tutkSession = new TutkSession(sid);
                    mSessions.put(String.valueOf(sid),tutkSession);
                    LogTool.d(TAG, "Create session id:" + sid);
                }
            } while (!m_bStoped);
        }

        private void checkConnect(int sid) {
            StringBuilder str = new StringBuilder("sid = " + sid).append(", ");
            if (sid < 0) {
                switch (sid) {
                    case IOTCAPIs.IOTC_ER_NOT_INITIALIZED:
                        str.append("Don't call IOTC_Initialize() when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_CONNECT_IS_CALLING:
                        str.append("IOTC_Connect_ByXX() is calling when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_FAIL_RESOLVE_HOSTNAME:
                        str.append("Can't resolved server's Domain name when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_SERVER_NOT_RESPONSE:
                        str.append("Server not response when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_FAIL_GET_LOCAL_IP:
                        str.append("Can't Get local IP when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_UNKNOWN_DEVICE:
                        str.append("Wrong UID when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_UNLICENSE:
                        str.append("UID is not registered when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_CAN_NOT_FIND_DEVICE:
                        str.append("Device is NOT online when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_EXCEED_MAX_SESSION:
                        str.append("Exceed the max session number when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_TIMEOUT:
                        str.append("Timeout when connecting.");
                        break;
                    case IOTCAPIs.IOTC_ER_DEVICE_NOT_LISTENING:
                        str.append("The device is not on listening when connecting.");
                        break;
                    default:
                        str.append("Failed to connect device when connecting.");
                }
            } else {
                IOTCAPIs.IOTC_Session_Check(sid, m_stSInfo);
                str.append(((m_stSInfo.Mode == 0) ? "P2P" : "Relay")).append(", NAT=type").append(IOTCAPIs.IOTC_Get_Nat_Type());
            }
            LogTool.d(TAG, "checkConnect: " + str);
        }

    }

    class LoginThread extends Thread {
        public void run() {
            super.run();
            LogTool.d(TAG, "LoginThread start");
            String tmpUID = Constant.TUTK_DEVICE_UID;
            do {
                int ret = IOTC_Device_Login(tmpUID, null, null);
                LogTool.d(TAG, "tmpUID = " + tmpUID + " IOTC_Device_Login return " + ret);

                if (ret == IOTCAPIs.IOTC_ER_NoERROR) {
                    LogTool.d(TAG, "IOTC_Device_Login(...)  success");
                    break;
                } else if (ret == IOTCAPIs.IOTC_ER_NETWORK_UNREACHABLE) {
                    LogTool.d(TAG, "Network is unreachable, please check the network settings");
                }
            } while (!m_bStoped);
            LogTool.d(TAG, "LoginThread exit");
        }
    }

    private String verN2Str(long nVer) {
        return String.format("%d.%d.%d.%d", (nVer >> 24) & 0xff, (nVer >> 16) & 0xff, (nVer >> 8) & 0xff, nVer & 0xff);
    }
}
