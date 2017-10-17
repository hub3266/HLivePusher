#include <jni.h>
#include <string>
#include <x264.h>
#include <faac.h>
#include "librtmp/rtmp.h"

extern "C"
JNIEXPORT jstring JNICALL
Java_com_hubing_hlivepusher_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    x264_picture_t *pic;
    x264_picture_init(pic);
    faacEncGetVersion(NULL,NULL);
    RTMP_Alloc();
    return env->NewStringUTF(hello.c_str());
}
