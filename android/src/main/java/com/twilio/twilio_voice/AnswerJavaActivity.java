package com.twilio.twilio_voice;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.media.AudioManager;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
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

public class AnswerJavaActivity extends AppCompatActivity {

    private static String TAG = "AnswerActivity";
    public static final String TwilioPreferences = "com.twilio.twilio_voicePreferences";

    private NotificationManager notificationManager;
    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;
    private MediaButtonIntentReceiver mediaButtonReceiver;

    private boolean initiatedDisconnect = false;

    private CallInvite activeCallInvite;
    private int activeCallNotificationId;
    private static final int MIC_PERMISSION_REQUEST_CODE = 17893;
    private static final int MIC_BLUETOOTH_REQUEST_CODE = 17693;
    private PowerManager.WakeLock wakeLock;
    private TextView tvUserName;
    private TextView tvCallStatus;
    private ImageView btnAnswer;
    private ImageView btnReject;
    private AudioSwitch audioSwitch;
    private int savedVolumeControlStream;
    private MenuItem audioDeviceMenuItem;

    Call.Listener callListener = callListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_answer);

        tvUserName = (TextView) findViewById(R.id.tvUserName);
        tvCallStatus = (TextView) findViewById(R.id.tvCallStatus);
        btnAnswer = (ImageView) findViewById(R.id.btnAnswer);
        btnReject = (ImageView) findViewById(R.id.btnReject);

        KeyguardManager kgm = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean isKeyguardUp = kgm.inKeyguardRestrictedInputMode();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        // registerReceiver();
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        mediaButtonReceiver = new MediaButtonIntentReceiver();
        registerReceiver();

        Log.d(TAG, "isKeyguardUp $isKeyguardUp");
        if (isKeyguardUp) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setTurnScreenOn(true);
                setShowWhenLocked(true);
                kgm.requestDismissKeyguard(this, null);
            } else {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
                wakeLock.acquire(60 * 1000L /*10 minutes*/);

                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                );
            }

        }

        handleIncomingCallIntent(getIntent());
        // audioSwitch = new AudioSwitch(getApplicationContext());
        // savedVolumeControlStream = getVolumeControlStream();
        // setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    private void handleIncomingCallIntent(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            Log.d(TAG, "handleIncomingCallIntent-");
            String action = intent.getAction();
            activeCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
            activeCallNotificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);
            tvCallStatus.setText(R.string.incoming_call_title);
            Log.d(TAG, action);
            switch (action) {
                case Constants.ACTION_INCOMING_CALL:
                case Constants.ACTION_INCOMING_CALL_NOTIFICATION:
                    configCallUI();
                    break;
                case Constants.ACTION_CANCEL_CALL:
                    newCancelCallClickListener();
                    break;
                case Constants.ACTION_ACCEPT:
                    Log.d(TAG, "ACTION ACCEPT IN AnswerJavaActivity");
                    checkPermissionsAndAccept();
                    break;
                case Constants.ACTION_END_CALL:
                    Log.d(TAG, "ending call" + activeCall != null ? "True" : "False");
                    if (activeCall == null) {
                        Log.d(TAG, "No active call to end. Returning");
                        finishAndRemoveTask();
                        break;
                    }
                    activeCall.disconnect();
                    initiatedDisconnect = true;
                    finishAndRemoveTask();
                    break;
                case Constants.ACTION_TOGGLE_MUTE:
                    boolean muted = activeCall.isMuted();
                    activeCall.mute(!muted);
                    break;
                default: {
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent-");
        if (intent != null && intent.getAction() != null) {
            Log.d(TAG, intent.getAction());
            switch (intent.getAction()) {
                case Constants.ACTION_CANCEL_CALL:
                    newCancelCallClickListener();
                    break;
                default: {
                }
            }
        }
    }


    private void configCallUI() {
        Log.d(TAG, "configCallUI");
        if (activeCallInvite != null) {

            String fromId = activeCallInvite.getFrom().replace("client:", "");
            SharedPreferences preferences = getApplicationContext().getSharedPreferences(TwilioPreferences, Context.MODE_PRIVATE);
            String caller;
            if (fromId != null) {
                caller = fromId;
            } else {
                caller = preferences.getString(fromId, preferences.getString("defaultCaller", getString(R.string.unknown_caller)));
            }
            tvUserName.setText(caller);

            btnAnswer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.d(TAG, "onCLick");
                    checkPermissionsAndAccept();
                }
            });

            btnReject.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    rejectCallClickListener();
                }
            });
        }
    }

    private void checkPermissionsAndAccept() {
        Log.d(TAG, "Clicked accept");
        if (!checkPermissionForMicrophone()) {
            Log.d(TAG, "configCallUI-requestAudioPermissions");
            requestAudioPermissions();
        } else {    
            Log.d(TAG, "configCallUI-newAnswerCallClickListener");
            acceptCall();
        }
    }

    private void startAudioSwitch() {
        audioSwitch.start((audioDevices, audioDevice) -> {
            Log.d(TAG, "Updating AudioDeviceIcon");
            updateAudioDeviceIcon(audioDevice);
            return Unit.INSTANCE;
        });
    }



    private void acceptCall() {
        Log.d(TAG, "Accepting call");
        Intent acceptIntent = new Intent(this, IncomingCallNotificationService.class);
        acceptIntent.setAction(Constants.ACTION_ACCEPT);
        acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
        acceptIntent.putExtra(Constants.ACCEPT_CALL_ORIGIN, 1);
        acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, activeCallNotificationId);
        Log.d(TAG, "Clicked accept startService");
        startService(acceptIntent);
        Log.d(TAG, "isLocked: " + isLocked() + " appHasStarted: " + TwilioVoicePlugin.appHasStarted);
        if (TwilioVoicePlugin.appHasStarted) {
            Log.d(TAG, "AnswerJavaActivity Finish");
            finish();
        }
        else {
            Log.d(TAG, "Answering call in AnswerjavaActivity 244 with id: " + activeCallNotificationId);
            notificationManager.cancel(activeCallNotificationId);
            activeCallInvite.accept(this, callListener);
        }
    }

    private boolean isLocked() {
        KeyguardManager myKM = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return myKM.inKeyguardRestrictedInputMode();
    }

    private void startAnswerActivity(Call call) {
        Intent intent = new Intent(this, BackgroundCallJavaActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.CALL_FROM, call.getFrom());
        startActivity(intent);
        Log.d(TAG, "Connected");
    }

    private void endCall() {
        Log.d(TAG, "endCall - initiatedDisconnect: " + initiatedDisconnect);
        if (!initiatedDisconnect) {
            Intent intent = new Intent(this, BackgroundCallJavaActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Constants.ACTION_CANCEL_CALL);

            this.startActivity(intent);
            finishAndRemoveTask();
        }

    }

    Call activeCall;

    private Call.Listener callListener() {
        return new Call.Listener() {


            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
                // audioSwitch.deactivate();
                Log.d(TAG, "Connect failure");
                Log.e(TAG, "Call Error: %d, %s" + error.getErrorCode() + error.getMessage());
            }

            @Override
            public void onRinging(@NonNull Call call) {

            }

            @Override
            public void onConnected(@NonNull Call call) {
                // audioSwitch.activate();
                activeCall = call;
                if (!TwilioVoicePlugin.appHasStarted) {
                    Log.d(TAG, "Connected from BackgroundUI");
                    TwilioVoicePlugin.activeCall = call;
                    startAnswerActivity(call);
                    finish();
                }
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                Log.d(TAG, "onReconnecting");
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                Log.d(TAG, "onReconnected");
            }

            @Override
            public void onDisconnected(@NonNull Call call, CallException error) {
                // audioSwitch.deactivate();
                if (!TwilioVoicePlugin.appHasStarted) {
                    Log.d(TAG, "Disconnected");
                    endCall();
                }
            }

        };
    }

    private class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Received broadcast for action " + action);

            if (action != null)
                switch (action) {
                    case Constants.ACTION_INCOMING_CALL:
                    case Constants.ACTION_CANCEL_CALL:
                    case Constants.ACTION_TOGGLE_MUTE:
                    case Constants.ACTION_END_CALL:
                        /*
                         * Handle the incoming or cancelled call invite
                         */
                        Log.d(TAG, "received intent to answerActivity");
                        handleIncomingCallIntent(intent);
                        break;
                    default:
                        Log.d(TAG, "Received broadcast for other action " + action);
                        break;

                }
        }
    }

    public class MediaButtonIntentReceiver extends BroadcastReceiver {

        public MediaButtonIntentReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (!Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
                return;
            }
            KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null) {
                return;
            }
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
            // do something
                Toast.makeText(context, "BUTTON PRESSED!", Toast.LENGTH_SHORT).show(); 
            }
            abortBroadcast();
        }
    }

    private void registerReceiver() {
        Log.d(TAG, "Registering answerJavaActivity receiver");
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Constants.ACTION_TOGGLE_MUTE);
            intentFilter.addAction(Constants.ACTION_CANCEL_CALL);
            intentFilter.addAction(Constants.ACTION_END_CALL);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    voiceBroadcastReceiver, intentFilter);
            IntentFilter mediaButtonFilter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mediaButtonReceiver, mediaButtonFilter);
            isReceiverRegistered = true;
        }
    }

    private void unregisterReceiver() {
        Log.d(TAG, "Unregistering receiver");
        if (isReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceBroadcastReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaButtonReceiver);
            isReceiverRegistered = false;
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
        // startAudioSwitch();
    }

    // We still want to listen messages from backgroundCallJavaActivity
//    @Override
//    protected void onPause() {
//        super.onPause();
//        unregisterReceiver();
//    }

    private void newCancelCallClickListener() {
        finish();
    }

    private void rejectCallClickListener() {
        Log.d(TAG, "Reject Call Click listener");
        if (activeCallInvite != null) {
            Intent rejectIntent = new Intent(this, IncomingCallNotificationService.class);
            rejectIntent.setAction(Constants.ACTION_REJECT);
            rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, activeCallInvite);
            startService(rejectIntent);
            finish();
        }
    }

    private Boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (resultMic != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        int resultMic2 = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT);
        if (resultMic2 != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return true;
    }

    private void requestAudioPermissions() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT};
        String[] permissionAudio = {Manifest.permission.RECORD_AUDIO};
        String[] permissionBluetooth = {Manifest.permission.BLUETOOTH_CONNECT};
        Log.d(TAG, "requestAudioPermissions");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, permissionAudio, MIC_PERMISSION_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, permissionAudio, MIC_PERMISSION_REQUEST_CODE);
            }
        } 
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
                ActivityCompat.requestPermissions(this, permissionBluetooth, MIC_BLUETOOTH_REQUEST_CODE);
            } else {
                ActivityCompat.requestPermissions(this, permissionBluetooth, MIC_BLUETOOTH_REQUEST_CODE);
            }
        } 
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "requestAudioPermissions-> permission granted->newAnswerCallClickListener");
            // startAudioSwitch();
            acceptCall();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permissions needed. Please allow in your application settings.", Toast.LENGTH_LONG).show();
                rejectCallClickListener();
            } else {
                // startAudioSwitch();
                acceptCall();
            }
        } else if (requestCode == MIC_BLUETOOTH_REQUEST_CODE) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permissions needed. Please allow in your application settings.", Toast.LENGTH_LONG).show();
                acceptCall();
            } else {
                // startAudioSwitch();
                acceptCall();
            }
        } else {
            throw new IllegalStateException("Unexpected value: " + requestCode);
        }
    }
    // @Override
    // public boolean onCreateOptionsMenu(Menu menu) {    
    //     MenuInflater inflater = getMenuInflater();    
    //     inflater.inflate(R.menu.menu, menu);    
    //     audioDeviceMenuItem = menu.findItem(R.id.menu_audio_device);    
    //     return true;
    // }
    
    // @Override
    // public boolean onOptionsItemSelected(MenuItem item) {    
    //     if (item.getItemId() == R.id.menu_audio_device) {        
    //         showAudioDevices();        
    //         return true;   
    //     }    
    //     return false;
    // }


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


    @Override
    protected void onDestroy() {
        Log.d(TAG, "AnwserJAvaActivity ondestroy");
        super.onDestroy();
        // audioSwitch.stop();
        setVolumeControlStream(savedVolumeControlStream);
        unregisterReceiver();
        if (wakeLock != null) {
            wakeLock.release();
        }
    }

}
