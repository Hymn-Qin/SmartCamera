package com.zzdc.abb.smartcamera.controller;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SensorListener;
import android.util.Log;


public class NightModeControl implements SensorEventListener {
    private static final String TAG = NightModeControl.class.getSimpleName();
    private SensorManager sm = null;
    private Sensor lightSensor;
    public float Current_Light_value;


    public NightModeControl(Context ctx)
    {
        sm = (SensorManager)ctx.getSystemService(ctx.SENSOR_SERVICE);

        lightSensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        Log.d(TAG, "lightSensor =" + lightSensor);
        if (lightSensor != null) {
            //   boolean rc = sm.registerListener(this, Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL);
            sm.registerListener((SensorEventListener) this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
            //   Log.d("keming10", "registerListener =" + rc);
        }

    }
    public void unrigsterListener(){
        sm.unregisterListener((SensorEventListener) this, lightSensor);

    }

    public void onSensorChanged(SensorEvent event)
    {
        synchronized (this) {
            switch (event.sensor.getType()){

                case Sensor.TYPE_LIGHT:
//                    View5.setText("光线：" + str);
                    Current_Light_value = event.values[0];
//                   Log.d("keming","light value ="+event.values[0]);
                    break;
                case Sensor.TYPE_PRESSURE:
//                    View6.setText("压力：" + str);
                    break;

                default:
//                    View12.setText("NORMAL：" + str);
                    break;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
