package com.zzdc.abb.smartcamera.controller;
/**
 * camera 1 api
 */

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.ptz.motorControl.MotorCmd;
import com.ptz.motorControl.MotorManager;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.zzdc.abb.smartcamera.R;
import com.zzdc.abb.smartcamera.SmartCameraReceiver;
import com.zzdc.abb.smartcamera.SmartCameraService;
import com.zzdc.abb.smartcamera.TutkBussiness.TutkManager;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.info.Device;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.SmartCameraApplication;
import com.zzdc.abb.smartcamera.util.TUTKUIDUtil;
import com.zzdc.abb.smartcamera.util.WindowUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private Button btnStart;
    private Button btnCall;
    private SurfacePreview mSurfacePreview;
    public static boolean RecordRuning = false;
    private ApplicationSetting mAplicationSetting = null;

    private TutkManager mTutk;
    private AvMediaRecorder mAvMediaRecorder = null;

    private OneKeyCallReciever mOneKeyCallReciever;
    public static final String ACTION_START_MONITOR = "com.zzdc.action.START_MONITOR";
    public static final String ACTION_STOP_MONITOR = "com.zzdc.action.STOP_MONITOR";
    private boolean mIsEnableMonitor;
    private static Context mContext;

    public static final int CallStateOff = 0;
    public static final int CallStateOn = 1;
    public static int CallState = CallStateOff;

    private AudioManager mAudioManager;

    public static final  String DEVICE_INFO_FILE = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator + "device.json";
    final MotorManager mMotorManager = MotorManager.getManger();

    public static MainActivity mainActivity;

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
                int mode = mAudioManager.getMode();
                Log.d(TAG,"mode="+mode + ",CallState="+CallState);

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

            } else if (intent.getAction().equalsIgnoreCase("com.foxconn.alert.camera.play")) {
                String type = intent.getStringExtra("type");
                String message = intent.getStringExtra("message");
                Log.d("qxj", "get receive -- type = " + type + " message = " + message);
                if (type.equals("ALERT")) {
                    if (!AvMediaRecorder.AlertRecordRunning){
                        mAvMediaRecorder.startAlertRecord();
                    } else {
                        mAvMediaRecorder.resetStopTime(0);
                    }
                } else if (type.equals("Camera")) {
                    if (message.equals("false")) {
                        if (mAplicationSetting.getSystemMonitorOKSetting()) {
                            mAplicationSetting.setSystemMonitorOKSetting(false);
                            if (RecordRuning) {
                                StopRecord();
                                RecordRuning = false;
                            }
                        }

                    } else if (message.equals("true")) {

                        if (!mAplicationSetting.getSystemMonitorOKSetting()) {
                            mAplicationSetting.setSystemMonitorOKSetting(true);
                            if (!RecordRuning) {
                                StartRecord();
                                RecordRuning = true;
                            }
                        }
                    }
                    Log.d("qxj", "send com.foxconn.zzdc.broadcast.camera receive -- type = " + type + " message = " + message);
                    Intent intents = new Intent("com.foxconn.zzdc.broadcast.camera");
                    intents.putExtra("type", type);
                    intents.putExtra("result", message);
                    context.sendBroadcast(intents);
                }
            }

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 100){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    WindowUtils.showPopupWindow(this);
                    initUI();
                    mMotorManager.initMotor();
                    btnStart.setEnabled(true);
                    startMonitorIfNeeded();
                }else {
                    Toast.makeText(this,"ACTION_MANAGE_OVERLAY_PERMISSION权限已被拒绝",Toast.LENGTH_SHORT).show();;
                }
            }
        }
    }
    private SmartCameraReceiver receiver = new SmartCameraReceiver();
    private void registerResetWifiBroadcast(){
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.foxconn.zzdc.broadcast.OPEN_HOT_SPOT");
        Log.d(TAG,"qr scan register ");
        registerReceiver(receiver, filter);
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent service = new Intent(this, SmartCameraService.class);
//        this.startForegroundService(service);
        startService(service);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        RecordRuning = false;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main2);
        initDebug();
        mainActivity = this;
        registerResetWifiBroadcast();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
        mOneKeyCallReciever = new OneKeyCallReciever();
        registerReceiver(mOneKeyCallReciever, filter);

        mContext = getApplicationContext();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mAplicationSetting = ApplicationSetting.getInstance();
        mAplicationSetting.SetContext(this);
        mIsEnableMonitor = mAplicationSetting.getSystemMonitorSetting();

        //数据库初始化
        FlowManager.init(this);
        //设备姿态
        MotorCmd.DEVICE_POSE = mAplicationSetting.getDevicePose();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(!Settings.canDrawOverlays(getApplicationContext())) {
                //启动Activity让用户授权
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent,100);
            } else {
                initUI();
                mMotorManager.initMotor();
                btnStart.setEnabled(true);
                startMonitorIfNeeded();
            }
        }
    }

    private void initTUTK() {
        mTutk = TutkManager.getInstance();
        mTutk.init();
    }

    private void initUI() {
        WindowUtils.showPopupWindow(this);
        mSurfacePreview = new SurfacePreview(this);
        mSurfacePreview.setVisibility(View.VISIBLE);

        WindowUtils.addView(mSurfacePreview);
        if (mAvMediaRecorder == null) {
            mAvMediaRecorder = AvMediaRecorder.getInstance();
        }
        mAvMediaRecorder.setmActivity(this);
        mAvMediaRecorder.setmHolder(SurfacePreview.surfaceHolder);
        btnStart = findViewById(R.id.start_muxer);
        btnStart.setText(mAplicationSetting.getSystemMonitorSetting() ? "停止" : "开始");
        btnStart.setOnClickListener(this);

        btnCall = findViewById(R.id.VoiceCall);
        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG,"CallState="+CallState + ",mode="+mAudioManager.getMode());
                if(CallState == CallStateOff) {
                    CallState = CallStateOn;
                    if(mTutk != null) {
                        mTutk.StartCall();
                        Log.d(TAG, "com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
                    }
                }else if(CallState == CallStateOn) {
                    CallState = CallStateOff;
                    if (mTutk != null) {
                        mTutk.StopCall();
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
        if (!mAplicationSetting.getSystemMonitorOKSetting()) {
            return;
        }
        mAvMediaRecorder.init();
        mAvMediaRecorder.avMediaRecorderStart();
    }

    private void StopRecord(){
        mAvMediaRecorder.avMediaRecorderStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WindowUtils.removeView(mSurfacePreview);
        mMotorManager.closeMotor();
        if(mTutk!=null){
            mTutk.unInit();
        }
        if (RecordRuning) {
            RecordRuning = false;
            mAvMediaRecorder.avMediaRecorderStop();
            mAvMediaRecorder = null;
        }
        VideoGather.getInstance().doStopCamera();
        unregisterReceiver(mOneKeyCallReciever);
        unregisterReceiver(receiver);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_muxer:
             //   startRecord();
                Log.d(TAG,"record clicked");
                if(!RecordRuning) {
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
    public void onBackPressed() { }

    public void getTUTKUID() {
        Log.d(TAG, " getTUTKUID ");
        File tmpFile = new File(DEVICE_INFO_FILE);
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
                    StringBuilder tmpStringBuilder = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
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

    public static boolean AUDIO_GATHER_DEBUG = false;
    public static boolean AUDIO_ENCODE_DEBUG = false;
    public static boolean VIDEO_GATHER_DEBUG = false;
    public static boolean VIDEO_ENCODE_DEBUG = false;
    public static boolean MUXER_DEBUG = false;
    public static boolean TRANSFER_DEBUG = false;
    public static boolean BUFFER_POOL_DEBUG = false;
    private void initDebug() {
        CompoundButton.OnCheckedChangeListener switchListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                LogTool.d(TAG, "onCheckedChanged");
                switch (buttonView.getId()) {
                    case R.id.audio_gather_debug:
                        AUDIO_GATHER_DEBUG = isChecked;
                        break;
                    case R.id.audio_encode_debug:
                        AUDIO_ENCODE_DEBUG = isChecked;
                        break;
                    case R.id.video_gather_debug:
                        VIDEO_GATHER_DEBUG = isChecked;
                        break;
                    case R.id.video_encode_debug:
                        VIDEO_ENCODE_DEBUG = isChecked;
                        break;
                    case R.id.muxer_debug:
                        MUXER_DEBUG = isChecked;
                        break;
                    case R.id.transfer_debug:
                        TRANSFER_DEBUG = isChecked;
                        break;
                    case R.id.buffer_pool_debug:
                        BUFFER_POOL_DEBUG = isChecked;
                        break;
                }
            }
        };
        ((Switch)findViewById(R.id.audio_gather_debug)).setOnCheckedChangeListener(switchListener);
        ((Switch)findViewById(R.id.audio_encode_debug)).setOnCheckedChangeListener(switchListener);
        ((Switch)findViewById(R.id.video_gather_debug)).setOnCheckedChangeListener(switchListener);
        ((Switch)findViewById(R.id.video_encode_debug)).setOnCheckedChangeListener(switchListener);
        ((Switch)findViewById(R.id.muxer_debug)).setOnCheckedChangeListener(switchListener);
        ((Switch)findViewById(R.id.transfer_debug)).setOnCheckedChangeListener(switchListener);
        ((Switch)findViewById(R.id.buffer_pool_debug)).setOnCheckedChangeListener(switchListener);
    }



}
