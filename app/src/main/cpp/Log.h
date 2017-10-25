//
// Created by lenovo on 2017/10/23.
//
#ifndef HLIVEPUSHER_LOG_H
#define HLIVEPUSHER_LOG_H
#include <android/log.h>
#define TAG "HLive_NDK"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG,__VA_ARGS__)
#endif //HLIVEPUSHER_LOG_H
