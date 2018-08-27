package com.zzdc.abb.smartcamera.controller;
/**
 * camera 1 api
 */

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.ParseException;
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
import com.zzdc.abb.smartcamera.FaceFeature.ContrastManager;
import com.zzdc.abb.smartcamera.FaceFeature.FaceConfig;
import com.zzdc.abb.smartcamera.FaceFeature.OnContrastListener;
import com.zzdc.abb.smartcamera.FaceFeature.PictureProductManager;
import com.zzdc.abb.smartcamera.FaceFeature.Utils;
import com.zzdc.abb.smartcamera.R;
import com.zzdc.abb.smartcamera.TutkBussiness.TutkManager;
import com.zzdc.abb.smartcamera.common.ApplicationSetting;
import com.zzdc.abb.smartcamera.common.Constant;
import com.zzdc.abb.smartcamera.info.Device;
import com.zzdc.abb.smartcamera.util.LogTool;
import com.zzdc.abb.smartcamera.util.TUTKUIDUtil;
import com.zzdc.abb.smartcamera.util.WindowUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    private Button btnStart;
    private Button btnCall;
    private SurfacePreview mSurfacePreview;
    private static boolean RecordRuning = false;
    private ApplicationSetting mAplicationSetting = null;
    private boolean mIsCmdComeFromOpenHotSpot = false;
    private TutkManager mTutk;
    private AvMediaRecorder mAvMediaRecorder = null;

    private OneKeyCallReciever mOneKeyCallReciever;
    private static final String ACTION_OPEN_HOT_SPOT = "com.foxconn.zzdc.broadcast.OPEN_HOT_SPOT";
    private static final String ACTION_STOP_CAMERA = "com.foxconn.zzdc.broadcast.STOP_CAMERA";
    private static final String ACTION_START_CAMERA = "com.foxconn.zzdc.broadcast.START_CAMERA";
    private static final String ACTION_CAMERA_STATUS = "com.foxconn.zzdc.broadcast.camera";
    private static final String ACTION_CAMERA_ALERT = "com.foxconn.alert.camera.play";
    private boolean mIsEnableMonitor;
    private static Context mContext;

    public static final int CallStateOff = 0;
    public static final int CallStateOn = 1;
    public static int CallState = CallStateOff;

    private AudioManager mAudioManager;

    public static final String DEVICE_INFO_FILE = android.os.Environment
            .getExternalStorageDirectory().getAbsolutePath() + File.separator + "device.json";
    final MotorManager mMotorManager = MotorManager.getManger();

    @SuppressLint("HandlerLeak")
    private Handler mControllerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Constant.START_RECORD:
                    startRecordFirstTime();
                    break;
                case Constant.INIT_TUTK_UID:
                    getTUTKUID();
                    break;
                default:
                    break;
            }
        }
    };

    public static Context getContext() {
        return mContext;
    }

    public class OneKeyCallReciever extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: This method is called when the BroadcastReceiver is receiving
            Log.e(TAG, "MainActivity onReceive " + intent.getAction());
            if (intent.getAction().equalsIgnoreCase("com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED")) {
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
            } else if (intent.getAction().equalsIgnoreCase(ACTION_OPEN_HOT_SPOT)) {
                Log.d(TAG, "Somebody wants to reset WIFI !!!");
                finish();
                mIsCmdComeFromOpenHotSpot = true;
            } else if (intent.getAction().equalsIgnoreCase(ACTION_START_CAMERA)) {
                Log.d(TAG, "Start camera after reset WIFI !!!");
                if (mAplicationSetting.getSystemMonitorOKSetting()) {
                    Log.d(TAG, "Now open camera from broadcast !!!");
                    mAvMediaRecorder.init();
                    mAvMediaRecorder.avMediaRecorderStart();
                    RecordRuning = true;
                }
            }else if (intent.getAction().equalsIgnoreCase(ACTION_CAMERA_ALERT)) {
                String type = intent.getStringExtra("type");
                String message = intent.getStringExtra("message");
                Log.d(TAG, "get receive -- type = " + type + " message = " + message);
                if (type.equals("ALERT")) {
                    if (mAplicationSetting.getSystemMonitorOKSetting()) {
                        mAvMediaRecorder.startAlertRecord();
                    }
                } else if (type.equals("Contrast")) {
                    if (message.equals("true")) {
                        if (!mAplicationSetting.getSystemMonitorOKSetting()) {
                            mAplicationSetting.setSystemContrastSetting(true);
                            startContrast();
                        }
                    }else if (message.equals("false")) {
                        if (mAplicationSetting.getSystemMonitorOKSetting()) {
                            mAplicationSetting.setSystemContrastSetting(false);
                            stopContrast();
                        }
                    }

                }else if (type.equals("Camera")) {
                    if (mAvMediaRecorder == null) {
                        mAvMediaRecorder = AvMediaRecorder.getInstance();
                    }
                    if (message.equals("false")) {
                        if (mAplicationSetting.getSystemMonitorOKSetting()) {
//                            mAplicationSetting.setSystemMonitorOKSetting(false);
                            if (RecordRuning) {
                                StopRecord();
                            }
                        }

                    } else if (message.equals("true")) {

                        if (!mAplicationSetting.getSystemMonitorOKSetting()) {
//                            mAplicationSetting.setSystemMonitorOKSetting(true);
                            if (!RecordRuning) {
                                StartRecord();
                            }
                        }
                    }
                    setCameraStatusToServer(mAplicationSetting.getSystemMonitorOKSetting());
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    WindowUtils.showPopupWindow(this);
                    initUI();
                    mMotorManager.initMotor();
                    btnStart.setEnabled(true);
                    startMonitorIfNeeded();
                } else {
                    Toast.makeText(this, "ACTION_MANAGE_OVERLAY_PERMISSION权限已被拒绝", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        RecordRuning = false;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main2);
        initDebug();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
        filter.addAction(ACTION_CAMERA_ALERT);
        filter.addAction(ACTION_OPEN_HOT_SPOT);
        filter.addAction(ACTION_START_CAMERA);
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
        mIsEnableMonitor = mAplicationSetting.getSystemMonitorOKSetting();
        MotorCmd.DEVICE_POSE = mAplicationSetting.getDevicePose();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(getApplicationContext())) {
                //启动Activity让用户授权
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 100);
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
        btnStart.setText(mAplicationSetting.getSystemMonitorOKSetting() ? "停止" : "开始");
        btnStart.setOnClickListener(this);

        btnCall = findViewById(R.id.VoiceCall);
        btnCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "CallState=" + CallState + ",mode=" + mAudioManager.getMode());
                if (CallState == CallStateOff) {
                    CallState = CallStateOn;
                    if (mTutk != null) {
                        mTutk.StartCall();
                        Log.d(TAG, "com.foxconn.zzdc.broadcast.VOLUME_UP_PRESSED");
                    }
                } else if (CallState == CallStateOn) {
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
        if (mAplicationSetting.getSystemMonitorOKSetting()) {
            Log.d(TAG, "from SharedPreferences start monitor");
            mControllerHandler.sendEmptyMessageDelayed(Constant.START_RECORD, 6000);
        } else {
            Log.d(TAG, "from SharedPreferences do not start monitor");
        }
    }

    private void setCameraStatusToServer(boolean value) {
        Intent intent = new Intent();
        intent.setAction(ACTION_CAMERA_STATUS);
        intent.putExtra("type","Camera");
        if(value) {
            intent.putExtra("result", "true");
        } else {
            intent.putExtra("result", "false");
        }
        sendBroadcast(intent);
        LogTool.d(TAG,"(setCameraStatusToServer)Set camera status to : "+value);
    }

    private  void startRecordFirstTime() {
        Log.d(TAG,"start record first time in MainActivity.");
        if (mAplicationSetting.getSystemMonitorOKSetting()) {
            mAvMediaRecorder.init();
            mAvMediaRecorder.avMediaRecorderStart();
            RecordRuning = true;
        }
//        Utils.startGetFeatureFromPath();
        startContrast();
    }

    public void StartRecord() {
        if (!mAplicationSetting.getSystemMonitorOKSetting()) {
            mAvMediaRecorder.init();
            mAvMediaRecorder.avMediaRecorderStart();
            RecordRuning = true;
            mAplicationSetting.setSystemMonitorOKSetting(true);
            setCameraStatusToServer(true);
        }
    }

    public void StopRecord() {
        Log.d(TAG,"RecordRuning = "+RecordRuning);
        if (mAplicationSetting.getSystemMonitorOKSetting()) {
            mAvMediaRecorder.avMediaRecorderStop();
            RecordRuning = false;
            mAplicationSetting.setSystemMonitorOKSetting(false);
            setCameraStatusToServer(false);
        }
    }

    public void startContrast() {
        if (mAplicationSetting.getSystemContrastSetting()) {
            LogTool.d("qxj","startContrast()" + mAplicationSetting.getSystemContrastSetting());
            ContrastManager contrastManager = ContrastManager.getInstance();
            contrastManager.setContrastKey(FaceConfig.faceAPP_Id, FaceConfig.faceFD_Key, FaceConfig.faceFR_KEY, FaceConfig.faceFT_KEY)
                    .setContrastConfig(1920, 1080, FaceConfig.scale, FaceConfig.scaleFT, FaceConfig.maxFacesNUM, FaceConfig.maxContrastFacesNUM)
                    .setSwitchContrast(true)
                    .setFamilyFace(MediaStorageManager.getInstance().getFaceDatas())
                    .setFamilyFocusFace(MediaStorageManager.getInstance().getFocus())
                    .startContrast();
            VideoGather.getInstance().registerVideoRawDataListener(contrastManager);
            contrastManager.onContrasManager(new OnContrastListener() {
                @Override
                public void onContrastRectList(List<Rect> rectList) {
                    Log.d("qxj", "--------get face rects--------- >> " + rectList.size());
                }

                @Override
                public void onContrastSucceed(boolean focus, String identity) {

                }

                @Override
                public void onContrastFailed(String msg, byte[] stranger) {

                }

                @Override
                public void onContrastError(String message) {

                }
            });
        }

        if (mAplicationSetting.getSystemFocusSetting()) {
            startCreatePicture();
        }
    }

    public void stopContrast() {
        if (!mAplicationSetting.getSystemContrastSetting()) {
            ContrastManager contrastManager = ContrastManager.getInstance();
            contrastManager.setSwitchContrast(false);
        }
        if (!mAplicationSetting.getSystemFocusSetting()) {
            stopPictureCreate();
        }
    }

    private ScheduledExecutorService scheduler = null;

    private void startCreatePicture() {
        try {
            scheduler = Executors.newScheduledThreadPool(1);
            synchronized (scheduler) {
                scheduler.scheduleAtFixedRate(TodoOperation, 10 * 1000, 30 * 60 * 1000, TimeUnit.MILLISECONDS);//周期
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private Semaphore semaphore = new Semaphore(1);
    private final Runnable TodoOperation = new Runnable() {
        public void run() {
            try {
                semaphore.acquire();
                String imageFile = MediaStorageManager.getInstance().generateFocusImageFileName();
                PictureProductManager productManager = PictureProductManager.getInstance();
                VideoGather.getInstance().registerVideoRawDataListener(productManager);
                productManager.startCreatePicture(imageFile, true);
                VideoGather.getInstance().unregisterVideoRawDataListener(productManager);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                semaphore.release();
            }

        }
    };

    private void stopPictureCreate() {
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
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

        if (mIsCmdComeFromOpenHotSpot) {
            Intent intend = new Intent();
            intend.setAction(ACTION_STOP_CAMERA);
            sendBroadcast(intend);
            mIsCmdComeFromOpenHotSpot = false;
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start_muxer:
                //   startRecord();
                Log.d(TAG, "record clicked");
                if (!RecordRuning) {
                    StartRecord();
                    Log.d(TAG, "start record ");
                } else {
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
    }

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
