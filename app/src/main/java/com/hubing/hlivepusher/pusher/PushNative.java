package com.hubing.hlivepusher.pusher;

import android.util.Log;

/**
 * Created by hubing on 2017/10/23.
 */

public class PushNative {


    static {
        System.loadLibrary("native-lib");
    }
    private static PushNative pushNative;

    private PushNative(){

    }
    public static PushNative getInstance() {
        if(pushNative == null){
            pushNative = new PushNative();
        }
        return pushNative;
    }

    public native void setVideoOptions(int width, int height, int bitrate,
                                       int fps);
    public native void fireVideo(byte[] buffer);
    public native void startPusher(String url);
    public native void setAudipOptions(int sampleRate, int channel);
    public native void fireAudio(byte[] buff,int length);



    public void onPostNativeError(int code) {
        Log.d("PusherNative", code + "");
//        if (null != mListener) {
//            mListener.onErrorPusher(code);
//        }
    }
}
