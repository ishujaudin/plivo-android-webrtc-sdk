package com.plivo.endpoint;

import static com.plivo.endpoint.Global.AUDIO_TOGGLE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

public class AudioOutputDeviceChangeReceiver extends BroadcastReceiver {
    private String currentAudioDevice = "";
    private String audioOutputLabels = "";
    private String audioInputLabels = "";
    private String deviceTypes = "";
    private String lastDeviceTypes = "";
    private int noOfDeviceInput = 0;
    private int noOfDeviceOutput = 0;
    private static boolean isFirstTimeCall = true;
    private boolean lastSpeakerStatus = false;
    private boolean currentSpeakerStatus = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            if (!isFirstTimeCall) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    AudioDeviceInfo audioDeviceInfo = getConnectedDeviceName(context);
                    checkToSendEvent(audioDeviceInfo);
                }, 2000);
            } else {
                isFirstTimeCall = false;
                AudioDeviceInfo audioDeviceInfo = getConnectedDeviceName(context);
                if(audioDeviceInfo != null) {
                    lastDeviceTypes = deviceTypes;
                    currentAudioDevice = (String) audioDeviceInfo.getProductName();
                    Log.InfoLogs(AUDIO_TOGGLE, "Audio device set initially " + currentAudioDevice + " type " + deviceTypes);
                }

            }
        }

    }

    protected void checkToSendEvent(AudioDeviceInfo audioDeviceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceInfo!=null) {
            String newDevice = (String) audioDeviceInfo.getProductName();
            if(Endpoint.getInstance() !=null && !Endpoint.getInstance().isCallRunning() && deviceTypes.contains("Device") && !deviceTypes.contains("Bluetooth")) deviceTypes = "Device Speaker";

            if (!Objects.equals(currentAudioDevice, newDevice) || !deviceTypes.equals(lastDeviceTypes)){
                String lastDevice = currentAudioDevice;
                currentAudioDevice = newDevice;
                if(Endpoint.getInstance() !=null && Endpoint.getInstance().isCallRunning()) {
                    sendAudioChangeEvent(audioDeviceInfo);
                    lastDeviceTypes = deviceTypes;
                    return;
                }
                Log.InfoLogs(AUDIO_TOGGLE, "Audio device toggled from "+lastDeviceTypes + " named "+ lastDevice +" to "+ deviceTypes + " named "+ newDevice);
                String changeStatus = getChangeStatus();
                lastDeviceTypes = deviceTypes;
                if(Endpoint.getInstance() !=null)
                    Endpoint.getInstance().eventListener.audioDeviceChange(changeStatus, audioDeviceInfo);

            }
        }
    }

    /**
     * Returns the change status of the device
     * "added" when the new device is connected over "Device Speaker"
     * "removed" when the device is disconnected and new device again becomes "Device Speaker"
     * reference of the device is "Device Speaker"
     * @return change status of the device
     */
    private String getChangeStatus() {
        return (Objects.equals(deviceTypes, "Device Speaker") && !Objects.equals(lastDeviceTypes, "Device Speaker")) ? "removed" : "Added";
    }

    private void sendAudioChangeEvent(AudioDeviceInfo audioDeviceInfo) {
        try {
            if (Endpoint.getInstance().isCallRunning()  && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Endpoint endpoint = Endpoint.getInstance();
                JSONObject audioDeviceInfoJson = new JSONObject();
                if(audioDeviceInfo == null) {
                    audioDeviceInfoJson.put("audioDeviceInfo", "DeviceSpeaker");
                }else {
                    audioDeviceInfoJson.put("audioDeviceInfo", getAudioDeviceInfoJSON(audioDeviceInfo));
                }
                if(endpoint.sipController.callInsights !=null){
                    endpoint.sipController.callInsights.sendAudioDeviceChangeToggleEvent(endpoint.sipController.signallingStats,audioDeviceInfoJson );
                }
            }
        } catch (JSONException e){
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private JSONObject getAudioDeviceInfoJSON(AudioDeviceInfo audioDeviceInfo) throws JSONException {
        JSONObject audioDeviceInfoJson = new JSONObject();
        audioDeviceInfoJson.put("deviceName", audioDeviceInfo.getProductName());
        audioDeviceInfoJson.put("deviceTypes", deviceTypes);
        audioDeviceInfoJson.put("deviceID", audioDeviceInfo.getId());
        audioDeviceInfoJson.put("deviceSampleRate", Arrays.toString(audioDeviceInfo.getSampleRates()));
        audioDeviceInfoJson.put("noOfDeviceInput",noOfDeviceInput);
        audioDeviceInfoJson.put("noOfDeviceOutput",noOfDeviceOutput);
        audioDeviceInfoJson.put("audioOutputLables", audioOutputLabels);
        audioDeviceInfoJson.put("audioInputLables", audioInputLabels);
        return audioDeviceInfoJson;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    protected AudioDeviceInfo getConnectedDeviceName(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioDeviceOutputList = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        audioOutputLabels = getAudioOutputLabels(audioDeviceOutputList);
        noOfDeviceOutput = audioDeviceOutputList.length;
        if (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn()) {
            deviceTypes = "Bluetooth Device";
            return getAudioDeviceInfo(context,new ArrayList<>(Arrays.asList(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO )));
        }
        if (audioManager.isWiredHeadsetOn()) {
            deviceTypes = "Wired Headset";
            return getAudioDeviceInfo(context,new ArrayList<>(Arrays.asList(AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)));
        }
        currentSpeakerStatus = audioManager.isSpeakerphoneOn();
        if (currentSpeakerStatus != lastSpeakerStatus) {
            lastSpeakerStatus = currentSpeakerStatus;
            if(currentSpeakerStatus) {
                deviceTypes = "Device Speaker";
                return getAudioDeviceInfo(context,new ArrayList<>(Collections.singletonList(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)));

            }else {
                deviceTypes = "Device Built-In Mic/Earpiece";
                return getAudioDeviceInfo(context,new ArrayList<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_MIC,AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)));

            }
        }
        deviceTypes = "Device Built-In Mic/Earpiece";
        return getAudioDeviceInfo(context,new ArrayList<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_MIC,AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    protected AudioDeviceInfo getConnectedDeviceForAPILessThen29(Context context, ArrayList<Integer> deviceType, boolean currentSpeakerStatus) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] audioDeviceOutputList = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        audioOutputLabels = getAudioOutputLabels(audioDeviceOutputList);
        if(currentSpeakerStatus) {
            deviceTypes = "Device Speaker";
        }else {
            deviceTypes = "Device Built-In Mic/Earpiece";
        }
        return getAudioDeviceInfo(context,deviceType);
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    private String getAudioOutputLabels(AudioDeviceInfo[] audioDeviceOutputList) {
        int count =0;
        StringBuilder labels = new StringBuilder();
        for(AudioDeviceInfo dev : audioDeviceOutputList){
            labels.append(dev.getProductName());
            labels.append(" ");
            count++;
        }
        noOfDeviceOutput = count;
        return labels.toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private AudioDeviceInfo getAudioDeviceInfo(Context context, ArrayList<Integer> deviceTypeList) {
        AudioManager audioManager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        audioInputLabels = getAudioInputLabels(devices);
        for (AudioDeviceInfo device : devices) {
            int type = device.getType();
            if (deviceTypeList.contains(type)) {
                return device;
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String getAudioInputLabels(AudioDeviceInfo[] devices) {
        int count  = 0;
        StringBuilder labels = new StringBuilder();
        for(AudioDeviceInfo dev : devices){
            labels.append(dev.getProductName());
            labels.append(" ");
            count++;
        }
        noOfDeviceInput = count;
        return labels.toString();
    }

    public void checkIfDeviceIsOnSpeaker() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioManager audioManager = (AudioManager) Endpoint.getInstance().getContext().getSystemService(Context.AUDIO_SERVICE);
            currentSpeakerStatus = audioManager.isSpeakerphoneOn();
            if (currentSpeakerStatus != lastSpeakerStatus) {
                lastSpeakerStatus = currentSpeakerStatus;
                AudioDeviceInfo audioDeviceInfo = getConnectedDeviceForAPILessThen29(Endpoint.getInstance().getContext(),new ArrayList<>(Arrays.asList(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)),currentSpeakerStatus);
                sendAudioChangeEvent(audioDeviceInfo);
            }
        }
    }
}
