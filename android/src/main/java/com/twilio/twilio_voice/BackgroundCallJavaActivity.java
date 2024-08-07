package com.twilio.twilio_voice;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

//import com.twilio.voice.Call;
import com.twilio.voice.CallInvite;
import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class BackgroundCallJavaActivity extends AppCompatActivity {

    private static String TAG = "BackgroundCallActivity";
    public static final String TwilioPreferences = "com.twilio.twilio_voicePreferences";


    //    private Call activeCall;
    private NotificationManager notificationManager;
    
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private TextView tvUserName;
    private TextView tvCallStatus;
    private ImageView btnMute;
    private ImageView menu_audio_device;
    private ImageView btnSalescaptain;
    private ImageView btnOutput;
    private ImageView btnHangUp;
    private AudioSwitch audioSwitch;
    private int savedVolumeControlStream;
    private MenuItem audioDeviceMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_background_call);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        tvUserName = (TextView) findViewById(R.id.tvUserName);
        tvCallStatus = (TextView) findViewById(R.id.tvCallStatus);
        btnMute = (ImageView) findViewById(R.id.btnMute);
        btnSalescaptain = (ImageView) findViewById(R.id.btnSalescaptain);
//        btnOutput = (ImageView) findViewById(R.id.btnOutput);
        btnHangUp = (ImageView) findViewById(R.id.btnHangUp);
        menu_audio_device = (ImageView) findViewById(R.id.menu_audio_device);
        KeyguardManager kgm = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Boolean isKeyguardUp = kgm.inKeyguardRestrictedInputMode();

        Log.d(TAG, "isKeyguardUp $isKeyguardUp");
        if (isKeyguardUp) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(true);
                setShowWhenLocked(true);
                kgm.requestDismissKeyguard(this, null);

            } else {
                wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
                wakeLock.acquire();

                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                );
            }
        }

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        
        audioSwitch = new AudioSwitch(getApplicationContext());
        savedVolumeControlStream = getVolumeControlStream();
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        handleCallIntent(getIntent());
    }

    private void handleCallIntent(Intent intent) {
        if (intent != null) {

            
            if (intent.getStringExtra(Constants.CALL_FROM) != null) {
                activateSensor();
                String fromId = intent.getStringExtra(Constants.CALL_FROM).replace("client:", "");
                Log.d(TAG, "caller fromID NEW");
                Log.d(TAG, fromId);
                Log.d(TAG, intent.getStringExtra(Constants.CALL_FROM));
                String caller;
                SharedPreferences preferences = getApplicationContext().getSharedPreferences(TwilioPreferences, Context.MODE_PRIVATE);
                if (fromId != null) {
                    caller = fromId;
                } else {
                    caller = preferences.getString(fromId, preferences.getString("defaultCaller", getString(R.string.unknown_caller)));
                }
                Log.d(TAG, "handleCallIntent");
                Log.d(TAG, "caller from");
                Log.d(TAG, caller);

                tvUserName.setText(caller);
                tvCallStatus.setText(getString(R.string.connected_status));
                Log.d(TAG, "handleCallIntent-");
                configCallUI();
                startAudioSwitch();
            }else{
                finish();
            }
        }
    }

    private void activateSensor() {
        if (wakeLock == null) {
            Log.d(TAG, "New wakeLog");
            wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "incall");
        }
        if (!wakeLock.isHeld()) {
            Log.d(TAG, "wakeLog acquire");
            wakeLock.acquire();
        } 
    }

    private void deactivateSensor() {
        if (wakeLock != null && wakeLock.isHeld()) {
            Log.d(TAG, "wakeLog release");
            wakeLock.release();
        } 
    }

    private void startAudioSwitch() {
        audioSwitch.start((audioDevices, audioDevice) -> {
            Log.d(TAG, "Updating AudioDeviceIcon");
            selectPreferredAudioDevice(audioDevices);
            return Unit.INSTANCE;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        audioSwitch.start((audioDevices, selectedAudioDevice) -> {
            selectPreferredAudioDevice(audioDevices);
            return Unit.INSTANCE;
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        audioSwitch.stop();
    }


    private void selectPreferredAudioDevice(List<? extends AudioDevice> audioDevices) {
        for (AudioDevice device : audioDevices) {
            if (device instanceof AudioDevice.BluetoothHeadset) {
                audioSwitch.selectDevice(device);
                updateAudioDeviceIcon(device);
                return;
            }
        }
        // Optionally, select another device if no Bluetooth devices are connected
        audioSwitch.selectDevice(audioSwitch.getAvailableAudioDevices().get(0));
        updateAudioDeviceIcon(audioSwitch.getAvailableAudioDevices().get(0));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getAction() != null) {
            Log.d(TAG, "onNewIntent-");
            Log.d(TAG, intent.getAction());
            switch (intent.getAction()) {
                case Constants.ACTION_CANCEL_CALL:
                    callCanceled();
                    break;
                default: {
                }
            }
        }
    }
    

    boolean isMuted = false;

    private void configCallUI() {
        Log.d(TAG, "configCallUI");

        menu_audio_device.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onCLick");
                showAudioDevices();
            }
        });

        btnMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onCLick");
                sendIntent(Constants.ACTION_TOGGLE_MUTE);
                isMuted = !isMuted;
                applyFabState(btnMute, isMuted);
            }
        });

        btnHangUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendIntent(Constants.ACTION_END_CALL);
                finish();

            }
        });

        btnSalescaptain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "onCLick");
                finish();
            }
        });

//        btnOutput.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
//                boolean isOnSpeaker = !audioManager.isSpeakerphoneOn();
//                audioManager.setSpeakerphoneOn(isOnSpeaker);
//                applyFabState(btnOutput, isOnSpeaker);
//            }
//        });

    }

    private void applyFabState(ImageView button, Boolean enabled) {
        // Set fab as pressed when call is on hold

        ColorStateList colorStateList;

        if (enabled) {
            colorStateList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white_55));
        } else {
            colorStateList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setBackgroundTintList(colorStateList);
        }
    }

    private void sendIntent(String action) {
        Log.d(TAG, "Sending intent");
        Log.d(TAG, action);
        Intent activeCallIntent = new Intent();
        activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activeCallIntent.setAction(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(activeCallIntent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {    
        MenuInflater inflater = getMenuInflater();    
        inflater.inflate(R.menu.menu, menu);    
        audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device);    
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {    
        if (item.getItemId() == R.id.menu_audio_device) {        
            showAudioDevices();        
            return true;   
        }    
        return false;
    }


    private void showAudioDevices() {
        AudioDevice selectedDevice = audioSwitch.getSelectedAudioDevice();
        List<AudioDevice> availableAudioDevices = audioSwitch.getAvailableAudioDevices();
        if (selectedDevice != null) {
            int selectedDeviceIndex = availableAudioDevices.indexOf(selectedDevice);
            ArrayList<String> audioDeviceNames = new ArrayList<>();
            for (AudioDevice a : availableAudioDevices) {
                audioDeviceNames.add(a.getName());
            }
            new AlertDialog.Builder(this)
                .setTitle("Select Audio Device")                
                .setSingleChoiceItems(                    
                    audioDeviceNames.toArray(new CharSequence[0]),                      
                    selectedDeviceIndex,                       
                    (dialog, index) -> {                            
                        dialog.dismiss();                            
                        AudioDevice selectedAudioDevice = availableAudioDevices.get(index);                            
                        updateAudioDeviceIcon(selectedAudioDevice);                            
                        audioSwitch.selectDevice(selectedAudioDevice);                        
                    }).create().show();    
        }
    }

    private void updateAudioDeviceIcon(AudioDevice selectedAudioDevice) {    
        int audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp;    
        if (selectedAudioDevice instanceof AudioDevice.BluetoothHeadset) {       
            audioDeviceMenuIcon = R.drawable.ic_bluetooth_white_24dp;    
        } else if (selectedAudioDevice instanceof AudioDevice.WiredHeadset) {        
            audioDeviceMenuIcon = R.drawable.ic_headset_mic_white_24dp;    
        } else if (selectedAudioDevice instanceof AudioDevice.Earpiece) {        
            audioDeviceMenuIcon = R.drawable.ic_phonelink_ring_white_24dp;    
        } else if (selectedAudioDevice instanceof AudioDevice.Speakerphone) {        
            audioDeviceMenuIcon = R.drawable.ic_volume_up_white_24dp;    
        }    
        if (audioDeviceMenuItem != null) {       
            audioDeviceMenuItem.setIcon(audioDeviceMenuIcon);    
        }
    }


    private void callCanceled() {
        Log.d(TAG, "Call is cancelled");
        finish();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        setVolumeControlStream(savedVolumeControlStream);
        deactivateSensor();
    }

}