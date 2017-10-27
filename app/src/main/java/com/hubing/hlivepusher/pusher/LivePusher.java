package com.hubing.hlivepusher.pusher;

import android.app.Activity;
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

    public LivePusher(SurfaceHolder surfaceHolder, Activity activity) {
        this.surfaceHolder = surfaceHolder;
        init(activity);
    }

    private void init(Activity activity) {
        audioPusher = new AudioPusher(new AudioParams(44100,1));
        videoPusher = new VideoPusher(new VideoParams(800,480, Camera.CameraInfo.CAMERA_FACING_BACK),activity,surfaceHolder);
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
