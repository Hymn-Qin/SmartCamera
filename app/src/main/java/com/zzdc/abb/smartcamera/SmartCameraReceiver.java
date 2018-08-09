package com.zzdc.abb.smartcamera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.zzdc.abb.smartcamera.controller.MainActivity;
import com.zzdc.abb.smartcamera.service.VideosService;
import com.zzdc.abb.smartcamera.util.LogTool;

public class SmartCameraReceiver extends BroadcastReceiver {

    private static final String  TAG = SmartCameraReceiver.class.getSimpleName();
    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        Log.e(TAG, "onReceive " + intent.getAction());
        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_EJECT)){
            Log.d(TAG,"Intent.ACTION_MEDIA_EJECT");
        } else if (intent.getAction().equalsIgnoreCase(Intent.ACTION_MEDIA_MOUNTED)){
            Log.d(TAG,"Intent.ACTION_MEDIA_MOUNTED");
			Intent startServiceIntent = new Intent(context, VideosService.class);
            context.startService(startServiceIntent);
        } else if (intent.getAction().equals("com.foxconn.zzdc.broadcast.OPEN_HOT_SPOT")) {
            Log.d(TAG,"camera receiver"+intent.getAction());
            if(MainActivity.mainActivity!=null){
                MainActivity.mainActivity.finish();
            }
        }
    }
}
