package com.moonstone.ezmaps_app.ezchat;

import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import com.moonstone.ezmaps_app.R;
import com.squareup.picasso.Picasso;

import de.hdodenhof.circleimageview.CircleImageView;

public class incomingCall extends AppCompatActivity {

    private MediaPlayer mMediaPlayer;
    private ImageButton accept, decline;
    private TextView callerName;
    private String callerString;
    private String callerPicUrl;
    private String roomId;
    private CircleImageView callerPic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.temp);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            callerString= extras.getString("callerName");
            roomId = extras.getString("roomId");
            callerPicUrl = extras.getString("callerPic");
        }
        accept = findViewById(R.id.acceptCall);
        decline = findViewById(R.id.declineCall);
        callerName = findViewById(R.id.callerName);
        callerPic = findViewById(R.id.callerPic);

        if(!callerPicUrl.isEmpty()){
            Log.d("overhere", callerPicUrl);
            Picasso.get().load(callerPicUrl).into(callerPic);
        }

        callerName.setText(callerString);

        decline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediaPlayer.stop();
                finish();
            }
        });

        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), Calling.class);
                i.putExtra("name",callerString );
                i.putExtra("roomId", roomId);
                mMediaPlayer.stop();
                startActivity(i);
            }
        });

        playSound();
    }


    private void playSound(){
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer = MediaPlayer.create(this, R.raw.ringtone);
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setLooping(true);
        Log.d("nononono", "What about here xxxx");
        mMediaPlayer.start();
    }
}
