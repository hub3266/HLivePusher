package com.hubing.hlivepusher.pusher;

/**
 * Created by hubing on 2017/10/17.
 */

public abstract class Pusher {
    public boolean pushing = false;

    public abstract void startPusher();

    public abstract void stopPusher();

    public abstract void release();
}
