// AssistantAudioInterface.aidl
package com.foxconn.abbassistant;

import com.foxconn.abbassistant.AudioData;
// Declare any non-default types here with import statements

interface AssistantAudioInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */

//    void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat,
//            double aDouble, String aString);

oneway void getByte(in AudioData audioData);
void getAudioMode(int type);
void setMessage(in String message);


}
