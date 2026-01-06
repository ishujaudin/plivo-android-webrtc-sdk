package com.plivo.endpoint;

import static com.plivo.endpoint.Global.SDK_NAME;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.plivo.endpoint.java_websocket.client.WebSocketClient;
import com.plivo.endpoint.java_websocket.handshake.ServerHandshake;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;


public class CallInsights {
    private static final String TAG = "CallInsights";
    private final String ANSWERED_EVENT = "CALL_ANSWERED";
    private final String TOGGLE_MUTE_EVENT = "TOGGLE_MUTE";
    private final String TOGGLE_HOLD_EVENT = "TOGGLE_HOLD";
    private final String AUDIO_DEVICE_TOGGLE = "AUDIO_DEVICE_TOGGLE_EVENT";
    private final String SUMMARY_EVENT = "CALL_SUMMARY";
    private final String FEEDBACK_EVENT = "FEEDBACK";
    private final String CALL_STATS = "CALL_STATS";
    private final String VERSION = "v1";

    private static final String TOGGLE_MUTE = "mute";
    private static final String TOGGLE_UNMUTE = "unmute";
    private static final String TOGGLE_HOLD = "hold";
    private static final String TOGGLE_UNHOLD = "unhold";
    private static final String ANSWERED_OUT_EVENT_INFO = "Outgoing call answered";
    private static final String ANSWERED_IN_EVENT_INFO = "Incoming call answered";

    private static final String CALL_RINGING = "CALL_RINGING";

    private final String STATS_SOURCE = "AndroidSDK";
    private final String[] CLIENT_VERSION = {"1", "2", "3"};

    private final String CLIENT_VERSION_MAJOR = CLIENT_VERSION[0];
    private final String CLIENT_VERSION_MINOR = CLIENT_VERSION[1];
    private final String CLIENT_VERSION_PATCH = CLIENT_VERSION[2];

    private final String OS_VERSION = "Android " + android.os.Build.VERSION.SDK_INT;

    private final String ARCH = System.getProperty("os.arch");
    private final String endpointName;
    private final String endpointDomain;
    private final ArrayList<JSONObject> statBuffer = new ArrayList<>();
    private WebSocketClient mWebSocketClient;
    private String callInsightsKey;
    private Timer timer;
    private Boolean rtpFlagEnabled;
    private RtpStats rtpStat;
    private Options option;
    private boolean isSpeakerOn;

    public CallInsights(String username, String password, String domain,
                        HashMap<String, Object> setupOptions, HashMap<String, String> jwtInfo) {
        endpointName = username;
        endpointDomain = domain;
        initOptions(setupOptions);
        getCallStatsKey(username, password, domain, jwtInfo);
    }

    public void initRTPStats(Context context) {
        rtpStat = new RtpStats(option.isEnableMediaMetrics(), context);
    }

    public void initRTPStats(EventListener eventListener, Context context) {
        rtpStat = new RtpStats(eventListener, option.isEnableMediaMetrics(), context);
    }

    RtpStats getRtpStats() {
        return rtpStat;
    }

    public void initOptions(HashMap<String, Object> setupOptions) {
        option = new Options(setupOptions);
    }

    Options getOptions() {
        return option;
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    private void sendStats(final JSONObject stats) {
        Log.D(TAG, "sendStats");
        try {
            if (stats.getString("msg").equals(SUMMARY_EVENT) ||
                    stats.getString("msg").equals(ANSWERED_EVENT) ||
                    stats.getString("msg").equals(FEEDBACK_EVENT) ||
                    stats.getString("msg").equals(TOGGLE_MUTE_EVENT) ||
                    stats.getString("msg").equals(TOGGLE_HOLD_EVENT) ||
                    stats.getString("msg").equals(AUDIO_DEVICE_TOGGLE) ||
                    statBuffer.size() >= Global.rtpBatchSize) {
                if (mWebSocketClient != null && mWebSocketClient.isOpen()) {
                    for (JSONObject stat : statBuffer) {
                        mWebSocketClient.send(stat.toString());
                        Log.D(TAG,"Call insights Websocket stats sent 1st" + stats);
                    }
                    statBuffer.clear();
                    return;
                }

                URI uri;
                try {
                    uri = new URI(Global.statsWSURL);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    return;
                }

                mWebSocketClient = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake serverHandshake) {
                        Log.D(TAG, "Call insights  Websocket Opened");
                        for (JSONObject stat : statBuffer) {
                            mWebSocketClient.send(stat.toString());
                            Log.D(TAG, "Call insights Websocket stats sent 2nd" + stats);
                        }
                        statBuffer.clear();
                    }

                    @Override
                    public void onMessage(String s) {
                    }

                    @Override
                    public void onClose(int i, String s, boolean b) {
                        Log.D(TAG, "Call insights Websockets Closed" + s);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.D(TAG, "Call insights Websockets Error " + e.getMessage());
                    }
                };
                Log.D(TAG, "websockets URI Connect");
                mWebSocketClient.connectBlocking();
            }
        } catch (JSONException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void checkIfDeviceIsOnSpeaker() {
        Endpoint.getInstance().audioOutputDeviceChangeReceiver.checkIfDeviceIsOnSpeaker();
    }

    private JSONObject getBasicStatsInfo(SignallingStats stats) {
        JSONObject event = new JSONObject();
        try {
            String USER_AGENT = SDK_NAME + " " + Global.VERSION;
            event.put("userAgent", USER_AGENT);
            event.put("sdkVersionMajor", CLIENT_VERSION_MAJOR);
            event.put("sdkVersionMinor", CLIENT_VERSION_MINOR);
            event.put("sdkVersionPatch", CLIENT_VERSION_PATCH);
            event.put("clientName", "Android");
            event.put("deviceOs", OS_VERSION);
            event.put("devicePlatform", ARCH);
            event.put("domain", endpointDomain);
            event.put("sdkName", SDK_NAME);
            event.put("source", STATS_SOURCE);
            event.put("version", "v1");
            event.put("timeStamp", Utils.getCurrentTimeInMilliSeconds());
            event.put("username", endpointName);
            event.put("callstats_key", callInsightsKey);
            event.put("corelationId", stats.getCallUUID());
            event.put("callUUID", stats.getCallUUID());
            event.put("xcallUUID", stats.getXCallUUID());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return event;
    }

    @SuppressLint("NewApi")
    private void getCallStatsKey(String username, String password, String domain,
                                 HashMap<String, String> jwtInfo) {
        JSONObject postBody = new JSONObject();
        String url = "";
        try {
            if (jwtInfo.size() == 0 || jwtInfo.get("jwt") == null ||
                    Objects.equals(jwtInfo.get("jwt"), "")) {
                url = Global.statsKeyURL;
                postBody.put("username", username);
                postBody.put("password", password);
                postBody.put("domain", domain);
            } else {
                url = Global.STATS_API_URL_ACCESS_TOKEN;
                postBody.put("jwt", jwtInfo.get("jwt"));
                if (jwtInfo.get("subAuthID") != null &&
                        !Objects.requireNonNull(jwtInfo.get("subAuthID")).isEmpty() &&
                        Objects.requireNonNull(jwtInfo.get("subAuthID")).contains("puser")) {
                    postBody.put("from", jwtInfo.get("subAuthID"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        HttpPostTask client1 = new HttpPostTask(postBody, "POST", new HTTPRequestCallback() {
            @Override
            public void onFailure(int StatusCode) {
                Log.InfoLogs(Global.LOGIN, "Call Stats key generation failed:- statusCode "+ StatusCode);
            }

            @Override
            public void onResponse(String response) {
                Log.D(TAG,"Successful call insights response." + response);
                if (response.equals("")) {
                    Log.D(TAG, "Call insights is not activated.");
                    return;
                }
                try {
                    Log.D(TAG, "GetCallStatsKeyResponse : " + response);
                    JSONObject jsonResponse = new JSONObject(response);
                    callInsightsKey = jsonResponse.getString("data");
                    rtpFlagEnabled = jsonResponse.getBoolean("is_rtp_enabled");
                    Log.InfoLogs(Global.LOGIN, "Call Stats key generated:- "+ callInsightsKey);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        client1.execute(url);
    }

    @SuppressLint("NewApi")
    public void sendAnswerEvent(SignallingStats stats, Boolean isIncoming) {
        Log.D(TAG, "sendAnswerEvent");
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        JSONObject answerEvent = getBasicStatsInfo(stats);
        try {
            answerEvent.put("msg", ANSWERED_EVENT);
            answerEvent.put("setupOptions", option.getOptions().toString());
            if (isIncoming) {
                answerEvent.put("info", ANSWERED_IN_EVENT_INFO);
            } else {
                answerEvent.put("info", ANSWERED_OUT_EVENT_INFO);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(answerEvent);
        sendStats(answerEvent);
    }

    public void sendToggleEvent(SignallingStats stats, Boolean isMute, Boolean isHold) {
        System.out.println("@@SipControllerCore : sendToggleEvent");
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        JSONObject toggleEvent = getBasicStatsInfo(stats);
        try {
            toggleEvent.put("setupOptions", option.getOptions().toString());
            if (isMute != null) {
                toggleEvent.put("msg", TOGGLE_MUTE_EVENT);
                toggleEvent.put("action", isMute ? TOGGLE_MUTE : TOGGLE_UNMUTE);
            }
            if (isHold != null) {
                toggleEvent.put("msg", TOGGLE_HOLD_EVENT);
                toggleEvent.put("action", isHold ? TOGGLE_HOLD : TOGGLE_UNHOLD);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(toggleEvent);
        sendStats(toggleEvent);
    }

    public void sendAudioDeviceChangeToggleEvent(SignallingStats stats, JSONObject audioDeviceInfoJson) {
        System.out.println("@@SipControllerCore : sendAudioDeviceChangeToggleEvent");
        if (callInsightsKey == null || callInsightsKey.isEmpty() || stats == null) return;
        JSONObject audioDeviceChangeEvent = getBasicStatsInfo(stats);
        try {
            audioDeviceChangeEvent.put("setupOptions", option.getOptions().toString());
            audioDeviceChangeEvent.put("msg", AUDIO_DEVICE_TOGGLE);
            audioDeviceChangeEvent.put("audioDeviceInfo", audioDeviceInfoJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(audioDeviceChangeEvent);
        sendStats(audioDeviceChangeEvent);
    }

    ArrayList<JSONObject> getStatBuffer() {
        return statBuffer;
    }

    @RequiresApi(api = Build.VERSION_CODES.GINGERBREAD)
    public void sendSummaryEvent(SignallingStats stats) {
        Log.D(TAG, "sendSummaryEvent " + stats.getXCallUUID());
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        JSONObject summaryEvent = getBasicStatsInfo(stats);
        try {
            summaryEvent.put("msg", SUMMARY_EVENT);
            summaryEvent.put("signalling", stats.getSignallingData());

            summaryEvent.put("setupOptions", option.getOptions().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(summaryEvent);
        sendStats(summaryEvent);
    }

    public void sendRingingEvent(SignallingStats stats) {
        Log.D(TAG, "@@sendRingingEvent :" + stats.getXCallUUID());
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        JSONObject ringingEvent = getBasicStatsInfo(stats);
        try {
            ringingEvent.put("msg", CALL_RINGING);
            ringingEvent.put("signalling", stats.getSignallingData());

            ringingEvent.put("setupOptions", option.getOptions().toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(ringingEvent);
        sendStats(ringingEvent);
    }

    @SuppressLint("NewApi")
    public void sendFeedbackEvent(JSONObject feedbackEvent) {
        if (callInsightsKey == null || callInsightsKey.isEmpty()) return;
        try {
            feedbackEvent.put("callstats_key", callInsightsKey);
            feedbackEvent.put("domain", endpointDomain);
            feedbackEvent.put("msg", FEEDBACK_EVENT);
            feedbackEvent.put("source", STATS_SOURCE);
            feedbackEvent.put("sdkVersion", Global.VERSION);
            feedbackEvent.put("timeStamp", Utils.getCurrentTimeInMilliSeconds());
            feedbackEvent.put("userName", endpointName);
            feedbackEvent.put("version", VERSION);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        statBuffer.add(feedbackEvent);
        sendStats(feedbackEvent);
    }

    @SuppressLint("NewApi")
    public void sendRtpStats(SignallingStats stats, PeerConnection pc) {
        Log.D(TAG, "CallInsights : sendRtpStats : enableTracking " + option.isEnableTacking());
        if ((callInsightsKey == null || callInsightsKey.isEmpty()) && !option.isEnableTacking())
            return;
        Log.D(TAG, "rtpFlagEnabled : " + rtpFlagEnabled + "\t" + "EnableTracking : " +
                option.isEnableTacking());
        if (!option.isEnableTacking()) {
            Log.D(TAG, "EnableTracking Flag is not set to True");
            return;
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                pc.getStats(rtcStatsReport -> {
                    JSONObject rtpStats = rtpStat.computeRTPStats(rtcStatsReport.getStatsMap());
                    try {
                        rtpStats.put("msg", CALL_STATS);
                        rtpStats.put("callstats_key", callInsightsKey);
                        rtpStats.put("corelationId", stats.getCallUUID());
                        rtpStats.put("callUUID", stats.getCallUUID());
                        rtpStats.put("xcallUUID", stats.getXCallUUID());
                        rtpStats.put("username", endpointName);
                        rtpStats.put("source", STATS_SOURCE);
                        rtpStats.put("timeStamp", Utils.getCurrentTimeInMilliSeconds());
                        rtpStats.put("domain", endpointDomain);
                        rtpStats.put("version", VERSION);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (rtpFlagEnabled == null || !rtpFlagEnabled) {
                        Log.D(TAG, "RTP Flag is not enabled");
                    } else {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            checkIfDeviceIsOnSpeaker();
                        }
                        statBuffer.add(rtpStats);
                        sendStats(rtpStats);
                    }
                });

            }
        }, Global.firstRTPCall, Global.rtpColectionFequency);

    }

    @SuppressLint("NewApi")
    public void stopTimer() {
        if ((callInsightsKey == null || callInsightsKey.isEmpty()) && !option.isEnableTacking())
            return;
        if (this.mWebSocketClient != null && this.mWebSocketClient.isOpen()) {
            this.mWebSocketClient.close();
        }
        if (this.timer != null) {
            this.timer.cancel();
            Log.D(TAG, "Timer stopped");
        }
    }

    void setStatsKey(String key) {
        callInsightsKey = key;
    }
}
