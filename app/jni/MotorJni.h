//
// Created by H2601915 on 2018/5/14.
//
#include <jni.h>
/* Header for class android_motorjni_api_MotorJni */

#ifndef _Included_motorControl_MotorManager
#define _Included_motorControl_MotorManager
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_ptz_motorControl_MotorManager_open
  (JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_com_ptz_motorControl_MotorManager_sendCmd
    (JNIEnv *, jclass, jint, jint, jint);

JNIEXPORT void JNICALL Java_com_ptz_motorControl_MotorManager_close
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif
