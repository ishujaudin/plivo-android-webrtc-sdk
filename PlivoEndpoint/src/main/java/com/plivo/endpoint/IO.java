package com.plivo.endpoint;

import static com.plivo.endpoint.Global.CALL;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.webrtc.AudioTrack;
import org.webrtc.RTCStats;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class IO {
    private static final int MAX_DTMF_DIGITS = 24;
    protected String toContact;
    protected String callId;
    protected boolean isOnMute;
    protected boolean isOnHold;
    private boolean isActive;
    protected boolean isCallAnswered;
    private final SipController sipController;
    Timer speechTimer = null;


    public IO(SipController sipController) {
        this.sipController = sipController;
    }

    /**
     * Send DTMF digit
     * @param digit digit to send
     */
    public boolean sendDigits(String digit) {
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(CALL,"Cannot send DTMF digit: no internet");
            return false;
        }
        if (!isActive()) {
            Log.InfoLogs(CALL, "Error in sending digits: there is no active call now" + digit);
            return false;
        }
        if (isOnHold) {
            Log.InfoLogs(CALL, "Error in sending digits: call is on hold." + digit);
            return false;
        }
        if (TextUtils.isEmpty(digit) || digit.length() > MAX_DTMF_DIGITS) {
            Log.InfoLogs(CALL, "Error in sending digits: digits length cannot be more than 24"+ digit);
            return false;
        }

        if (!checkDtmfDigit(digit)) {
            Log.InfoLogs(CALL,"Invalid DTMF digit: " + digit);
            return false;
        }

        try {
            return sipController.sendDigits(digit);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Hang up a call
     */
    public boolean hangup() {
        Log.D("hangup");
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(CALL,"Cannot hangup: no internet");
            return false;
        }
        if (!isActive()) {
            Log.InfoLogs(CALL,"Cannot hangup: Call is not active.");
            return false;
        }

        try {
            sipController.hangup();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("hangup failed");
        }
        return false;
    }

    public boolean mute() {
        Log.D("mute");
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot mute() without an active call.");
            return false;
        }
        if(!isCallAnswered){
            Log.E("Cannot mute() without an answered call.");
            return false;
        }
        if (isOnMute) {
            Log.D("Already on mute");
            return true;
        }

        try {
            for(AudioTrack track: sipController.stream.audioTracks){
                track.setEnabled(false);
            }
            isOnMute = true;
            sipController.sendToggleEvent(true, null);
            startSpeechRecognition();
            Log.D("mute success");
            Log.InfoLogs(CALL, "Call is muted");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("mute failed");
        }

        return false;
    }

    public boolean unmute() {
        Log.D("unmute");
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot unmute() without an active call.");
            return false;
        }
        if(!isCallAnswered){
            Log.E("Cannot unmute() without an answered call.");
            return false;
        }
        if (isOnHold) {
            Log.D("Already on hold.");
            isOnMute = false;
            return true;
        }
        if (!isOnMute) {
            Log.D("Already unmute");
            return true;
        }

        try {
            for(AudioTrack track: sipController.stream.audioTracks){
                track.setEnabled(true);
            }
            isOnMute = false;
            sipController.sendToggleEvent(false, null);
            stopSpeechRecognition();
            Log.D("unmute success");
            Log.InfoLogs(CALL, "Call is unmuted");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("unmute failed");
        }

        return false;
    }

    public boolean hold() {
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot hold() without an active call.");
            return false;
        }
        if (isOnHold) {
            Log.D("Already hold");
            return true;
        }

        try {
            for(AudioTrack track: sipController.remoteStream.audioTracks){
                track.setEnabled(false);
            }

            for(AudioTrack track: sipController.stream.audioTracks){
                track.setEnabled(false);
            }

            isOnHold = true;
            Log.InfoLogs(CALL,"call is hold");
            sipController.sendToggleEvent(null, true);
            Log.D("hold success");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("hold failed");
        }

        return false;
    }

    public boolean unhold() {
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (!isActive()) {
            Log.E("Cannot unhold() without an active call.");
            return false;
        }
        if (!isOnHold) {
            Log.D("already unhold");
            return true;
        }

        try {
            for(AudioTrack track: sipController.remoteStream.audioTracks){
                track.setEnabled(true);
            }

            // only unmute if already on unmute before hold was applied
            if (!isOnMute) {
                for(AudioTrack track: sipController.stream.audioTracks){
                    track.setEnabled(true);
                }
            }
            isOnHold = false;
            Log.InfoLogs(CALL,"call is unhold");
            sipController.sendToggleEvent(null, false);
            Log.D("unhold success");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("unhold failed");
        }

        return false;
    }

    public boolean checkDtmfDigit(String digit) {
        return Utils.VALID_DTMF.contains(digit);
    }

    public void setToContact(String toContact) {
        this.toContact = toContact;
    }

    // To format: <sip:username@phone.plivo.com>
    public String getToContact() {
        return toContact;
    }

    protected boolean isActive() {
        return isActive;
    }

    protected void setActive(boolean active) {
        isActive = active;
    }

    protected void reset() {
        if (isOnMute) {
            unmute();
        }
        if (isOnHold) {
            unhold();
        }
        isActive = false;
    }

    protected void startSpeechRecognition() {
        if (speechTimer != null) {
            Log.InfoLogs(CALL, "speech timer was active before muting the call");
            speechTimer.cancel();
            speechTimer = null;
        }
        speechTimer = new Timer();
        speechTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(isActive()) {
                    sipController.pc.getStats(rtcStatsReport -> {
                        processStats(rtcStatsReport.getStatsMap());
                    });
                }
            }
        }, Global.speechTimer, Global.speechTimer);
    }

    protected void stopSpeechRecognition() {
        if (speechTimer != null) {
            Log.InfoLogs(CALL, "speech timer stopped");
            speechTimer.cancel();
            speechTimer = null;
        }
    }

    protected void processStats(Map<String, RTCStats> statsMap) {
        Double mediaSourceAudioLevel;
        for (RTCStats item : statsMap.values()) {
            if (item.getType().equals("media-source")) {
                mediaSourceAudioLevel = (Double) item.getMembers().get("audioLevel");
                if(sipController.callInsights.getRtpStats() !=null) {
                    double dbValue = sipController.callInsights.getRtpStats().getAudioLevel(mediaSourceAudioLevel);
                    if (dbValue > -40.0) {
                        Log.InfoLogs(CALL, "User trying to speak on mute");
                        //fire the speakingOnMute event on the main thread
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                sipController.endpoint.eventListener.speakingOnMute();
                            }
                        });
                        stopSpeechRecognition();
                    }
                } else {
                    Log.InfoLogs(CALL, "RTP Instance not instantiated yet");
                }
            }
        }
    }
}


