package com.twilio.twilio_voice;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.MediaPlayer;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.media.AudioManager;
import android.content.IntentFilter;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;

import android.media.session.MediaSessionManager;
import android.media.session.MediaController;
import android.text.TextUtils;

public class IncomingCallNotificationService extends Service {

    private static final String TAG = IncomingCallNotificationService.class.getSimpleName();
    public static final String TwilioPreferences = "com.twilio.twilio_voicePreferences";
    private Context context;
    private IntentFilter intentFilter;
    private static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";
    private static int counter;
    private static int doublePressSpeed = 300; // double keypressed in ms
    private static Timer doublePressTimer;
    private static CallInvite privCallInvite;
    private static int privNotificationId;
    private static Intent privIntent;
    private static PendingIntent privIntentNotif;
    private static int answeredNotificationId;
    private boolean isPlaying = false;
    public static MediaSessionCompat mediaSession;

    private MediaSessionManager mMediaSessionManager;
    private Handler handler = new Handler();
    private MediaPlayer mediaPlayer;

    Call activeCall;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        Log.i(TAG, "onStartCommand " + action);
        if (action != null) {
            CallInvite callInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
            privIntent = intent;
            privCallInvite = intent.getParcelableExtra(Constants.INCOMING_CALL_INVITE);
            int notificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);
            privNotificationId = intent.getIntExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, 0);
            Log.i(TAG, "onStartCommand notificationId" + notificationId);
            Log.i(TAG, "is callInvite null: " + (callInvite != null));
            switch (action) {
                case Constants.ACTION_INCOMING_CALL:
                    handleIncomingCall(callInvite, notificationId);
                    break;
                case Constants.ACTION_ACCEPT:
                    int origin = intent.getIntExtra(Constants.ACCEPT_CALL_ORIGIN, 0);
                    Log.d(TAG, "onStartCommand-ActionAccept in IncomingCallNotificationService" + origin);
                    if (mediaPlayer != null && isPlaying) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                        isPlaying = false;
                    }
                    accept(callInvite, notificationId, origin);
                    break;
                case Constants.ACTION_REJECT:
                    if (mediaPlayer != null && isPlaying) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                        isPlaying = false;
                    }
                    reject(callInvite);
                    break;
                case Constants.ACTION_CANCEL_CALL:
                    if (mediaPlayer != null && isPlaying) {
                        mediaPlayer.stop();
                        mediaPlayer.release();
                        mediaPlayer = null;
                        isPlaying = false;
                    }
                    handleCancelledCall(intent);
                    break;
                case Constants.ACTION_RETURN_CALL:
                    returnCall(intent);
                    break;
                default:
                    break;
            }
        }
        isPlaying = true;
        mediaSession = new MediaSessionCompat(this, "MediaSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);

        PendingIntent mbrIntent = PendingIntent.getBroadcast(this, 0, new Intent(Intent.ACTION_MEDIA_BUTTON), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        mediaSession.setMediaButtonReceiver(mbrIntent);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                // Handle play
            }

            @Override
            public void onPause() {
                super.onPause();
                // Handle pause
            }

            @Override
            public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyEvent.getKeyCode()) {
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            Log.d(TAG, "Inside Media Listner");
                            if (mediaPlayer != null && isPlaying) {
                                mediaPlayer.stop();
                                mediaPlayer.release();
                                mediaPlayer = null;
                            }
                            if (privIntentNotif != null && isPlaying) {
                                try {
                                    isPlaying = false;
                                    privIntentNotif.send();
                                    privIntentNotif = null;
                                    Log.d(TAG, "Intent sent successfully");
                                } catch (PendingIntent.CanceledException e) {
                                    Log.e(TAG, "PendingIntent was cancelled", e);
                                }
                            } else {
                                Log.e(TAG, "PendingIntent is null");
                            }
                            return true;
                    }
                }
                return super.onMediaButtonEvent(intent);
            }
        });

        mediaSession.setActive(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 0, SystemClock.elapsedRealtime())
                    .build();

            mediaSession.setPlaybackState(playbackState);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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
                    stopSelf();
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
//                    endCall();
                }
            }

        };
    }

    private void startAnswerActivity(Call call) {
        Intent intent = new Intent(this, BackgroundCallJavaActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.CALL_FROM, call.getFrom());
        startActivity(intent);
        Log.d(TAG, "Connected");
    }

    private Notification createNotification(CallInvite callInvite, int notificationId, int channelImportance) {
        Log.i(TAG, "createNotification");
        Intent intent = new Intent(this, AnswerJavaActivity.class);
        intent.setAction(Constants.ACTION_INCOMING_CALL_NOTIFICATION);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT |  PendingIntent.FLAG_IMMUTABLE);
        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putString(Constants.CALL_SID_KEY, callInvite.getCallSid());

        Context context = getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(TwilioPreferences, Context.MODE_PRIVATE);
        Log.i(TAG, "Setting notification from, " + callInvite.getFrom());
        String fromId = callInvite.getFrom().replace("client:", "");
        Log.i(TAG, "CALLER NAME AFTER REMOVAL = " + fromId);
        String caller;
        if (fromId != null) {
            caller = fromId;
        } else {
            caller = preferences.getString(fromId, preferences.getString("defaultCaller", "Unknown caller"));
        }
        caller = caller.replaceAll("_", " ");
        Log.i(TAG, "CALLER NAME AFTER SETTING = " + caller);
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Version O = Android 8
            Log.i(TAG, "building notification for Android 8+");
            return buildNotification(getApplicationName(context), getString(R.string.new_call, caller),
                    pendingIntent,
                    extras,
                    callInvite,
                    notificationId,
                    createChannel(channelImportance));
        } else {
            Log.i(TAG, "building notification for older phones");

            return new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                    .setContentTitle(getApplicationName(context))
                    .setContentText(getString(R.string.new_call, caller))
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setExtras(extras)
                    .setContentIntent(pendingIntent)
                    .setFullScreenIntent(pendingIntent, true)
                    .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000, 1000, 1000})
                    .setLights(Color.RED, 3000, 3000)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setColor(Color.rgb(20, 10, 200)).build();
        }
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    /**
     * Build a notification.
     *
     * @param text          the text of the notification
     * @param pendingIntent the body, pending intent for the notification
     * @param extras        extras passed with the notification
     * @return the builder
     */
    @TargetApi(Build.VERSION_CODES.O)
    private Notification buildNotification(String title, String text, PendingIntent pendingIntent, Bundle extras,
                                           final CallInvite callInvite,
                                           int notificationId,
                                           String channelId) {
        Log.d(TAG, "Building notification");
        Intent rejectIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
        rejectIntent.setAction(Constants.ACTION_REJECT);
        rejectIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        rejectIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        PendingIntent piRejectIntent = PendingIntent.getService(getApplicationContext(), 0, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT |  PendingIntent.FLAG_IMMUTABLE);

        Intent acceptIntent;
        PendingIntent piAcceptIntent;
        // VERSION S = Android 12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "building acceptIntent for Android 12+");
            acceptIntent = new Intent(getApplicationContext(), IncomingCallNotificationActivity.class);
            acceptIntent.setAction(Constants.ACTION_ACCEPT);
            acceptIntent.putExtra(Constants.ACCEPT_CALL_ORIGIN, 0);
            acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
            acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            piAcceptIntent = PendingIntent.getActivity(getApplicationContext(), 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT |  PendingIntent.FLAG_IMMUTABLE);
            privIntentNotif = piAcceptIntent;
        }
        else {
            acceptIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
            acceptIntent.setAction(Constants.ACTION_ACCEPT);
            acceptIntent.putExtra(Constants.ACCEPT_CALL_ORIGIN, 0);
            acceptIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
            acceptIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
            piAcceptIntent = PendingIntent.getService(getApplicationContext(), 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            privIntentNotif = piAcceptIntent;
        }

        long[] mVibratePattern = new long[]{0, 400, 400, 400, 400, 400, 400, 400};
        Notification.Builder builder =
                new Notification.Builder(getApplicationContext(), channelId)
                        .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setCategory(Notification.CATEGORY_CALL)
                        .setFullScreenIntent(pendingIntent, true)
                        .setExtras(extras)
                        .setVibrate(mVibratePattern)
                        .setAutoCancel(true)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .addAction(android.R.drawable.ic_menu_delete, getString(R.string.decline), piRejectIntent)
                        .addAction(android.R.drawable.ic_menu_call, getString(R.string.answer), piAcceptIntent)
                        .setFullScreenIntent(pendingIntent, true);

        return builder.build();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private String createChannel(int channelImportance) {
        Log.i(TAG, "creating channel!");
        NotificationChannel callInviteChannel = new NotificationChannel(Constants.VOICE_CHANNEL_HIGH_IMPORTANCE,
                "Primary Voice Channel", NotificationManager.IMPORTANCE_HIGH);
        String channelId = Constants.VOICE_CHANNEL_HIGH_IMPORTANCE;

        if (channelImportance == NotificationManager.IMPORTANCE_LOW) {
            Log.i(TAG, "channel is low importance");
            callInviteChannel = new NotificationChannel(Constants.VOICE_CHANNEL_LOW_IMPORTANCE,
                    "Primary Voice Channel", NotificationManager.IMPORTANCE_LOW);
            channelId = Constants.VOICE_CHANNEL_LOW_IMPORTANCE;
        }
        callInviteChannel.setLightColor(Color.GREEN);
        callInviteChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(callInviteChannel);

        return channelId;
    }

    private void accept(CallInvite callInvite, int notificationId, int origin) {
        endForeground();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(notificationId);
        Log.i(TAG, "accept call invite! in IncomingCallNotificationService");
//        SoundPoolManager.getInstance(this).stopRinging();
        Log.i(TAG, "IsAppVisible: " + isAppVisible() + " Origin: " + origin);
        Intent activeCallIntent;
        if (origin == 0 && !isAppVisible()) {
            Log.i(TAG, "Creating answerJavaActivity intent");
            activeCallIntent = new Intent(this, AnswerJavaActivity.class);
        } else {
            Log.i(TAG, "Creating answer broadcast intent");
            activeCallIntent = new Intent();
        }
        answeredNotificationId = notificationId;
        activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activeCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activeCallIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        activeCallIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        activeCallIntent.putExtra(Constants.ACCEPT_CALL_ORIGIN, origin);
        activeCallIntent.setAction(Constants.ACTION_ACCEPT);
        Log.i(TAG, "Launch IsAppVisible && !isAppVisible: " + (origin == 0 && !isAppVisible()));
        if (origin == 0 && !isAppVisible()) {
            startActivity(activeCallIntent);
            Log.i(TAG, "starting activity");
        } else {
//            Intent openAppCallIntent;
//            String packageName = "com.theclosecompany.sales_book";
//            openAppCallIntent = getPackageManager().getLaunchIntentForPackage(packageName);
//            if (openAppCallIntent != null) {
//                startActivity(openAppCallIntent);
//            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(activeCallIntent);
            Log.i(TAG, "sending broadcast intent");
        }
    }

    private void reject(CallInvite callInvite) {
        callInvite.reject(getApplicationContext());
//        SoundPoolManager.getInstance(this).stopRinging();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        SoundPoolManager.getInstance(this).playDisconnect();
        isPlaying = false;
        privIntentNotif = null;
        Intent rejectCallIntent = new Intent();
        rejectCallIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        rejectCallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rejectCallIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        rejectCallIntent.setAction(Constants.ACTION_REJECT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(rejectCallIntent);
        endForeground();
    }

    private void handleCancelledCall(Intent intent) {
//        SoundPoolManager.getInstance(this).stopRinging();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
        isPlaying = false;
        privIntentNotif = null;
        CancelledCallInvite cancelledCallInvite = intent.getParcelableExtra(Constants.CANCELLED_CALL_INVITE);
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(TwilioPreferences, Context.MODE_PRIVATE);
        boolean prefsShow = preferences.getBoolean("show-notifications", true);
        if (prefsShow) {
            isPlaying = false;
            privIntentNotif = null;
            buildMissedCallNotification(cancelledCallInvite.getFrom(), cancelledCallInvite.getTo());
        }
        endForeground();
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void returnCall(Intent intent) {
        endForeground();
        Log.i(TAG, "returning call!!!!");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.cancel(100);
    }


    private void buildMissedCallNotification(String callerId, String to) {

        String fromId = callerId.replace("client:", "");
        Context context = getApplicationContext();
        SharedPreferences preferences = context.getSharedPreferences(TwilioPreferences, Context.MODE_PRIVATE);
        String caller;
        if (fromId != null) {
            caller = fromId;
        } else {
            caller = preferences.getString(fromId, preferences.getString("defaultCaller", "Unknown caller"));
        }
        caller = caller.replaceAll("_", " ");
        String title = getString(R.string.notification_missed_call, caller);


        Intent returnCallIntent = new Intent(getApplicationContext(), IncomingCallNotificationService.class);
        returnCallIntent.setAction(Constants.ACTION_RETURN_CALL);
        returnCallIntent.putExtra(Constants.CALL_TO, to);
        returnCallIntent.putExtra(Constants.CALL_FROM, callerId);
        PendingIntent piReturnCallIntent = PendingIntent.getService(getApplicationContext(), 0, returnCallIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        Notification notification;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this, createChannel(NotificationManager.IMPORTANCE_HIGH))


                            .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                            .setContentTitle(title)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setAutoCancel(true)
                            .addAction(android.R.drawable.ic_menu_call, getString(R.string.twilio_call_back), piReturnCallIntent)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentTitle(getApplicationName(context))
                            .setContentText(title)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notification = builder.build();
        } else {
            notification = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_call_end_white_24dp)
                    .setContentTitle(getApplicationName(context))
                    .setContentText(title)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .addAction(android.R.drawable.ic_menu_call, getString(R.string.decline), piReturnCallIntent)
                    .setColor(Color.rgb(20, 10, 200)).build();
        }
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(100, notification);
    }

    private void handleIncomingCall(CallInvite callInvite, int notificationId) {
        Log.i(TAG, "handle incoming call");
        Log.d(TAG, "NOTIFICATION ID 428 LINE: " + notificationId);
//        SoundPoolManager.getInstance(this).playRinging();
        mediaPlayer = MediaPlayer.create(this, R.raw.incoming);
        mediaPlayer.setLooping(true);
        mediaPlayer.start();
//        startMediaSessionControlLoop();
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.d(TAG, "AUDIO FOCUS GAINED");
                        mediaSession.setActive(true);
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        Log.d(TAG, "AUDIO FOCUS LOST");
                        mediaSession.setActive(false);
//                        new Handler().postDelayed(() -> {
//                            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
//                        }, 1000);
                        break;
                }
            }
        };

        AudioFocusRequest focusRequest = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .build();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            int result = audioManager.requestAudioFocus(focusRequest);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setCallInProgressNotification(callInvite, notificationId);
        }
        sendCallInviteToActivity(callInvite, notificationId);
    }

    private void endForeground() {
        stopForeground(true);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void setCallInProgressNotification(CallInvite callInvite, int notificationId) {
        if (isAppVisible()) {
            Log.i(TAG, "setCallInProgressNotification - app is visible.");
            startForeground(notificationId, createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_LOW), FOREGROUND_SERVICE_MEDIA_PLAYBACK);
        } else {
            Log.i(TAG, "setCallInProgressNotification - app is NOT visible.");
            startForeground(notificationId, createNotification(callInvite, notificationId, NotificationManager.IMPORTANCE_HIGH), FOREGROUND_SERVICE_MEDIA_PLAYBACK);
        }
    }

    /*
     * Send the CallInvite to the VoiceActivity. Start the activity if it is not running already.
     */
    private void sendCallInviteToActivity(CallInvite callInvite, int notificationId) {

        Log.d(TAG, "NOTIFICATION ID 455 LINE: " + notificationId);

        Log.i(TAG, "sendCallInviteToActivity.");

        Intent pluginIntent = new Intent();
        pluginIntent.setAction(Constants.ACTION_INCOMING_CALL);
        pluginIntent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        pluginIntent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        LocalBroadcastManager.getInstance(this).sendBroadcast(pluginIntent);
        Log.i(TAG, "AppHasStarted " + TwilioVoicePlugin.appHasStarted + " sdk>=29 and !isAppVisible() " + (Build.VERSION.SDK_INT >= 29 && !isAppVisible()));
        if (TwilioVoicePlugin.appHasStarted || (Build.VERSION.SDK_INT >= 29 && !isAppVisible())) {
            return;
            
        }
        startAnswerActivity(callInvite, notificationId);
        Log.i(TAG, "Starting AnswerActivity from IncomingCallNotificationService");
    }

    private void startAnswerActivity(CallInvite callInvite, int notificationId) {
        Log.d(TAG, "NOTIFICATION ID 473 LINE: " + notificationId);
        Intent intent = new Intent(this, AnswerJavaActivity.class);
        intent.setAction(Constants.ACTION_INCOMING_CALL);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean isAppVisible() {
        return ProcessLifecycleOwner
                .get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Destroying IncomingCallNotificationService");
        if (mediaSession != null) {
            mediaSession.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}
