package com.zzdc.abb.smartcamera.util;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class WriteToFileTool {

    private static final String TAG = WriteToFileTool.class.getSimpleName();
    BufferedOutputStream mOutputStream;

    public void prepare() {

        String dir = android.os.Environment
                .getExternalStorageDirectory().getAbsolutePath() + File.separator;
        String fileName = dir + "test.nv21";
        File tmpFile = new File(fileName);
        LogTool.d(TAG, "tmpFile " + tmpFile.toString());
        if (!tmpFile.exists()) {
            try {
                tmpFile.createNewFile();
                LogTool.d(TAG, "create a file ");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                LogTool.d(TAG, "createNewFile Exception " + e.toString());

            }

        } else {
            if (tmpFile.delete()) {
                try {
                    tmpFile.createNewFile();
                    LogTool.d(TAG, "delete and create a file");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    LogTool.d(TAG, "createNewFile Exception " + e.toString());

                }
            }
        }
        try {
            mOutputStream = new BufferedOutputStream(new FileOutputStream(tmpFile));
            LogTool.d("Encoder", "outputStream initialized");
        } catch (Exception e){
            LogTool.d(TAG,"BufferedOutputStream Exception " + e.toString());

        }
    }

    public void write(byte[] data){
        try{
            mOutputStream.write(data, 0, data.length);
        }catch (Exception e){
            LogTool.d(TAG,"write Exception " + e.toString());
        }
    }

    public void close(){
        try{
            mOutputStream.flush();
            mOutputStream.close();
        }catch (Exception e){
            Log.d(TAG,"close Exception " + e.toString());
        }finally {

        }
    }
}
