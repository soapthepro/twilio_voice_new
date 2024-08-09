package com.twilio.twilio_voice;

import android.content.ComponentName;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.media.session.MediaSessionManager;
import android.media.session.MediaController;

import androidx.annotation.RequiresApi;

import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MyNotificationListenerService extends NotificationListenerService {

    private MediaSessionManager mMediaSessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize MediaSessionManager
        mMediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        // Set up your OnActiveSessionsChangedListener
        mMediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, new ComponentName(this, MyNotificationListenerService.class));
    }

    private MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            // Handle the change in active sessions
            for (MediaController controller : controllers) {
                // Do something with each active media controller
                String packageName = controller.getPackageName();
                MediaController.PlaybackInfo playbackInfo = controller.getPlaybackInfo();
                // Handle playbackInfo or packageName as needed
            }
        }
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Handle notification posted
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Handle notification removed
    }
}
