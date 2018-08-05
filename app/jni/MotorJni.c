//
// Created by H2601915 on 2018/5/14.
//

#include <termios.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <jni.h>
#include <unistd.h>
#include<malloc.h>

#include "MotorJni.h"

#include "android/log.h"
static const char *TAG="motor_jni";
#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)

#define MOTOR_HOR_DEV   "dev/motor0"
#define MOTOR_VER_DEV   "dev/motor1"
int FD_HOR,FD_VER;

JNIEXPORT jint JNICALL Java_com_ptz_motorControl_MotorManager_open
  (JNIEnv *env, jclass thiz)
{
    LOGE("MotorOpen()");

    /* Opening device */
    {
    //horizon motor
        FD_HOR = open(MOTOR_HOR_DEV, O_RDWR);
        LOGD("open() FD_HOR = %d", FD_HOR);
        if (FD_HOR == -1)
        {
            /* Throw an exception */
            LOGE("Cannot open port");
            /* TODO: throw an exception */
            return -1;
        }

        //vertical  motor
        FD_VER = open(MOTOR_VER_DEV, O_RDWR);
        LOGD("open() FD_VER = %d", FD_VER);
        if (FD_VER == -1)
        {
            /* Throw an exception */
            LOGE("Cannot open port");
            /* TODO: throw an exception */
            return -1;
        }
    }
    return 1;
}

JNIEXPORT jint JNICALL Java_com_ptz_motorControl_MotorManager_sendCmd
    (JNIEnv *env, jclass thiz, jint type, jint cmd, jint parameters)
{
    int ret;
    if(type == 0)
    {
    //horizon motor
    LOGD("horizon motor, cmd =%d,parmeter=%d",cmd,parameters);
        if(parameters == -1)
           ret = ioctl(FD_HOR, cmd);
        else{
           ret = ioctl(FD_HOR, cmd, &parameters);
           if(ret == 0)
                return parameters;
        }
    }else{
    //vertical  motor
    LOGD("vertical motor");
        if(parameters == -1)
            ret = ioctl(FD_VER, cmd);
        else{
            ret = ioctl(FD_VER, cmd, &parameters);
            if(ret == 0)
                return parameters;
        }
    }
    LOGD("motor return, cmd =%d,parmeter=%d",cmd,parameters);
    return ret;
}

JNIEXPORT void JNICALL Java_com_ptz_motorControl_MotorManager_close
  (JNIEnv *env, jobject thiz)
{
    close(FD_HOR);
    close(FD_VER);
}

