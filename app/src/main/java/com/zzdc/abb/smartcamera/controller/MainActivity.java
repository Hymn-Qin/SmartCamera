package com.zzdc.abb.smartcamera.controller;
/**
 * camera 1 api
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.ptz.motorControl.MotorManager;
import com.zzdc.abb.smartcamera.R;
import com.zzdc.abb.smartcamera.TutkBussiness.TutkManager;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.info.Device;
import com.zzdc.abb.smartcamera.util.TUTKUIDUtil;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, SurfacePreview.PermissionNotify {

    private static final String TAG = "MainActivity";
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Button btnStart;
    private Button btnCall;
    private SurfacePreview mSurfacePreview;
    private boolean RecordRuning = false;
    private ApplicationSetting mAplicationSetting = null;

    private TutkManager mTutk;
    private AvMediaRecorder mAvMediaRecorder = null;
    private static final int CallStateOff = 0;
    private static final int CallStateOn = 1;
    private OneKeyCallReciever mOneKeyCallReciever;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    public static final String PREFRENCE = "com.zzdc.abb.smartcamera";
    public static final String ACTION_START_MONITOR = "com.zzdc.action.START_MONITOR";
    public static final String ACTION_STOP_MONITOR = "com.zzdc.action.STOP_MONITOR";
    private boolean mIsEnableMonitor;
    private static Context mContext;

    private int CallState = CallStateOff;
    public static final  String DEVICE_INFO_FILE = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator + "device.json";
    final MotorManager mMotorManager = MotorManager.getManger();
    private HistoryManager mHistoryManager;

    private Handler mControllerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Constant.START_RECORD:
                    StartRecord();
                    RecordRuning = true;
                    break;
                case Constant.INIT_TUTK_UID:
                    getTUTKUID();
                    break;
                default:
                    break;
            }
        }
    };
    public static Context getContext(){
        return mContext;
    }


    public class OneKeyCallReciever extends BroadcastReceiver {

        private final String  TAG = OneKeyCallReciever.class.getSimpleName();
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: This method is called when the BroadcastReceiver is receiving
            // an Intent broadcast.
            Log.e(TAG, "onReceive " + intent.getAction());
            if (intent.getAction().equalsIgnoreCase("com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED")){
                if(CallState == CallStateOff) {
                    CallState = CallStateOn;
                    if(mTutk != null) {
                        mTutk.StartCall();
                        //       Toast.makeText(MainActivity.this, "start Call", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
                    }
                }else if(CallState == CallStateOn) {
                    CallState = CallStateOff;
                    if (mTutk != null) {
                        mTutk.StopCall();
                        //       Toast.makeText(MainActivity.this, "start Call", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
                    }
                }
            }else if(intent.getAction().equalsIgnoreCase(ACTION_START_MONITOR)){
             //   MonitorOn = true;
                mAplicationSetting.setSystemMonitorSetting(true);
                mIsEnableMonitor  = mAplicationSetting.getSystemMonitorSetting();
                Log.d(TAG,"isOpenMonitor = " + mIsEnableMonitor);

            }else if(intent.getAction().equalsIgnoreCase(ACTION_STOP_MONITOR)){
              //  MonitorOn = false;
                mAplicationSetting.setSystemMonitorSetting(false);
                mIsEnableMonitor  = mAplicationSetting.getSystemMonitorSetting();
                Log.d(TAG,"isOpenMonitor = " + mIsEnableMonitor);

            }

        }
    }





    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        RecordRuning = false;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main2);
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
        mOneKeyCallReciever = new OneKeyCallReciever();
        registerReceiver(mOneKeyCallReciever, filter);
//        mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
//        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
//        mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
//        mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
//         mAudioManager.setSpeakerphoneOn(true);
        mContext = getApplicationContext();


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mAplicationSetting = ApplicationSetting.getInstance();
        mAplicationSetting.SetContext(this);
   //     mAplicationSetting.setSystemMonitorSetting(true);
        mIsEnableMonitor = mAplicationSetting.getSystemMonitorSetting();


//        mSharedPreferences = getSharedPreferences(PREFRENCE, Context.MODE_PRIVATE);
//        mEditor = mSharedPreferences.edit();
//        setSystemMonitorSetting(true);
//        mIsEnableMonitor  = getSystemMonitorSetting();
        Log.d(TAG,"isOpenMonitor = " + mIsEnableMonitor);
        initUI();
        mMotorManager.initMotor();
        btnStart.setEnabled(true);
        startMonitorIfNeeded();

    }



    private void initTUTK() {
        mTutk = TutkManager.getInstance();
        mTutk.init();
    }

    private void initUI() {
        RelativeLayout.LayoutParams lp;
        mSurfaceView = (SurfaceView) findViewById(R.id.sv_surfaceview2);
        mSurfaceView.setKeepScreenOn(true);
        mHolder = mSurfaceView.getHolder();
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        lp = new RelativeLayout.LayoutParams(1080,
                810);
        if (mSurfaceView != null) {
            mSurfaceView.setLayoutParams(lp);
        }

        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfacePreview = new SurfacePreview(this,this);
        mHolder.addCallback(mSurfacePreview);
        if (mAvMediaRecorder == null) {
            mAvMediaRecorder = AvMediaRecorder.getInstance();
        }
        mAvMediaRecorder.setmActivity(this);
        mAvMediaRecorder.setmHolder(mHolder);
        btnStart = (Button) findViewById(R.id.start_muxer);
        btnStart.setText(mAplicationSetting.getSystemMonitorSetting() ? "停止" : "开始");
        btnStart.setOnClickListener(this);

        btnCall = (Button) findViewById(R.id.VoiceCall);
        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(CallState == CallStateOff) {
                    CallState = CallStateOn;
                    if(mTutk != null) {
                        mTutk.StartCall();
                        //       Toast.makeText(MainActivity.this, "start Call", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
                    }
                }else if(CallState == CallStateOn) {
                    CallState = CallStateOff;
                    if (mTutk != null) {
                        mTutk.StopCall();
                        //       Toast.makeText(MainActivity.this, "start Call", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
                    }
                }
            }
        });


    }
    private void startMonitorIfNeeded(){
        mControllerHandler.sendEmptyMessageDelayed(Constant.INIT_TUTK_UID, 3000);
        //根据SharedPreferences中是否启动监控打开监控
        if(mAplicationSetting.getSystemMonitorSetting()){
            Log.d(TAG,"from SharedPreferences start monitor");
            mControllerHandler.sendEmptyMessageDelayed(Constant.START_RECORD, 6000);
        } else {
            Log.d(TAG,"from SharedPreferences do not start monitor");
        }
    }

    private void StartRecord(){

        mAvMediaRecorder.init();
        mAvMediaRecorder.avMediaRecorderStart();

    }
    private void StopRecord(){
        mAvMediaRecorder.avMediaRecorderStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMotorManager.closeMotor();

        mTutk.unInit();
        if (RecordRuning) {
            RecordRuning = false;
            mAvMediaRecorder.avMediaRecorderStop();
            mAvMediaRecorder = null;
        }
        VideoGather.getInstance().doStopCamera();
        unregisterReceiver(mOneKeyCallReciever);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_muxer:
             //   startRecord();
                Log.d(TAG,"record clicked");
                if(RecordRuning == false)
                {
                    RecordRuning = true;
                    mAplicationSetting.setSystemMonitorSetting(true);
                    StartRecord();
                    Log.d(TAG,"start record ");
                } else{
                    RecordRuning = false;
                    mAplicationSetting.setSystemMonitorSetting(false);
                    StopRecord();
                    Log.d(TAG,"stop record ");
                }
                btnStart.setText(RecordRuning ? "停止" : "开始");
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        return;
    }

    @Override
    public boolean hasPermission() {
        return true;
    }

//    @Override
//    public void cameraHasOpened() {
//        VideoGather.getInstance().doStartPreview(this, mHolder);
//    }
//
//    @Override
//    public void cameraHasPreview(int width, int height, int fps) {
//        this.width = width;
//        this.height = height;
//        this.frameRate = fps;
//    }


    public void getTUTKUID() {
        Log.d(TAG, " setTUTKUID ");
        File tmpFile = new File(DEVICE_INFO_FILE);
        StringBuilder tmpStringBuilder = null;
        String line;

        if (!tmpFile.exists()) {

            String tmpUid = TUTKUIDUtil.getTUTKUID();
            if (TextUtils.isEmpty(tmpUid)) {
                Log.d(TAG, "tmpUid == NULL, retry");
                tmpUid = "02:00:00:00:00:00";
            }
            Log.d(TAG,"TUTK UID = " + tmpUid);
            Device tmpDevie = new Device();
            tmpDevie.setUID(tmpUid);
            String tmpJson;
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("mUID", tmpDevie.getUID());
                tmpJson = jsonObject.toString();
                FileOutputStream tmpFOS = new FileOutputStream(tmpFile, false);
                tmpFOS.write(tmpJson.getBytes("utf-8"));
                tmpFOS.close();
                Log.d(TAG, "tmpDevie.getUID() = " + tmpDevie.getUID() + " uid length = " + tmpDevie.getUID().length());
                Constant.TUTK_DEVICE_UID = tmpDevie.getUID();
            } catch (Exception e) {
                Log.d(TAG, "Exception " + e.toString());
                e.printStackTrace();
            }

        } else {
            if (tmpFile.length() > 0) {
                try {
                    FileInputStream tmpFIS = new FileInputStream(tmpFile);
                    InputStreamReader tmpStreamReader = new InputStreamReader(tmpFIS);
                    BufferedReader reader = new BufferedReader(tmpStreamReader);
                    tmpStringBuilder = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        // stringBuilder.append(line);
                        tmpStringBuilder.append(line);
                    }
                    JSONObject jsonObject = new JSONObject(tmpStringBuilder.toString());
                    Log.d(TAG, "mUID " + jsonObject.get("mUID"));
                    Constant.TUTK_DEVICE_UID = jsonObject.get("mUID").toString();
                    reader.close();
                    tmpFIS.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

        if (Constant.TUTK_DEVICE_UID.length() == 20) {
            initTUTK();
        }
    }
}
