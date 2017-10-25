package com.hubing.hlivepusher;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.hubing.hlivepusher.pusher.LivePusher;

public class MainActivity extends AppCompatActivity {
    private static String URL = "rtmp://www.hubg.top/myapp/mystream";
    private LivePusher livePusher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surface);
        livePusher = new LivePusher(surfaceView.getHolder());
        Button start = (Button) findViewById(R.id.start);
        Button switchVideo = (Button) findViewById(R.id.switchVideo);
        switchVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                livePusher.switchCamera();
            }
        });
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Button btn = (Button) view;
                if (btn.getText().equals("开始直播")) {
                    livePusher.startPush(URL);
                    btn.setText("停止直播");
                } else {
                    livePusher.stopPush();
                    btn.setText("开始直播");
                }
            }
        });


    }


}
