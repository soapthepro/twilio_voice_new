package com.twilio.twilio_voice;

import android.content.Context;

import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

public class AudioSwitchManager {
    private static AudioSwitch audioSwitch = null;

    private static boolean isActive = false;

    public static synchronized AudioSwitch getInstance(Context context) {
        if (audioSwitch == null) {
            audioSwitch = new AudioSwitch(context.getApplicationContext(), true);
            audioSwitch.start((audioDevices, selectedAudioDevice) -> {
                // Log or handle the list of audio devices and the currently selected device
                return Unit.INSTANCE;
            });
            isActive = true;
        }
        return audioSwitch;
    }

    // Method to get the isActive state
    public static boolean isActive() {
        return isActive;
    }

    public static void closeAudioSwitch() {
        if (isActive && audioSwitch != null) {
            isActive = false;
            audioSwitch.stop();
            audioSwitch = null;
        }
    }

    public static List<String> getAudioDevices() {
        if (isActive && audioSwitch != null) {
            List<AudioDevice> availableDevices = audioSwitch.getAvailableAudioDevices();
            List<String> deviceNames = new ArrayList<>();

            for (AudioDevice device : availableDevices) {
                deviceNames.add(device.getName());
            }
            return deviceNames;
        }
        return null;
    }

    public static void setAudioDevice(AudioDevice device) {
        if (isActive && audioSwitch != null) {
            audioSwitch.deactivate();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            audioSwitch.selectDevice(device);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            audioSwitch.activate();
        }
    }
}
