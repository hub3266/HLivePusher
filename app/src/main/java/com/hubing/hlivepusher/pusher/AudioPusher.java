package com.hubing.hlivepusher.pusher;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.hubing.hlivepusher.params.AudioParams;

/**
 * Created by hubing on 2017/10/17.
 */

public class AudioPusher extends Pusher {
    private AudioRecord audioRecord;
    private  int minBufferSize;
    private AudioParams params;


    public AudioPusher(AudioParams params){
        this.params = params;
        int channelConfig = this.params.getChannel() == 1 ?
                AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
        //最小缓冲区大小
        minBufferSize = AudioRecord.getMinBufferSize(this.params.getSampleRateInHz(), channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                this.params.getSampleRateInHz(),
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
    }
    @Override
    public void startPusher() {
        new Thread(new AudioRecordTask()).start();
    }

    @Override
    public void stopPusher() {

    }

    @Override
    public void release() {

    }

    class AudioRecordTask implements Runnable{

        @Override
        public void run() {
            //开始录音
            audioRecord.startRecording();

            while(pushing){
                //通过AudioRecord不断读取音频数据
                byte[] buffer = new byte[minBufferSize];
                int len = audioRecord.read(buffer, 0, buffer.length);
                if(len > 0){
                    //传给Native代码，进行音频编码
                }
            }
        }

    }
}
