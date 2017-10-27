package com.hubing.hlivepusher.pusher;


import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
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

    private Activity activity;
    private int rotation;
    private byte[] raw;

    public VideoPusher(VideoParams params, Activity activity, SurfaceHolder surfaceHolder) {
        this.params = params;
        this.surfaceHolder = surfaceHolder;
        surfaceHolder.addCallback(this);
        this.activity = activity;

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
            paramters.setPictureFormat(ImageFormat.NV21);
            setPreviewSize(paramters);
            setPreviewOrientation(paramters);
            camera.setParameters(paramters);
            camera.setPreviewDisplay(surfaceHolder);
            //获取预览图像数据
            int bitPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
            //videoParam.getWidth() * videoParam.getHeight()*bitPerPixel/8  数据大
            buffers = new byte[params.getWidth() * params.getHeight() * bitPerPixel / 8];
            raw = new byte[params.getWidth() * params.getHeight() * bitPerPixel / 8];
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
            if (pushing) {
                switch (rotation) {
                    case Surface.ROTATION_0:
                        portraitData2Raw(bytes,raw);
                        break;
                    case Surface.ROTATION_90:
                        raw = bytes;
                        break;
                    case Surface.ROTATION_270:
                        landscapeData2Raw(bytes,raw);
                        break;
                }
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

    private void setPreviewOrientation(Camera.Parameters paramters) {
        rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                PushNative.getInstance().setVideoOptions(params.getHeight(), params.getWidth(), params.getBitrate(), params.getFps());
                break;
            case Surface.ROTATION_90:
                PushNative.getInstance().setVideoOptions(params.getWidth(), params.getHeight(), params.getBitrate(), params.getFps());
                break;
            case Surface.ROTATION_180:
                PushNative.getInstance().setVideoOptions(params.getHeight(), params.getWidth(), params.getBitrate(), params.getFps());
                break;
            case Surface.ROTATION_270:
                PushNative.getInstance().setVideoOptions(params.getWidth(), params.getHeight(), params.getBitrate(), params.getFps());
                break;
        }
        int result;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(params.getCameraId(), cameraInfo);
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + rotation) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cameraInfo.orientation - rotation + 360) % 360;
        }
        camera.setDisplayOrientation(result);

    }


    private void landscapeData2Raw(byte[] data,byte[] raw) {
        int width = params.getWidth(), height = params.getHeight();
        int y_len = width * height;
        int k = 0;
        // y数据倒叙插入raw中
        for (int i = y_len - 1; i > -1; i--) {
            raw[k] = data[i];
            k++;
        }
        // System.arraycopy(data, y_len, raw, y_len, uv_len);
        // v1 u1 v2 u2
        // v3 u3 v4 u4
        // 需要转换为:
        // v4 u4 v3 u3
        // v2 u2 v1 u1
        int maxpos = data.length - 1;
        int uv_len = y_len >> 2; // 4:1:1
        for (int i = 0; i < uv_len; i++) {
            int pos = i << 1;
            raw[y_len + i * 2] = data[maxpos - pos - 1];
            raw[y_len + i * 2 + 1] = data[maxpos - pos];
        }
    }

    private void portraitData2Raw(byte[] data,byte[] raw) {
        // if (mContext.getResources().getConfiguration().orientation !=
        // Configuration.ORIENTATION_PORTRAIT) {
        // raw = data;
        // return;
        // }
        int width = params.getWidth(), height = params.getHeight();
        int y_len = width * height;
        int uvHeight = height >> 1; // uv数据高为y数据高的一半
        int k = 0;
        if (params.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK) {
            for (int j = 0; j < width; j++) {
                for (int i = height - 1; i >= 0; i--) {
                    raw[k++] = data[width * i + j];
                }
            }
            for (int j = 0; j < width; j += 2) {
                for (int i = uvHeight - 1; i >= 0; i--) {
                    raw[k++] = data[y_len + width * i + j];
                    raw[k++] = data[y_len + width * i + j + 1];
                }
            }
        } else {
            for (int i = 0; i < width; i++) {
                int nPos = width - 1;
                for (int j = 0; j < height; j++) {
                    raw[k] = data[nPos - i];
                    k++;
                    nPos += width;
                }
            }
            for (int i = 0; i < width; i += 2) {
                int nPos = y_len + width - 1;
                for (int j = 0; j < uvHeight; j++) {
                    raw[k] = data[nPos - i - 1];
                    raw[k + 1] = data[nPos - i];
                    k += 2;
                    nPos += width;
                }
            }
        }
    }
}
