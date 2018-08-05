#
# Created by H2601915 on 2018/5/14.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

TARGET_PLATFORM := android-3
LOCAL_MODULE    := motor_jni
LOCAL_SRC_FILES := MotorJni.c
LOCAL_LDLIBS    := -llog
LOCAL_LDFLAGS += -fPIC

include $(BUILD_SHARED_LIBRARY)
