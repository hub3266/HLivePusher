package com.hubing.hlivepusher.params;

/**
 * Created by hubing on 2017/10/17.
 */

public class VideoParams {
    private int width;
    private int height;

    //    码率
    private int bitrate = 480000;
    private int fps = 10;
    private int cameraId;

    public VideoParams(int width,int height,int cameraId){
        this.width = width;
        this.height = height;
        this.cameraId = cameraId;

    }

    public VideoParams(int width,int height,int cameraId,int bitrate,int fps){
        this.width = width;
        this.height = height;
        this.cameraId = cameraId;
        this.bitrate = bitrate;
        this.fps = fps;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getBitrate() {
        return bitrate;
    }

    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public int getCameraId() {
        return cameraId;
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }
}
