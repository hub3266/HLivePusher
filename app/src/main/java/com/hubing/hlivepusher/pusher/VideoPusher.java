package com.hubing.hlivepusher.pusher;


import android.app.Application;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.hubing.hlivepusher.params.VideoParams;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by hubing on 2017/10/17.
 */

public class VideoPusher extends Pusher implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private String TAG = "VideoPusher";

    private VideoParams params;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private byte[] buffers;

    public VideoPusher(VideoParams params, SurfaceHolder surfaceHolder) {
        this.params = params;
        this.surfaceHolder = surfaceHolder;
        surfaceHolder.addCallback(this);

    }

    @Override
    public void startPusher() {
        startPreview();
        pushing = true;

    }

    @Override
    public void stopPusher() {

    }

    @Override
    public void release() {

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        startPreview();

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }


    private void startPreview() {

        try {
            camera = Camera.open(params.getCameraId());
            Camera.Parameters paramters = camera.getParameters();

//            List<Camera.Size> pictureSizes = paramters.getSupportedPictureSizes();
//            int length = pictureSizes.size();
//            for (int i = 0; i < length; i++) {
//                Log.i("camera","SupportedPictureSizes : " + pictureSizes.get(i).width + "x" + pictureSizes.get(i).height);
//            }

            paramters.setPictureFormat(ImageFormat.NV21);
           // paramters.setPreviewSize(params.getWidth(), params.getHeight());
            setPreviewSize(paramters);
            PushNative.getInstance().setVideoOptions(params.getWidth(),params.getHeight(),params.getBitrate(),params.getFps());
            camera.setParameters(paramters);
            camera.setPreviewDisplay(surfaceHolder);
            //获取预览图像数据
            int bitPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
            //videoParam.getWidth() * videoParam.getHeight()*bitPerPixel/8  数据大
            buffers = new byte[params.getWidth() * params.getHeight() * bitPerPixel / 8];
            camera.addCallbackBuffer(buffers);
            camera.setPreviewCallbackWithBuffer(this);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    private void stopPreview() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    public void switchCamera() {
        if (params.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
            params.setCameraId(Camera.CameraInfo.CAMERA_FACING_FRONT);
        } else {
            params.setCameraId(Camera.CameraInfo.CAMERA_FACING_BACK);
        }
//        int w = params.getWidth();
//        params.setWidth(params.getHeight());
//        params.setHeight(w);
        // 重新加载
        stopPreview();
        startPreview();
    }

    /**
     * camera 数据  回调
     *
     * @param bytes
     * @param camera
     */
    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (camera != null) {
            //不这写的话 只会调用一次
            camera.addCallbackBuffer(bytes);
            if(pushing){
                PushNative.getInstance().fireVideo(bytes);
            }
        }
    }


    private void setPreviewSize(Camera.Parameters parameters) {
        List<Integer> supportedPreviewFormats = parameters.getSupportedPreviewFormats();
        for (Integer integer : supportedPreviewFormats) {
            System.out.println("支持:" + integer);
        }
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size size = supportedPreviewSizes.get(0);
        Log.d(TAG, "支持 " + size.width + "x" + size.height);
        int m = Math.abs(size.height * size.width - params.getHeight() * params.getWidth());
        supportedPreviewSizes.remove(0);
        Iterator<Camera.Size> iterator = supportedPreviewSizes.iterator();
        while (iterator.hasNext()) {
            Camera.Size next = iterator.next();
            Log.d(TAG, "支持 " + next.width + "x" + next.height);
            int n = Math.abs(next.height * next.width - params.getHeight() * params.getWidth());
            if (n < m) {
                m = n;
                size = next;
            }
        }
        params.setHeight(size.height);
        params.setWidth(size.width);
        parameters.setPreviewSize(params.getWidth(), params.getHeight());
        Log.d(TAG, "预览分辨率 width:" + size.width + " height:" + size.height);
    }
}
