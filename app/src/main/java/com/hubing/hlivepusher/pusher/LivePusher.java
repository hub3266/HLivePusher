package com.hubing.hlivepusher.pusher;

import android.hardware.Camera;
import android.view.SurfaceHolder;

import com.hubing.hlivepusher.params.AudioParams;
import com.hubing.hlivepusher.params.VideoParams;

/**
 * Created by hubing on 2017/10/17.
 */

public class LivePusher {
    private AudioPusher audioPusher;
    private VideoPusher videoPusher;
    private SurfaceHolder surfaceHolder;

    public LivePusher(SurfaceHolder surfaceHolder) {
        this.surfaceHolder = surfaceHolder;
        init();
    }

    private void init() {
        audioPusher = new AudioPusher(new AudioParams(44100,1));
        videoPusher = new VideoPusher(new VideoParams(800,480, Camera.CameraInfo.CAMERA_FACING_BACK),surfaceHolder);
    }

    public void startPush(String url){
        audioPusher.startPusher();
        videoPusher.startPusher();
        PushNative.getInstance().startPusher(url);
    }

    public void stopPush(){
        audioPusher.stopPusher();
        videoPusher.stopPusher();
    }

    public void switchCamera(){
        videoPusher.switchCamera();
    }
}
