package com.plivo.endpoint;

import android.media.AudioDeviceInfo;

import java.util.HashMap;

public interface EventListener {
    void onLogin();
    void onLogout();
    void onLoginFailed();
    void onLoginFailed(String message);
    /**
     * This event will be fired when there is new incoming call.
     * @param incoming new Incoming call object.
     */
    void onIncomingCall(Incoming incoming);
    void onIncomingCallConnected(Incoming incoming);
    void onIncomingCallHangup(Incoming incoming);
    void onIncomingCallRejected(Incoming incoming);
    void onIncomingCallInvalid(Incoming incoming);

    /**
     * This event will be fired when outgoing call is initiated.
     */
    void onOutgoingCall(Outgoing outgoing);
    void onOutgoingCallRinging(Outgoing outgoing);
    void onOutgoingCallAnswered(Outgoing outgoing);
    void onOutgoingCallRejected(Outgoing outgoing);
    void onOutgoingCallHangup(Outgoing outgoing);
    void onOutgoingCallInvalid(Outgoing outgoing);
    void mediaMetrics(HashMap<String, Object> message);
    void onPermissionDenied(String message);
    void audioDeviceChange(String change, AudioDeviceInfo device);
    void speakingOnMute();
}

