package com.plivo.endpoint;

import static com.plivo.endpoint.Global.CALL;
import static com.plivo.endpoint.Global.JWT_HEADER;
import static com.plivo.endpoint.Global.LOGIN;
import static com.plivo.endpoint.Global.LOGOUT;
import static com.plivo.endpoint.Global.callUUID;
import static com.plivo.endpoint.Global.xCallUUID;
import static org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpSender;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SipController {


    private static final String TAG = "SipController";
    static PeerConnection temp = null;
    private static SipController controller;

    static {
        try {
            System.loadLibrary("rtcsip_jni");
        } catch (UnsatisfiedLinkError exc) {
            Log.E("rtcsip_jni library not found");
        }
    }

    HashMap<Integer, String> errorCodes = new HashMap<Integer, String>() {{
        put(10001, "INVALID_ACCESS_TOKEN");
        put(10002, "INVALID_ACCESS_TOKEN_HEADER");
        put(10003, "INVALID_ACCESS_TOKEN_ISSUER");
        put(10004, "INVALID_ACCESS_TOKEN_SUBJECT");
        put(10005, "ACCESS_TOKEN_NOT_VALID_YET");
        put(10006, "ACCESS_TOKEN_EXPIRED");
        put(10007, "INVALID_ACCESS_TOKEN_SIGNATURE");
        put(10008, "INVALID_ACCESS_TOKEN_GRANTS");
        put(10009, "EXPIRATION_EXCEEDS_MAX_ALLOWED_TIME");
        put(10010, "MAX_ALLOWED_LOGIN_REACHED");
    }};
    PeerConnection pc;
    PeerConnectionFactory peerConnectionFactory;
    AudioTrack localAudioTrack;
    Boolean isIceGatheringCompleted = false;
    MediaConstraints sdpConstraints;
    MediaStream stream;
    Boolean didSentInvite = false;
    Boolean isIncomingCall = false;
    SignallingStats signallingStats;
    CallInsights callInsights;
    MediaStream remoteStream;
    String username;
    String password;
    String token;
    String certId;
    EventListener eventListener;
    Endpoint endpoint;
    Incoming incoming;
    long maxAverageBitrate = Global.maxAverageBitrate;
    private OnRegistrationEventListener onRegistrationEventListener;
    private OnCallEventListener onCallEventListener;

    private SipController(Endpoint endpoint) {
        this.eventListener = endpoint.eventListener;
        this.endpoint = endpoint;
        checkBitrate(endpoint.getSetupOptions());
        registerListeners();
    }

    public static SipController getInstance(Endpoint endpoint) {
        if (controller == null) {
            controller = new SipController(endpoint);
        }
        return controller;
    }

    public void reset() {
        eventListener = null;
        controller = null;
    }

    public void checkBitrate(HashMap<String, Object> setupOptions) {
        try {
            if (setupOptions.containsKey("maxAverageBitrate") &&
                    !(setupOptions.get("maxAverageBitrate") instanceof String)) {
                long bitrate = Long.parseLong(String.valueOf(
                        setupOptions.get("maxAverageBitrate")));
                if (bitrate >= Global.minAverageBitrate && bitrate <= Global.maxAverageBitrate) {
                    maxAverageBitrate = bitrate;
                } else {
                    Log.E("maxAverageBitrate should be in between "
                            + Global.minAverageBitrate + " and " + Global.maxAverageBitrate);
                }
            }
        } catch (Exception e) {
            Log.E(e.getMessage());
        }
    }

    private void registerListeners() {
        this.registerOnRegistrationEventListener((event, user) -> {
            Log.D("onRegistrationEvent : " + event.name());
            if (event == RegistrationEvent.REGISTERED) {
                endpoint.setRegistered(true);
                endpoint.setJWTStatus();
                Log.D(TAG, "onRegistrationEvent: onLogin() fired");
                eventListener.onLogin();
                Log.syncServerLogs(endpoint.getJwtAccessToken(),endpoint.getLogsUsername());
            } else if (event == RegistrationEvent.NOT_REGISTERED
                    && user.equalsIgnoreCase("logout")) {
                registerTimeOut(endpoint.getRegistrationTimeout());
                endpoint.setRegistered(false);
                eventListener.onLogout();
                endpoint.unregisterReceivers();
                Log.InfoLogs(LOGOUT, "Logout successful");
                Log.syncServerLogs(endpoint.getJwtAccessToken(),endpoint.getLogsUsername());
            } else if (event == RegistrationEvent.NOT_REGISTERED_JWT) {
                String[] splitStr = user.split("@@");
                Log.saveSipMessage(splitStr[1]);
                Log.D("onRegistrationEvent : errorCode " + user);
                registerTimeOut(endpoint.getRegistrationTimeout());
                if (!sendTokenExpired(splitStr[0])) {
                    sendCallback(errorCodes.get(Integer.parseInt(splitStr[0])));
                }
                endpoint.setRegistered(false);
            } else
                eventListener.onLoginFailed();
                Log.saveSipMessage(user);
                if(user.contains("401 Unauthorized")){
                    Log.InfoLogs(LOGIN, "Login failed: Incorrect credential");
                }
        });

        this.registerOnCallEventListener((event, user) -> {
            if (event == CallEvent.TERMINATE_CALL) {
                Log.D("call hanged up : " + user);
                if (incoming != null) {
                    incoming.reset();
                    Log.InfoLogs(CALL, "Incoming call Rejected"+ user);
                    eventListener.onIncomingCallHangup(incoming);
                    incoming.stopSpeechRecognition();
                    isIncomingCall = false;
                    incoming = null;
                } else {
                    Log.InfoLogs(CALL, "Outgoing call Rejected: "+ user);
                    endpoint.getOutgoing().reset();
                    endpoint.getOutgoing().stopSpeechRecognition();
                    eventListener.onOutgoingCallHangup(endpoint.getOutgoing());
                }
                hangupWithoutSummery();
                Log.syncServerLogs(endpoint.getJwtAccessToken(),endpoint.getLogsUsername());
            }
            if (event == CallEvent.CALL_ACCEPTED) {
                Log.D("call accepted");
                if (!isIncomingCall) {
                    Outgoing out = endpoint.getOutgoing();
                    eventListener.onOutgoingCallAnswered(out);
                }
            }
        });
    }

    private void sendCallback(String message) {
        if (endpoint.isLoginWithToken()) {
            endpoint.eventListener.onLogout();
            endpoint.unregisterReceivers();
        } else {
            endpoint.eventListener.onLoginFailed(message);
        }
        endpoint.resetJWTParam(true);
    }

    private boolean sendTokenExpired(String errorCode) {
        if (endpoint.isLoginWithToken() && errorCode.equals("10006")) {
            if (endpoint.accessTokenListener != null) {
                Log.InfoLogs(LOGIN, "Login failed: access token expired generating new token");
                endpoint.accessTokenListener.getAccessToken();
                endpoint.resetJWTParam(false);
                return true;
            }
        }
        return false;
    }

    public synchronized void setRemoteSDP(String remoteSDP) {
        Log.InfoLogs(CALL, "SDP Offer Received "+remoteSDP);
        SessionDescription.Type type = SessionDescription.Type.ANSWER;
        if (pc == null) {
            createLocalPeerConnection();
            type = SessionDescription.Type.OFFER;
        }

        SessionDescription sessionDescription = new SessionDescription(type, remoteSDP);
        pc.setRemoteDescription(new CustomSdpObserver("localSetRemoteDesc"), sessionDescription);

        Log.D(TAG,pc.signalingState().toString());
    }

    String getXCallUUID(String callInfo) {
        String id = "";
        String[] parts = callInfo.split(",");
        for (String s : parts) {
            String[] info = s.split(":");
            if (info[0].equals(Global.xCallUUID)) {
                id = info[1];
                break;
            }
        }
        return id;
    }

    public boolean sendDigits(String digit) {
        Log.InfoLogs(CALL, "send dtmf: "+ digit);
        if (digit.length() > 24) {
            Log.D("Error in sending digits: digits length cannot be more than 24");
            return false;
        }
        RtpSender m_audioSender = null;
        for (RtpSender sender : pc.getSenders()) {
            if (sender.track() != null) {
                String trackType = Objects.requireNonNull(sender.track()).kind();
                if (trackType.equals("audio")) {
                    Log.D(TAG, "Found audio sender.");
                    m_audioSender = sender;
                }
            }
        }
        if (m_audioSender != null) {
            Objects.requireNonNull(m_audioSender.dtmf()).insertDtmf(digit, 100, 500);
            Log.D("Digit Send: " + digit);
            return true;
        }
        return false;
    }

    public synchronized void handleInfoLog(String log) {
        Log.InfoLogs("SipControllerCore", log);
    }

    public synchronized void setCallInfo(String callInfo) {
        String call_uuid = getXCallUUID(callInfo);
        String call_id = getCallID(callInfo);

        if(endpoint.isCallRunning()){
            if(signallingStats == null) signallingStats = new SignallingStats();
            if(!Objects.equals(call_uuid, endpoint.getLastXCallUUID())){
                Log.InfoLogs(CALL, "set call_uuid for the call: " +call_uuid);
            }
            signallingStats.setCallUUID(call_id);
            signallingStats.setXCallUUID(call_uuid);
            endpoint.setLastCallUUID(call_id);
            endpoint.setLastXCallUID(call_uuid);
        }  else {
            endpoint.setLoginCallID(call_id);
        }
        Log.D("**" + xCallUUID + " : "
                + call_uuid + " | " + callUUID + " : " + call_id);
    }

    private String getCallID(String callInfo) {
        String id = null;
        String[] parts = callInfo.split(",");
        for (String s : parts) {
            String[] info = s.split(":");
            if (info[0].equals(Global.callUUID)) {
                id = info[1];
                break;
            }
        }
        return id;
    }

    public synchronized void setCallState(String state, int statusCode) {
        Log.D(TAG, "setCallState: " + state);

        CallState callState;
        try {
            callState = CallState.valueOf(state);
        } catch (IllegalArgumentException e) {
            callState = null;
        }

        TerminatedReason terminateReason;
        try {
            terminateReason = TerminatedReason.valueOf(state);
        } catch (IllegalArgumentException e) {
            terminateReason = null;
        }
        if (isIncomingCall) {
            incomingCallStates(callState, statusCode, terminateReason);
        } else {
            outgoingCallStates(callState, statusCode, terminateReason);
        }
    }

    public void outgoingCallStates(CallState callState, int statusCode,
                                   TerminatedReason terminateReason) {


        Log.InfoLogs(CALL, "Outgoing callStates "+((callState!=null)?callState:"null") +
                " statusCode "+ statusCode + " terminateReason: "+((terminateReason!=null)?terminateReason:"null"));

        if (callState != null) {
            if (statusCode >= 180 && statusCode <= 183) {
                signallingStats.setRingStartTime();
                signallingStats.setPostDialDelay();
                Outgoing out = endpoint.getOutgoing();
                eventListener.onOutgoingCallRinging(out);
                callInsights.sendRingingEvent(signallingStats);
                Log.InfoLogs(CALL,"Outgoing call Ringing");
            }

            if (callState == CallState.CallAccepted) {
                Log.InfoLogs(CALL,"Outgoing call Answered");
                if (signallingStats.getPostDialDelay() == null) {
                    signallingStats.setPostDialDelay();
                }
                signallingStats.setAnswerTime();
                signallingStats.setCallConfirmedTime();
                // send call answerstats
                setCallAnsweredStatus();
                callInsights.sendAnswerEvent(signallingStats, false);
                callInsights.initRTPStats(eventListener, endpoint.getContext());
                callInsights.sendRtpStats(signallingStats, pc);
            }
        }

        if (terminateReason != null) {
            if (statusCode >= 480 && statusCode <= 489) {
                signallingStats.setHangupTime();
                Log.D("@@sendSummaryEvent : outgoing : >=480 && <=489");
                callInsights.sendSummaryEvent(signallingStats);
                callInsights.stopTimer();
                Outgoing out = endpoint.getOutgoing();
                out.setActive(false);
                cleanupCallData();
                Log.InfoLogs(CALL, "Outgoing call rejected/ignored");
                eventListener.onOutgoingCallRejected(out);
                Log.syncServerLogs(endpoint.getJwtAccessToken(),endpoint.getLogsUsername());
            }

            if (statusCode >= 404 && statusCode <= 408) {
                // invalid call
                Log.D("@@sendSummaryEvent : outgoing : >=404 && <=408");
                callInsights.sendSummaryEvent(signallingStats);
                cleanupCallData();
                Outgoing out = endpoint.getOutgoing();
                out.setActive(false);
                eventListener.onOutgoingCallInvalid(out);
            }

            if (statusCode == 503) {
                // invalid call
                Log.D("@@sendSummaryEvent : outgoing : 503");
                callInsights.sendSummaryEvent(signallingStats);
                cleanupCallData();
                Outgoing out = endpoint.getOutgoing();
                out.setActive(false);
                eventListener.onOutgoingCallInvalid(out);
            }

            if (statusCode == 200) {
                signallingStats.setHangupTime();
                Log.D("@@sendSummaryEvent : outgoing : 200");
                callInsights.sendSummaryEvent(signallingStats);
                callInsights.stopTimer();
                cleanupCallData();
            }
        }
    }

    private void setCallAnsweredStatus() {
        if(endpoint!=null && endpoint.isCallRunning()){
            if(endpoint.getOutgoing()!=null){
                endpoint.getOutgoing().isCallAnswered = true;
                return;
            }
            if(endpoint.getIncoming()!=null){
                endpoint.getIncoming().isCallAnswered = true;
            }
        }
    }

    @SuppressLint("NewApi")
    public void incomingCallStates(CallState callState, int statusCode,
                                   TerminatedReason terminateReason) {


        Log.InfoLogs(CALL, "Incoming callSates "+((callState!=null)?callState:"null") +
                " statusCode "+ statusCode + " terminateReason: "+((terminateReason!=null)?terminateReason:"null"));

        if (callState != null) {
            if (statusCode >= 180 && statusCode <= 183) {
                if(signallingStats == null) signallingStats = new SignallingStats();
                signallingStats.setPostDialDelay();
                signallingStats.setCallProgressTime();
                this.sendRinging();
                eventListener.onIncomingCall(incoming);
                callInsights.sendRingingEvent(signallingStats);
            }

            if (callState == CallState.CallAccepted) {
                Log.InfoLogs(CALL, "Incoming call Answered");
                signallingStats.setAnswerTime();
                signallingStats.setCallConfirmedTime();
                setCallAnsweredStatus();
                callInsights.sendAnswerEvent(signallingStats, true);
                callInsights.initRTPStats(eventListener, endpoint.getContext());
                callInsights.sendRtpStats(signallingStats, pc);
            }
        }

        if (terminateReason != null) {
            if (statusCode == 486 || statusCode == 487) {
                signallingStats.setHangupTime();
                Log.D("@@sendSummaryEvent : 486||487");
                callInsights.sendSummaryEvent(signallingStats);
                callInsights.stopTimer();
                incoming.setActive(false);
                Log.InfoLogs(CALL, "Incoming call Rejected 486||487");
                cleanupCallData();

                eventListener.onIncomingCallRejected(incoming);
                Log.syncServerLogs(endpoint.getJwtAccessToken(),endpoint.getLogsUsername());
            }

            if (statusCode == 408) {
                // call invalid
                Log.D("@@sendSummaryEvent : 408");
                callInsights.sendSummaryEvent(signallingStats);
                cleanupCallData();
                incoming.setActive(false);
                eventListener.onIncomingCallInvalid(incoming);
            }

            if (statusCode == 200) {
                signallingStats.setHangupTime();
                Log.D("@@sendSummaryEvent : 200");
                callInsights.sendSummaryEvent(signallingStats);
                callInsights.stopTimer();
                cleanupCallData();
            }

            if (statusCode == 603) {
                signallingStats.setHangupTime();
                Log.D("@@sendSummaryEvent : 603");
                callInsights.sendSummaryEvent(signallingStats);
                callInsights.stopTimer();
                cleanupCallData();
                incoming.setActive(false);
                Log.InfoLogs(CALL, "Incoming call Rejected :603");
                eventListener.onIncomingCallRejected(incoming);
                Log.syncServerLogs(endpoint.getJwtAccessToken(),endpoint.getLogsUsername());
            }
        }
    }

    private void cleanupCallData() {
        signallingStats = null;
    }

    public void createLocalPeerConnection() {
        final ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);

        rtcConfig.continualGatheringPolicy = GATHER_CONTINUALLY;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.NEGOTIATE;
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
//        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(new ArrayList<String>(Arrays.asList("stun:stun.plivo.com:3478", "stun:stun.l.google.com:19302"))).createIceServer();
        iceServers.add(iceServer);
        rtcConfig.iceServers = iceServers;
        pc = peerConnectionFactory.createPeerConnection(rtcConfig,
                new CustomPeerConnectionObserver("localPeerCreation") {
                    @Override
                    public void onIceCandidate(IceCandidate iceCandidate) {
                        super.onIceCandidate(iceCandidate);
                        Log.D("incoming icecandidate check %s", iceCandidate.sdp);
                        try {
                            JSONObject json = new JSONObject();
                            json.put("type", "candidate");
                            json.put("label", iceCandidate.sdpMLineIndex);
                            json.put("id", iceCandidate.sdpMid);
                            json.put("candidate", iceCandidate.sdp);
                            iceCandidate(iceCandidate.sdpMid, iceCandidate.sdp);
                            Log.D(TAG, json.toString());

                            if (!didSentInvite) {
                                new Handler(Looper.getMainLooper()).postDelayed(()
                                        -> iceGatheringFinish(), 1000);
                            }
                            didSentInvite = true;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }


                    @Override
                    public void onAddStream(MediaStream mediaStream) {
                        Log.D("stream added");
                        super.onAddStream(mediaStream);
                        remoteStream = mediaStream;
                    }

                    @Override
                    public void onIceConnectionChange(
                            PeerConnection.IceConnectionState iceConnectionState) {
                        super.onIceConnectionChange(iceConnectionState);
                        Log.D(TAG,"iceconnectionstate: \n"+ iceConnectionState);
                        if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED &&
                                isIncomingCall) {
                            Log.D("incoming call answered listener fired\n");
                            eventListener.onIncomingCallConnected(incoming);
                        }
                    }

                    @Override
                    public void onIceGatheringChange(
                            PeerConnection.IceGatheringState iceGatheringState) {
                        super.onIceGatheringChange(iceGatheringState);
                        if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                            isIceGatheringCompleted = true;
                            Log.D(TAG, pc.signalingState().toString());
                        }

                    }
                });
        addStreamToLocalPeer();
    }

    public void createLocalOffer(String dest, Map<String, String> headers) {
        pc.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                Log.D(TAG, pc.signalingState().toString());
                String localSdp = sessionDescription.description;
                localSdp = localSdp.replace("useinbandfec=1",
                        "useinbandfec=1;maxaveragebitrate=" + maxAverageBitrate);

                pc.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", sessionDescription.type);
                    json.put("sdp", sessionDescription.description);
                    Log.D(TAG,json.toString());
                    try {
                        Log.D("Signalling state before call");
                        Log.D(TAG, pc.signalingState().toString());
                        temp = pc;
                        Log.D(TAG, pc.toString());
                        String[] keys = new String[0];
                        String[] values = new String[0];
                        if (!headers.isEmpty()) {
                            keys = headers.keySet().toArray(new String[0]);
                            values = headers.values().toArray(new String[0]);
                        }
                        makeCall(dest, localSdp, keys, values);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //}
                    Log.D(TAG, json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.InfoLogs(CALL, "SDP Offer created "+localSdp);
            }
        }, sdpConstraints);
    }

    public void addStreamToLocalPeer() {
        pc.addTrack(localAudioTrack);
        Log.D("printing stream");
    }

    public void initializePeerConnection() {
        Log.D("initializing Peer Connection");

        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(
                        endpoint.getContext()).createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory
                .builder().setOptions(options).createPeerConnectionFactory();

        MediaConstraints audioConstraints = new MediaConstraints();
        boolean enableAudio = true;
        stream = peerConnectionFactory.createLocalMediaStream("101");
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
        localAudioTrack.setEnabled(enableAudio);
        stream.addTrack(localAudioTrack);
        sdpConstraints = new MediaConstraints();

        sdpConstraints.mandatory
                .add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory
                .add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "false"));
        sdpConstraints.mandatory
                .add(new MediaConstraints.KeyValuePair("IceRestart", "true"));

    }

    public void doAnswer() {
        if (pc == null) {
            createLocalPeerConnection();
        }
        Log.D(TAG ,"doanswer: " + pc.signalingState());

        pc.createAnswer(new CustomSdpObserver("localCreateAnswer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                pc.setLocalDescription(new CustomSdpObserver("localSetDesc"), sessionDescription);
                Log.D("@@SipController :" +
                        " doAnswer : createAnswer : onCreateSuccess");
                String localSdp = sessionDescription.description;
                localSdp = localSdp.replace("useinbandfec=1",
                        "useinbandfec=1;maxaveragebitrate=" + maxAverageBitrate);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("sdp", sessionDescription.description);
                    Log.D("set local sdp\n%s", sessionDescription.description);
                    answer(localSdp);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.InfoLogs(CALL, "SDP Answer created: "+ localSdp);
            }
        }, sdpConstraints);
    }

    public void reject() {
        this.rejectCall();
        if (signallingStats != null) {
            Log.D("@@sendSummaryEvent : reject");
            callInsights.sendSummaryEvent(signallingStats);
            callInsights.stopTimer();
        }
    }

    public void hangup() {
        if(incoming!=null && incoming.isActive()) {
            Log.InfoLogs(CALL, "Incoming Call Hangup locally");
        }else{
            Log.InfoLogs(CALL, "Outgoing Call Hangup locally");
        }
        if (pc != null) {
            pc.close();
        }
        this.endCall();
        if (signallingStats != null) {
            Log.D("@@sendSummaryEvent : hangup");
            callInsights.sendSummaryEvent(signallingStats);
            callInsights.stopTimer();
        }
        pc = null;
        didSentInvite = false;
    }

    private void hangupWithoutSummery() {
        if (pc != null) {
            pc.close();
        }
        this.endCall();
        pc = null;
        didSentInvite = false;
    }

    public void sendFeedbackEvent(JSONObject feedbackEvent) {
        callInsights.sendFeedbackEvent(feedbackEvent);
    }


    public void relayVoipPushNotification(String username, String password,
                                          String deviceToken, String certificateId,
                                          Map<String, String> push_headers) {
        Log.InfoLogs(CALL, "Incoming call initiated for : "+ username + " with header : "+ push_headers);
        this.username = username;
        this.password = password;
        this.token = deviceToken;
        this.certId = (certificateId == null || certificateId.isEmpty()) ? "" : certificateId;
        Log.D("SipController", " : relayVoipPushNotification : " + push_headers.toString());
        if (endpoint.isCallRunning()) {
            Log.D("SipController", " : relayVoipPushNotification : Call already running");
            return;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Label", push_headers.get("label"));
        headers.put("X-Index", push_headers.get("index"));
        String stirVerification = "Not applicable";
        if (push_headers.containsKey("X-Plivo-Stir-Verification")) {
            stirVerification = push_headers.get("X-Plivo-Stir-Verification");
            headers.put("X-Plivo-Stir-Verification", stirVerification);
        }
        String registrar = push_headers.get("registrar");
        headers.put("X-Plivo-Registrar-Info", registrar);
        String from = push_headers.get("callerID");
        String extraHeaderString = push_headers.get("extraHeaders");

        incoming = new Incoming(from, "",
                (HashMap<String, String>) Utils.stringToMap(extraHeaderString),
                stirVerification,
                this);

        headers.remove("sound");
        headers.remove("message");
        if (endpoint.isLoginTriedWithJWT()) {
            headers.put(JWT_HEADER, endpoint.getJwtAccessToken());
        } else if (TextUtils.isEmpty(password)) {
            Log.D("@@SipController", "registerUserTokenHeaders : loginFailed");
            eventListener.onLoginFailed();
            return;
        }

        if (!username.isEmpty()) {
            Log.D("@@SipController", "registerUserTokenHeaders called");
            String[] keys = new String[0];
            String[] values = new String[0];
            if (!headers.isEmpty()) {
                keys = headers.keySet().toArray(new String[0]);
                values = headers.values().toArray(new String[0]);
            }

            Log.D("@@SipController", "username : " + this.username);
            Log.D("@@SipController", "password : " + this.password);
            if (endpoint.isLoginTriedWithJWT()) {
                this.password = "";
            }
            registerUserTokenHeaders(this.username, this.password, this.token, certId, Global.OUTBOUND_PROXY, keys, values, endpoint.isLoginTriedWithJWT());
            if (peerConnectionFactory == null)
                initializePeerConnection();
        } else {
            Log.D("@@SipController", "registerUserTokenHeaders : loginFailed");
            eventListener.onLoginFailed();
        }
    }

    protected Incoming getIncoming() {
        return this.incoming;
    }


    public void login(String username, String password, String token, String certificateId) {
        this.username = username;
        this.password = password;
        this.token = token;
        this.certId = certificateId;
        registerUserToken(username, password, token, certificateId);
        initializePeerConnection();
    }

    public void loginWithAccessToken(String username, String token, String jwt, String certificateId) {
        this.username = username;
        this.certId = (certificateId == null || certificateId.isEmpty()) ? "" : certificateId;
        Log.D(TAG, "loginWithAccessToken: username: " + username);
        Log.D(TAG, "loginWithAccessToken: username: " + token);
        this.token = token;
        String[] keys = new String[]{JWT_HEADER};
        String[] values = new String[]{jwt};
        registerUserTokenHeaders(this.username, "", this.token, this.certId, Global.OUTBOUND_PROXY, keys, values, true);
        initializePeerConnection();
    }

    public void sendToggleEvent(Boolean isMute, Boolean ishold) {
        callInsights.sendToggleEvent(signallingStats, isMute, ishold);
    }

    public synchronized void onRegistration(RegistrationEvent event, String user) {
        Log.D(TAG, "onRegistration");
        if (onRegistrationEventListener != null) {
            if (event == RegistrationEvent.REGISTERED) {
                endpoint.getSetupOptions().put("maxAverageBitrate", maxAverageBitrate);
                HashMap<String, String> jwtInfo = new HashMap<>();
                if (endpoint.isLoginTriedWithJWT()) {
                    jwtInfo.put("jwt", endpoint.getJwtAccessToken());
                    jwtInfo.put("subAuthID", endpoint.getSub_auth_ID());
                }
                callInsights = new CallInsights(this.username, this.password,
                        Global.DOMAIN, endpoint.getSetupOptions(), jwtInfo);
            }
            onRegistrationEventListener.onRegistrationEvent(event, user);
        }
    }

    public synchronized void onCall(CallEvent event, String user) {
        Log.D(TAG, "onCall");
        if (onCallEventListener != null) {
            if (event == CallEvent.INCOMING_CALL) {
                isIncomingCall = true;
            }
            onCallEventListener.onCallEvent(event, user);
        }
    }

    public synchronized void registerOnRegistrationEventListener(
            OnRegistrationEventListener listener) {
        onRegistrationEventListener = listener;
    }

    public synchronized void registerOnCallEventListener(OnCallEventListener listener) {
        onCallEventListener = listener;
    }

    public native String getPublicIp();

    public native void answer(String sdp);

    public native void rejectCall();

    public native void sendRinging();

    public native void init(ServerSettings serverSettings,boolean isDebug);

    public native void deinit();

    public native void registerUser(String username, String password);

    public native void registerUserToken(String username, String password, String token, String certId);

    public native void registerUserTokenHeaders(String username, String password, String token, String certId, String proxy, String[] headerKeys, String[] headerValues, boolean isLoginWithJWT);

    public native void unregisterUser();

    public native void unregisterUserHeader(String[] headerKeys, String[] headerValues);

    public native void registerTimeOut(int time);

    public native void makeCall(String sipUri, String localSDP, String[] headerKeys,
                                String[] headerValues);

    public native void endCall();

    public native void iceGatheringFinish();

    public native void networkChange();

    public native void networkChangeHeader(String[] headerKeys, String[] headerValues);

    public native void iceCandidate(String mid, String sdp);

    protected enum RegistrationEvent {
        REGISTERED,
        NOT_REGISTERED,
        NOT_REGISTERED_JWT
    }

    protected enum CallEvent {
        INCOMING_CALL,
        TERMINATE_CALL,
        CALL_ACCEPTED
    }

    protected enum TerminatedReason {
        Error,
        Timeout,
        Replaced,
        LocalBye,
        RemoteBye,
        LocalCancel,
        RemoteCancel,
        Rejected,
        Referred
    }

    protected enum CallState {
        EarlyMedia,
        Calling,
        CallAccepted,
        Connecting
    }

    public interface OnRegistrationEventListener {
        void onRegistrationEvent(RegistrationEvent event, String user);
    }

    public interface OnCallEventListener {
        void onCallEvent(CallEvent event, String user);
    }
}

