package com.zzdc.abb.smartcamera.util;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

public class TUTKUIDUtil {
    private static final String TAG = TUTKUIDUtil.class.getSimpleName();
    private static final String TUTK_UID_FILE = "/proc/tutkuuid";
    public static String getTUTKUID(){
        String tmpUID = null;


        File fl = new File(TUTK_UID_FILE);
        FileInputStream tmpFIS = null;
        try {
            tmpFIS = new FileInputStream(fl);
            tmpUID = getStringFromStream(tmpFIS);
            tmpFIS.close();
        } catch (Exception e) {
            Log.d(TAG,"getTUTKUID Exception " + e.getMessage());
            e.printStackTrace();
        }

        if(TextUtils.isEmpty(tmpUID)){
            tmpUID = "02:00:00:00:00:00";
        }
        if (tmpUID.contains("\n")){
            tmpUID = tmpUID.replaceAll("\n","");
        }
        return tmpUID.toUpperCase();
    }

    private static String getStringFromStream(InputStream crunchifyStream) throws IOException {
        if (crunchifyStream != null) {
            Writer crunchifyWriter = new StringWriter();

            char[] crunchifyBuffer = new char[2048];
            try {
                Reader crunchifyReader = new BufferedReader(new InputStreamReader(crunchifyStream, "UTF-8"));
                int counter;
                while ((counter = crunchifyReader.read(crunchifyBuffer)) != -1) {
                    crunchifyWriter.write(crunchifyBuffer, 0, counter);
                }
            } finally {
                crunchifyStream.close();
            }
            return crunchifyWriter.toString();
        } else {
            return "No Contents";
        }
    }
}
