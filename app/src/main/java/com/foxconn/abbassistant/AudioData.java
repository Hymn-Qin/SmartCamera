package com.foxconn.abbassistant;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by xiao-jie.qin@mail.foxconn.com on 2018/6/2.
 */

@SuppressLint("ParcelCreator")
public class AudioData implements Parcelable {

    byte[] pcm;

    public byte[] getPcm() {
        return pcm;
    }

    public void setPcm(byte[] pcm) {
        this.pcm = pcm;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(pcm.length);
        dest.writeByteArray(pcm);

    }

    public static final Creator<AudioData> CREATOR=new Creator<AudioData>() {

        @Override
        public AudioData createFromParcel(Parcel source) {
            AudioData audioData = new AudioData();

            byte[] _byte = new byte[source.readInt()];
            source.readByteArray(_byte);
            audioData.setPcm(_byte);
            return audioData;
        }

        @Override
        public AudioData[] newArray(int size) {
            return new AudioData[size];
        }
    };
}
