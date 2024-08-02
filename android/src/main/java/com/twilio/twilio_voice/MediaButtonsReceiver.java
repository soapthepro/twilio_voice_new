package com.twilio.twilio_voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButtonsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
            KeyEvent keyEvent = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (keyEvent != null && keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                // Handle the key press here
                Log.d("AyushReceiver", "Key Event Received: " + keyEvent.getKeyCode());
            }
        }
    }
}
