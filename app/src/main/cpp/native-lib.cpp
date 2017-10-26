#include <jni.h>
#include <string>
#include <x264.h>
#include <faac.h>
#include <string.h>
#include <pthread.h>
#include <malloc.h>
#include <vector>
#include "librtmp/rtmp.h"
#include "Log.h"


extern "C" {
/**
 * rtmp处理
 */
#define _RTMP_Free(_rtmp)  if(_rtmp) {RTMP_Free(_rtmp); _rtmp = NULL;}
#define _RTMP_Close(_rtmp)  if(_rtmp ) RTMP_Close(_rtmp);
#ifndef ULONG
typedef unsigned long ULONG;
#endif

int mWidth;
int mHeight;
int mBitrate;
int mfps;
int yLength, uvLength;
int publishing;
int readyRtmp;
//视频
RTMP *rtmp;
ULONG start_time;
char *rtmp_path;
x264_t *videoEncHandle = NULL;
x264_picture_t *pic_in = NULL;
x264_picture_t *pic_out = NULL;
//音频
faacEncHandle audioEncHandle;
ULONG nInputSamples;//采样位数
ULONG nMaxOutputBytes;//

//线程
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;
pthread_t publisher_tid;
std::vector<RTMPPacket *> mVector;

//jni


void add_264_header_data(unsigned char *pString, unsigned char *pps, int spslength,
                         int ppsLength);
void add_rtmp_packet(RTMPPacket *pPacket);
void add_264_body_data(uint8_t *buf, int length);
void add_aac_body_data(unsigned char *buffer, int len);
void add_aac_head_data();

JNIEXPORT void JNICALL
Java_com_hubing_hlivepusher_pusher_PushNative_setVideoOptions(JNIEnv *env, jobject instance,
                                                              jint width, jint height, jint bitrate,
                                                              jint fps) {
    LOGD("视频编码参数:%dx%d %dkbs %d", width, height, bitrate / 1000, fps);

    if (videoEncHandle) {
        LOGD("视频编码器已打开");
        if (mfps != fps || mBitrate != bitrate || mHeight != height
            || mWidth != width) {
            //属性不同
            x264_encoder_close(videoEncHandle);
            videoEncHandle = 0;
            free(pic_in);
            free(pic_out);
        } else {
            //属性相同
            return;
        }
    }
    x264_param_t param;
    mWidth = width;
    mHeight = height;
    mBitrate = bitrate;
    mfps = fps;
    yLength = mWidth * mHeight;
    uvLength = yLength / 4;
    x264_param_default_preset(&param, x264_preset_names[0], x264_tune_names[7]);
    param.i_level_idc = 51; //取值范围10-51。设置比特流的Level。默认40，即4.0。用来告诉解码器需要支持的什么级别的 兼容性。
    param.i_csp = X264_CSP_I420;//设置输入的视频采样的格式0x0001yuv 4:2:0 plana
    param.i_width = mWidth;
    param.i_height = mHeight;
    param.i_threads = 1; //将帧切分成块，由不同的线程进行 分别编码。0-4。0 for auto
    param.i_fps_num = fps;
    param.i_fps_den = 1;
    param.i_timebase_den = param.i_fps_den;
    param.i_timebase_num = param.i_fps_num;
    param.i_keyint_max = fps * 2;//关键帧的最大间隔帧数

    param.rc.i_rc_method = X264_RC_ABR;  //参数i_rc_method表示码率控制，CQP(恒定质量)，CRF(恒定码率)，ABR(平均码率)
    param.rc.i_bitrate = mBitrate / 1000;// 码率(比特率,单位Kbps)
    param.rc.i_vbv_max_bitrate = mBitrate / 1000 * 1.2;//最大码率
    param.rc.i_vbv_buffer_size = mBitrate / 1000; //设置了i_vbv_max_bitrate必须设置此参数，码率控制区大小,单位kbps
    param.b_vfr_input = 0;// VFR输入。1 ：时间基和时间戳用于码率控制  0 ：仅帧率用于码率控制
    param.b_repeat_headers = 1;          // 是否复制sps和pps放在每个关键帧的前面

    x264_param_apply_profile(&param, x264_profile_names[0]);

    videoEncHandle = x264_encoder_open(&param);
    if (!videoEncHandle) {
        LOGI("视频编码器打开失败");
        return;
    }
    LOGI("视频编码器打开完成");
    pic_in = (x264_picture_t *) malloc(sizeof(x264_picture_t));
    pic_out = (x264_picture_t *) malloc(sizeof(x264_picture_t));
    x264_picture_alloc(pic_in, X264_CSP_I420, mWidth, mHeight);

}

JNIEXPORT void JNICALL
Java_com_hubing_hlivepusher_pusher_PushNative_fireVideo(JNIEnv *env, jobject instance,
                                                        jbyteArray buffer_) {
    LOGI("fireVideo");
    if (!publishing || !readyRtmp || !videoEncHandle || !rtmp
        || !RTMP_IsConnected(rtmp)) {
        return;
    }

    jbyte *buffer = env->GetByteArrayElements(buffer_, NULL);
    uint8_t *y = pic_in->img.plane[0];
    uint8_t *u = pic_in->img.plane[1];
    uint8_t *v = pic_in->img.plane[2];
    /**
     * android拍照的数据格式nv21 转换成yuv420
     */
    memcpy(pic_in->img.plane[0], buffer, yLength);
    for (int i = 0; i < uvLength; i++) {
        // u[i] = buffer[yLength + i * 2 + 1];
        // v[i] = buffer[yLength + i * 2];
        *(u + i) = *(buffer + yLength + i * 2 + 1);
        *(v + i) = *(buffer + yLength + i * 2);
    }
    int nbNal = -1;
    x264_nal_t *nal = NULL;
    x264_picture_init(pic_out);
    if (x264_encoder_encode(videoEncHandle, &nal, &nbNal, pic_in, pic_out) < 0) {
        LOGI("编码失败");
        return;
    }
    LOGI("编码完成");
    pic_in->i_pts++;
    int spsLength, ppsLength;
    const int aa = 100;
    unsigned char sps[aa];
    unsigned char pps[aa];
    memset(sps, 0, aa);
    memset(pps, 0, aa);
    for (int i = 0; i < nbNal; i++) {
        if (nal[i].i_type == NAL_SPS) {
            //sps编码的第一帧 相对h264
            spsLength = nal[i].i_payload - 4;
            memcpy(sps, nal[i].p_payload + 4, spsLength);
        } else if (nal[i].i_type == NAL_PPS) {
            //sps编码的第二帧  相对h264
            ppsLength = nal[i].i_payload - 4;
            memcpy(pps, nal[i].p_payload + 4, ppsLength);
            add_264_header_data(sps, pps, spsLength, ppsLength);
        } else {
            add_264_body_data(nal[i].p_payload, nal[i].i_payload);
        }
    }

    env->ReleaseByteArrayElements(buffer_, buffer, 0);
}
void *puliser(void *args) {
    publishing = 1;
    do {
        rtmp = RTMP_Alloc();
        if (!rtmp) {
            LOGD("rtmp 初始化失败");
            goto END;
        }
        RTMP_Init(rtmp);
        //设置连接超时，单位秒，默认30秒
        rtmp->Link.timeout = 5;
        LOGI("RTMP_SetupURL RTMP is :%d path:%s", rtmp ? 1 : 0, rtmp_path);
        /*设置URL*/
        if (!RTMP_SetupURL(rtmp, rtmp_path)) {
            LOGD("RTMP_SetupURL() failed!");
            goto END;
        }
        /*设置可写,即发布流,这个函数必须在连接前使用,否则无效*/
        RTMP_EnableWrite(rtmp);
        /*连接服务器*/
        if (!RTMP_Connect(rtmp, NULL)) {
            LOGD("RTMP_Connect() failed!");
            goto END;
        }
        LOGI("RTMP_Connect success");
        /*连接流*/
        if (!RTMP_ConnectStream(rtmp, 0)) {
            LOGD("RTMP_ConnectStream() failed!");
            goto END;
        }
        readyRtmp = 1;
        add_aac_head_data();
        while (publishing) {
            pthread_mutex_lock(&mutex);
            if (mVector.size() <= 0) {
                LOGD("等待数据");
                pthread_cond_wait(&cond, &mutex);
            }
            if (!publishing) {
                goto END;
            }

            if (mVector.size() > 0) {
                for (std::vector<RTMPPacket *>::iterator it = mVector.begin();
                     it != mVector.end();) {
                    RTMPPacket *packet = *it;
                    packet->m_nInfoField2 = rtmp->m_stream_id;
                    int i = RTMP_SendPacket(rtmp, packet, 1);
                    LOGD("发送信息");
//                    mVector.erase(it);
//                    RTMPPacket_Free(packet);
//                    free(packet);
                    if (!i) {
                        LOGD("mVector  N");
                        RTMPPacket_Free(packet);
                        pthread_mutex_unlock(&mutex);
                        goto END;
                    } else {
                        LOGD("mVector  Y");
                        mVector.erase(it);
                        RTMPPacket_Free(packet);
                    }
                }
            }

            pthread_mutex_unlock(&mutex);
        }

        END:
        _RTMP_Close(rtmp);
        _RTMP_Free(rtmp);

    } while (0);
    publishing = 0;
    readyRtmp = 0;
    LOGI("推流结束");
    free(rtmp_path);
    rtmp_path = NULL;
    std::vector<RTMPPacket *>::iterator iter = mVector.begin();
    for (; iter != mVector.end();) {
        RTMPPacket *packet = *iter;
        if (packet) {
            RTMPPacket_Free(packet);
        }
        iter = mVector.erase(iter);
    }
    pthread_exit(NULL);
}
JNIEXPORT void JNICALL
Java_com_hubing_hlivepusher_pusher_PushNative_startPusher(JNIEnv *env, jobject instance,
                                                          jstring url_) {
    const char *url = env->GetStringUTFChars(url_, 0);
    rtmp_path = (char *) malloc(strlen(url) + 1);
    memset(rtmp_path, 0, strlen(url) + 1);
    memcpy(rtmp_path, url, strlen(url));
    start_time = RTMP_GetTime();
    pthread_create(&publisher_tid, NULL, puliser, NULL);
    env->ReleaseStringUTFChars(url_, url);

}
void add_264_header_data(unsigned char *sps, unsigned char *pps, int spslength, int ppsLength) {
    int size = 16 + spslength + ppsLength;
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, size);
    RTMPPacket_Reset(packet);
    char *body = packet->m_body;
    int i = 0;
    body[i++] = 0x17;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;
    body[i++] = 0x00;

    body[i++] = 0x01; //版本
    body[i++] = sps[1];
    body[i++] = sps[2];
    body[i++] = sps[3];
    body[i++] = 0xff;


    body[i++] = 0xe1; //sps的个数 通常为0xe1；
    body[i++] = (spslength >> 8) & 0xff;  //sps长度
    body[i++] = (spslength) & 0xff;
    memcpy(&body[i], sps, spslength); //sps内容
    i += spslength;

    body[i++] = 0x01;//pps的个数
    body[i++] = (ppsLength >> 8) & 0xff;  //pps长度
    body[i++] = (ppsLength) & 0xff;
    memcpy(&body[i], pps, ppsLength); //pps内容
    i += ppsLength;

    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nBodySize = size;
    packet->m_nChannel = 0x04;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    //packet->m_headerType = RTMP_PACKET_SIZE_MINIMUM;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    add_rtmp_packet(packet);


}
void add_264_body_data(uint8_t *buf, int length) {
    /*去掉帧界定符 *00 00 00 01*/
    if (buf[2] == 0x00) {
        //如果第3位是ox00  界定符 *00 00 00 01
        buf += 4;
        length -= 4;
    } else if (buf[2] == 0x01) {
        //如果第3位是ox01  界定符 * 00 00 01
        buf += 3;
        length -= 3;
    }
    int size = 9 + length;

    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, size);
    RTMPPacket_Reset(packet);
    char *body = packet->m_body;
    //关键帧与非关键帧  65 关键帧    67  sps      68 pps
    int type = buf[0] & 0x1f;
    body[0] = 0x27;
    if (type == NAL_SLICE_IDR) {
        body[0] = 0x17;
    }
    body[1] = 0x01;
    body[2] = 0x00;
    body[3] = 0x00;
    body[4] = 0x00;
    body[5] = (length >> 24) & 0xff;
    body[6] = (length >> 16) & 0xff;
    body[7] = (length >> 8) & 0xff;
    body[8] = (length) & 0xff;
    memcpy(&body[9], buf, length);

    packet->m_hasAbsTimestamp = 0;
    packet->m_nBodySize = size;
    packet->m_packetType = RTMP_PACKET_TYPE_VIDEO;
    packet->m_nChannel = 0x04;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    packet->m_nTimeStamp = RTMP_GetTime() - start_time;

    add_rtmp_packet(packet);
}

void add_rtmp_packet(RTMPPacket *pPacket) {
    pthread_mutex_lock(&mutex);
    if (publishing && readyRtmp) {
        mVector.push_back(pPacket);
    }
    pthread_cond_signal(&cond);
    pthread_mutex_unlock(&mutex);

}

/************************音频****************************/
JNIEXPORT void JNICALL
Java_com_hubing_hlivepusher_pusher_PushNative_setAudipOptions(JNIEnv *env, jobject instance,
                                                              jint sampleRate, jint channel) {
    if (audioEncHandle) {
        return;
    }
    audioEncHandle = faacEncOpen(sampleRate, channel, &nInputSamples, &nMaxOutputBytes);
    if (!audioEncHandle) {
        LOGI("音频编码器打开失败！");
        return;
    }
    faacEncConfigurationPtr configuration = faacEncGetCurrentConfiguration(audioEncHandle);
    configuration->mpegVersion = MPEG4;//MPEG version, 2 or 4 - MPEG版本，MPEG2 or MPEG4
    configuration->allowMidside = 1;//Allow mid/side coding - 是否允许mid/side coding 取值：0-NO 1-YES
    configuration->aacObjectType = LOW;     /* AAC object type - AAC对象类型，详细见补充说明1，取值:1-MAIN 2-LOW 3-SSR 4-LTP*/
    configuration->outputFormat = 0;//输出是否包含ADTS头
    configuration->useTns = 1; //时域噪音控制,大概就是消爆音Use Temporal Noise Shaping - 瞬时噪声定形(temporal noise shaping，TNS)滤波器,取值：0-NO 1-YES

    /* Use one of the channels as LFE channel - 是否允许一个通道为低频通道，取值：0-NO 1-YES*/
    /* LFE(low-frequencyeffects) */
    configuration->useLfe = 0;

    /**
      *PCM Sample Input Format  - 输入pcm数据类型
      *0 FAAC_INPUT_NULL    invalid, signifies a misconfigured config
      *1 FAAC_INPUT_16BIT native endian 16bit
      *2 FAAC_INPUT_24BIT native endian 24bit in 24 bits(not implemented)
      *3 FAAC_INPUT_32BIT native endian 24bit in 32 bits (DEFAULT)
      *f4 FAAC_INPUT_FLOAT 32bit floating point
    */
    configuration->inputFormat = FAAC_INPUT_16BIT;

    /* 默认100，值越大音质越高 */
    configuration->quantqual = 100;
    /* AAC file frequency bandwidth - 频宽 取值：0， 32000，64000都可以*/
    configuration->bandWidth = 0;

    /* block type enforcing -
 * (SHORTCTL_NORMAL/SHORTCTL_NOSHORT/SHORTCTL_NOLONG)
 */
    configuration->shortctl = SHORTCTL_NORMAL;
    if (!faacEncSetConfiguration(audioEncHandle, configuration)) {
        LOGI("音频配置失败");
    };
    LOGI("音频编码器打开成功！");
}

JNIEXPORT void JNICALL
Java_com_hubing_hlivepusher_pusher_PushNative_fireAudio(JNIEnv *env, jobject instance,
                                                        jbyteArray buff_,jint length) {
    jbyte *buff = env->GetByteArrayElements(buff_, NULL);
    //int publishing;
    //int readyRtmp;
    if(!publishing||!readyRtmp || !audioEncHandle){
        LOGI("还没开始推流");
        return;
    }
    unsigned char* outputBuffer = (unsigned char *) malloc(nMaxOutputBytes * sizeof(unsigned char));
    int len = faacEncEncode(audioEncHandle, (int32_t *) buff, nInputSamples, outputBuffer, nMaxOutputBytes);
    if(len>0){
        add_aac_body_data(outputBuffer,len);
    }
    if(outputBuffer) {
        free(outputBuffer);
    }
    env->ReleaseByteArrayElements(buff_, buff, 0);
}

/**
 * 解码数据
 */
void add_aac_head_data() {
    if (!audioEncHandle) {
        return;
    }
    unsigned char *buf;
    ULONG len;/*buf长度,一般是2*/
    faacEncGetDecoderSpecificInfo(audioEncHandle, &buf, &len);
    RTMPPacket *packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet, len + 2);
    RTMPPacket_Reset(packet);
    unsigned char *body = (unsigned char *) packet->m_body;
    /*AF 00 + AAC RAW data*/
    body[0] = 0xAF;
    body[1] = 0x00;
    memcpy(&body[2], buf, len); /*spec_buf是AAC sequence header数据*/
    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = len + 2;
    packet->m_nChannel = 0x04;
    packet->m_hasAbsTimestamp = 0;
    packet->m_nTimeStamp = 0;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    add_rtmp_packet(packet);
    free(buf);
}

void add_aac_body_data(unsigned char *buffer, int len) {

    RTMPPacket* packet = (RTMPPacket *) malloc(sizeof(RTMPPacket));
    RTMPPacket_Alloc(packet,len+2);
    char * body = packet->m_body;
    body[0] = 0xaf;
    body[1] = 0x01;
    memcpy(body+2,buffer,len);
    packet->m_packetType = RTMP_PACKET_TYPE_AUDIO;
    packet->m_nBodySize = len+2;
    packet->m_nChannel = 0x04;
    packet->m_hasAbsTimestamp = 0;
    packet->m_headerType = RTMP_PACKET_SIZE_MEDIUM;
    packet->m_nTimeStamp = RTMP_GetTime() - start_time;
    add_rtmp_packet(packet);
}
}