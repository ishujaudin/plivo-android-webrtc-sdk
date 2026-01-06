package com.plivo.endpoint;

import static java.lang.Math.log10;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.RTCStats;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class RtpStats {
    private static final String TAG = "RtpStats";
    private Context context;
    private final HashMap<String, ArrayList<Double>> mediaMetricMap;
    private final HashMap<String, Boolean> mediaWarning;
    private final EventListener eventListener;
    ArrayList<Double> jitterLocalList, jitterRemoteList, rttList, mosList, packetLossLocalList, packetLossRemoteList, audioLevelLocalList, audioLevelRemoteList, microphoneAccess;
    String codec;
    JSONObject rtp_stats_config;
    private final boolean enableMediaMetrics;

    public RtpStats(boolean enableMediaMetrics, Context context) {
        this(null, enableMediaMetrics, context);
    }

    public RtpStats(com.plivo.endpoint.EventListener eventListener, boolean enableMediaMetrics, Context context) {
        this.context = context;
        this.enableMediaMetrics = enableMediaMetrics;
        mediaMetricMap = new HashMap<>();
        mediaWarning = new HashMap<>();
        jitterLocalList = new ArrayList<>();
        jitterRemoteList = new ArrayList<>();
        rttList = new ArrayList<>();
        mosList = new ArrayList<>();
        packetLossLocalList = new ArrayList<>();
        packetLossRemoteList = new ArrayList<>();
        audioLevelLocalList = new ArrayList<>();
        audioLevelRemoteList = new ArrayList<>();
        mediaMetricMap.put("jitterLocalMeasures", jitterLocalList);
        mediaMetricMap.put("jitterRemoteMeasures", jitterRemoteList);
        mediaMetricMap.put("rtt", rttList);
        mediaMetricMap.put("mos", mosList);
        mediaMetricMap.put("packetLossLocalMeasures", packetLossLocalList);
        mediaMetricMap.put("packetLossRemoteMeasures", packetLossRemoteList);
        mediaMetricMap.put("audioLevelLocalMeasures", audioLevelLocalList);
        mediaMetricMap.put("audioLevelRemoteMeasures", audioLevelRemoteList);
        mediaMetricMap.put("microphoneAccess", microphoneAccess);

        mediaWarning.put("jitterLocalMeasures", false);
        mediaWarning.put("jitterRemoteMeasures", false);
        mediaWarning.put("rtt", false);
        mediaWarning.put("mos", false);
        mediaWarning.put("packetLossLocalMeasures", false);
        mediaWarning.put("packetLossRemoteMeasures", false);
        mediaWarning.put("audioLevelLocalMeasures", false);
        mediaWarning.put("audioLevelRemoteMeasures", false);
        mediaWarning.put("microphoneAccess", false);
        codec = "PCMU";
        this.eventListener = eventListener;
        initStatsConfig();
    }

    private void initStatsConfig() {
        rtp_stats_config = new JSONObject();
        try {
            rtp_stats_config.put("localFractionLoss", 0.0);
            rtp_stats_config.put("remoteFractionLoss", 0.0);
            rtp_stats_config.put("localPacketsLost", 0.0);
            rtp_stats_config.put("localPacketsSent", 0.0);
            rtp_stats_config.put("remotePacketsLost", 0.0);
            rtp_stats_config.put("remotePacketsReceived", 0.0);
            rtp_stats_config.put("prePacketsReceived", 0.0);
            rtp_stats_config.put("prePacketsSent", 0.0);
            rtp_stats_config.put("preRemotePacketsLoss", 0.0);
            rtp_stats_config.put("preLocalPacketsLoss", 0.0);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getNetworkType() {
        if (context != null) {
            if (PackageManager.PERMISSION_DENIED != context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_WIFI_STATE)) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected())
                    return "unknown"; //not connected
                if (info.getType() == ConnectivityManager.TYPE_WIFI)
                    return "wifi";
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return "mobile";
                }
            } else {
                Log.D("Currently network permissions are not allowed");
            }
        }
        return "unknown";
    }


    public String getNetworkEffectiveType() {
        return "unknown";
    }

    public Integer getNetworkDownlinkSpeed() {
        if (getNetworkType().equals("mobile")) {
            return -1;
        }
        if (context != null) {
            if (PackageManager.PERMISSION_DENIED != context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_WIFI_STATE)) {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                int linkSpeed = wifiManager.getConnectionInfo().getRssi();
                return WifiManager.calculateSignalLevel(linkSpeed, 5);
            } else {
                Log.D("Currently network permissions are not allowed");
            }
        }
        return -1;
    }

    double getMOS(double rtt, double jitter) {
        double fractionLoss = 0.0;
        double R;
        double MOS;
        double RValue;

        if ("opus".equals(codec)) {
            RValue = 95.0;
        } else {
            RValue = 93.2;
        }

        double effectiveLatency = (rtt) / 2000.0 + (jitter * 2) + 10;

        if (effectiveLatency < 160) {
            R = RValue - (effectiveLatency / 40);
        } else {
            R = RValue - (effectiveLatency - 120) / 10;
        }
        R = R - (fractionLoss * 2.5);
        if (R <= 0) {
            MOS = 1;
        } else if (R > 100) {
            MOS = 4.5;
        } else {
            MOS = 1 + 0.035 * R + 7.10 / 1000000 * R * (R - 60) * (100 - R);
        }
        return MOS;
    }

    double calculateFractionLoss(int packetsLost, int packetsSent, String type) {
        double fractionLost;
        if (packetsLost == 0 && packetsSent == 0) {
            fractionLost = 0.0;
        } else if (packetsSent == 0) {
            fractionLost = 1.0;
        } else {
            fractionLost = (double) packetsLost / packetsSent;
        }
        try {
            if ("local".equals(type)) {
                rtp_stats_config.put("packetsSent", packetsSent);
                rtp_stats_config.put("packetsLost", packetsLost);
            } else {
                rtp_stats_config.put("prePacketsReceived", packetsSent);
                rtp_stats_config.put("preRemotePacketsLoss", packetsLost);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return fractionLost;
    }

    private void basePackets(Map<String, RTCStats> streamStat) {
        if (streamStat.get("remote-rtp") instanceof Map) {
            Map<String, Integer> report = (Map<String, Integer>) streamStat.get("remote-rtp");
            if (report != null) {
                Integer received = report.get("packetsReceived");
                Integer lost = report.get("packetsLost");
                try {
                    rtp_stats_config.put("prePacketsReceived", received);
                    rtp_stats_config.put("preRemotePacketsLoss", lost);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        if (streamStat.get("local-rtp") instanceof Map) {
            Map<String, Integer> localRtp = (Map<String, Integer>) streamStat.get("local-rtp");
            if (localRtp != null) {
                Integer packetsSent = localRtp.get("packetsSent");
                Integer packetsLost = localRtp.get("packetsLost");
                try {
                    rtp_stats_config.put("packetsSent", packetsSent != null ? packetsSent : 0);
                    rtp_stats_config.put("packetsLost", packetsLost != null ? packetsLost : 0);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public double getAudioLevel(double audioLevelAmplitude) {
        Double audioLevelDecibles = -100.0;
        if (audioLevelAmplitude > 0.0) {
            audioLevelDecibles = 20 * log10(audioLevelAmplitude);
        }
        if (audioLevelDecibles.isNaN()) {
            return 0.0;
        } else if (audioLevelDecibles < -100.0) {
            return -100;
        } else {
            DecimalFormat df = new DecimalFormat("###.###");
            return df.format(audioLevelDecibles) != null ? Double.parseDouble(df.format(audioLevelDecibles)) : 0.0;
        }
    }

    private boolean isCallOnMute() {
        return Endpoint.getInstance().isCallRunning() &&
                ((Endpoint.getInstance().getIncoming()!=null && Endpoint.getInstance().getIncoming().isOnMute) ||
                        (Endpoint.getInstance().getOutgoing()!=null && Endpoint.getInstance().getOutgoing().isOnMute));
    }

    public void printMediaMetric(JSONObject localStats, JSONObject remoteStats) {
        try {
            callMediaMatrices("rtt", localStats.getDouble("rtt"), "high_rtt", "high latency detected, can result delay in audio", null);
            callMediaMatrices("mos", localStats.getDouble("mos"), "low_mos", "low Mean Opinion Score (MOS)", null);
            callMediaMatrices("jitterLocalMeasures", localStats.getDouble("jitter"), "high_jitter", "high jitter detected due to network congestion, can result in audio quality problems", "local");
            callMediaMatrices("jitterRemoteMeasures", remoteStats.getDouble("jitter"), "high_jitter", "high jitter detected due to network congestion, can result in audio quality problems", "remote");
            callMediaMatrices("packetLossLocalMeasures", localStats.getDouble("fractionLoss"), "high_packetloss", "high packet loss is detected on media stream, can result in choppy audio or dropped call", "local");
            callMediaMatrices("packetLossRemoteMeasures", remoteStats.getDouble("fractionLoss"), "high_packetloss", "high packet loss is detected on media stream, can result in choppy audio or dropped call", "remote");
            processAudioLevels("audioLevelLocalMeasures", localStats.getDouble("audioLevel"), "no_audio_received", "no audio packets received", "local");
            processAudioLevels("audioLevelRemoteMeasures", remoteStats.getDouble("audioLevel"), "no_audio_received", "no audio packets received", "remote");
            checkMicrophoneAccess("microphoneAccess", localStats.getInt("bytesSent"), localStats.getDouble("audioLevel"), "no_microphone_access", "Access to microphone not given", null);
        } catch (JSONException exception) {
            exception.printStackTrace();
        }
    }

    public JSONObject getLocalStats(Map<String, Object> localStats, Double audioMediaLevel) {

        double MOSLocal;
        double rtt = 0.0;
        double bytesSent = 0;
        double audioLevel;
        if(isCallOnMute()){
            audioLevel = -100.0;
        } else {
            audioLevel = getAudioLevel(audioMediaLevel);
        }
        Log.D("@@RtpStats : getLocalStats : AudioLevel : " + audioLevel);
        double jitter = 0.0;
        long src = 0;
        int packetsLost = 0;
        int packetsSent = 0;

        for (String stat : localStats.keySet()) {
            switch (stat) {
                case "bytesSent":
                    bytesSent = Double.parseDouble(String.valueOf(localStats.get(stat)));
                    break;
                case "googRtt":
                    rtt = Double.parseDouble(String.valueOf(localStats.get(stat)));
                    break;
                case "googJitterReceived":
                    jitter = Double.parseDouble(String.valueOf(localStats.get(stat)));
                    break;
                case "packetsLost":
                    packetsLost = Integer.parseInt(String.valueOf(localStats.get(stat)));
                    break;
                case "packetsSent":
                    packetsSent = Integer.parseInt(String.valueOf(localStats.get(stat)));
                    break;
                case "ssrc":
                    src = Long.parseLong(String.valueOf(localStats.get(stat)));
                    break;
                default:
                    break;
            }
        }

        MOSLocal = getMOS(rtt, jitter);
        double localFractionLoss = calculateFractionLoss(packetsLost, packetsSent, "local");
        try {
            rtp_stats_config.put("localFractionLoss", localFractionLoss);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JSONObject localStatsDict = new JSONObject();
        try {
            localStatsDict.put("audioLevel", audioLevel);
            localStatsDict.put("bytesSent", bytesSent);
            localStatsDict.put("fractionLoss", localFractionLoss);
            localStatsDict.put("fractionLoss", rtp_stats_config.getDouble("localFractionLoss"));
            localStatsDict.put("rtt", rtt);
            localStatsDict.put("mos", MOSLocal);
            localStatsDict.put("jitter", new DecimalFormat("###.###").format(jitter));
            localStatsDict.put("packetsLost", packetsLost);
            localStatsDict.put("packetsSent", packetsSent);
            localStatsDict.put("ssrc", src);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return localStatsDict;
    }

    public JSONObject getRemoteStats(Map<String, Object> remoteStats) {
        double audioLevel = 0.0;
        double bytesReceived = 0;
        double jitter = 0.0;
        long src = 0;
        int packetsLost = 0;
        int packetsReceived = 0;

        for (String stat : remoteStats.keySet()) {
            switch (stat) {
                case "audioLevel":
                    double aLevel = Double.parseDouble(String.valueOf(remoteStats.get(stat)));
                    audioLevel = getAudioLevel(aLevel / 40);
                    Log.D("@@RtpStats : getRemoteStats : AudioLevel : " + audioLevel);
                    break;
                case "bytesReceived":
                    bytesReceived = Double.parseDouble(String.valueOf(remoteStats.get(stat)));
                    break;
                case "googJitterReceived":
                    jitter = Double.parseDouble(String.valueOf(remoteStats.get(stat)));
                    break;
                case "packetsLost":
                    packetsLost = Integer.parseInt(String.valueOf(remoteStats.get(stat)));
                    break;
                case "packetsReceived":
                    packetsReceived = Integer.parseInt(String.valueOf(remoteStats.get(stat)));
                    break;
                case "ssrc":
                    src = Long.parseLong(String.valueOf(remoteStats.get(stat)));
                    break;
                default:
                    break;
            }
        }

        double localFractionLoss = calculateFractionLoss(packetsLost, packetsReceived, "remote");
        try {
            rtp_stats_config.put("localFractionLoss", localFractionLoss);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JSONObject remoteStatsDict = new JSONObject();
        try {
            remoteStatsDict.put("audioLevel", audioLevel);
            remoteStatsDict.put("bytesReceived", bytesReceived);
            remoteStatsDict.put("fractionLoss", rtp_stats_config.getDouble("localFractionLoss"));
            remoteStatsDict.put("jitter", new DecimalFormat("###.###").format(jitter));
            remoteStatsDict.put("packetsLost", packetsLost);
            remoteStatsDict.put("packetsReceived", packetsReceived);
            remoteStatsDict.put("ssrc", src);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return remoteStatsDict;
    }

    public JSONObject getAudioLevels() {
        return new JSONObject();
    }

    private JSONObject processStats(JSONObject stats) {
        try {
            double fractionLoss = stats.getDouble("fractionLoss");
            double jitter = stats.getDouble("jitter");
            stats.put("fractionLoss", (double) Math.round(fractionLoss * 1000) / 1000);
            stats.put("jitter", (double) Math.round(jitter * 1000) / 1000);
            if (stats.has("rtt")) {
                double rtt = stats.getDouble("rtt");
                stats.put("rtt", (double) Math.round(rtt * 1000) / 1000);
            }
            if (stats.has("mos")) {
                if (stats.get("mos").equals("null")) {
                    stats.put("mos", null);
                } else {
                    double mos = stats.getDouble("mos");
                    stats.put("mos", (double) Math.round(mos * 1000) / 1000);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return stats;
    }

    public Double sendAlertCallback(ArrayList<Double> metricsObject, String type) {
        int count = 0;
        double total = 0.0;
        for (Double value : metricsObject) {
            switch (type) {
                case "rtt":
                    if (value > 400) {
                        count = count + 1;
                        total = total + value;
                    }
                    break;
                case "mos":
                    if (value < 3.5) {
                        count = count + 1;
                        total = total + value;
                    }
                    break;
                case "jitter_local":
                case "jitter_remote":
                    if (value > 30) {
                        count = count + 1;
                        total = total + value;
                    }
                    break;
                case "packectloss_local":
                case "packectloss_remote":
                    if (codec != null && codec.equals("PCMU")) {
                        if (value >= 0.02) {
                            count = count + 1;
                            total = total + value;
                        }
                    } else {
                        if (value >= 0.10) {
                            count = count + 1;
                            total = total + value;
                        }
                    }
                    break;
            }
        }
        if (count >= 2) {
            return total / count;
        } else {
            return -1.0;
        }
    }

    public void sendMedialMetricsCallBack(String group, String level, String type, Double value, Boolean active, String description, String stream) {
        Log.D("Sending media metrics");
        HashMap<String, Object> messageTemplate = new HashMap<>();
        messageTemplate.put("group", group);
        messageTemplate.put("level", level);
        messageTemplate.put("type", type);
        messageTemplate.put("value", value);
        messageTemplate.put("active", active);
        messageTemplate.put("description", description);
        messageTemplate.put("stream", stream);
        Log.D(TAG, "****** metrics "+ messageTemplate);
        if (eventListener != null && this.enableMediaMetrics)
            eventListener.mediaMetrics(messageTemplate);
    }

    public void callMediaMatrices(String type, Double value, String message, String description, String stream) {
        ArrayList<Double> metricsObject = mediaMetricMap.get(type);
        if (metricsObject != null) {
            metricsObject.add(value);
            if (metricsObject.size() == 3) {
                Double average = sendAlertCallback(metricsObject, type);
                if (average != -1.0) {
                    mediaWarning.put(type, true);
                    sendMedialMetricsCallBack("network", "warning", message, average, true, description, stream);
                } else {
                    try {
                        if (mediaWarning.get(type) != null && mediaWarning.get(type)) {
                            mediaWarning.put(type, false);
                            sendMedialMetricsCallBack("network", "warning", message, 0.0, false, description, stream);
                        }
                    }catch (NullPointerException e){
                       e.printStackTrace();
                    }
                }
                metricsObject.remove(0);
            }
        }
    }

    public void processAudioLevels(String type, Double value, String message, String description, String stream) {
        ArrayList<Double> metricsObject = mediaMetricMap.get(type);
        if (metricsObject != null) {
            if (metricsObject.size() == 2) {
                metricsObject.add(value);
                Double audioLevelVolume = 0.0;
                //Count the entries of each audio levels

                HashMap<Double, Integer> audioLevelCounts = new HashMap<>();

                for (Double audioLevel : metricsObject
                ) {
                    if (audioLevelCounts.containsKey(audioLevel)) {
                        Integer val = audioLevelCounts.get(audioLevel);
                        audioLevelCounts.put(audioLevel, val != null ? val + 1 : 1);
                    } else {
                        audioLevelCounts.put(audioLevel, 1);
                    }
                    if (audioLevelCounts.get(audioLevel) >= 2) {
                        audioLevelVolume = audioLevel;
                    }
                }
                if (audioLevelVolume == -100) {
                    mediaWarning.put(type, true);
                    Log.D("Audio mute detected for " + type);
                    sendMedialMetricsCallBack("network", "warning", message, audioLevelVolume, true, description, stream);
                } else {
                    if (mediaWarning.get(type)) {
                        mediaWarning.put(type, false);
                        sendMedialMetricsCallBack("network", "warning", message, 0.0, false, description, stream);
                    }
                }
            } else {
                metricsObject.add(value);
            }
            if (metricsObject.size() == 3) {
                metricsObject.remove(0);
            }
        }

    }

    public void checkMicrophoneAccess(String type, int bytes, double audioLevel, String message, String description, String stream) {
        if (bytes == 0 && audioLevel == -100) {
            mediaWarning.put(type, true);
            sendMedialMetricsCallBack("network", "warning", message, 0.0, true, description, stream);
        } else {
            Log.D(TAG, "***** checkmicrophone "+ mediaWarning.toString());
            if (type != null && mediaWarning.get(type)) {
                mediaWarning.put(type, false);
                sendMedialMetricsCallBack("network", "warning", message, 0.0, false, description, stream);
            }
        }
    }

    public String getCodec(String codec) {
        if (codec.toLowerCase().startsWith("opus")) {
            return "opus";
        } else if (codec.toLowerCase().startsWith("pcmu")) {
            return "pcmu";
        } else {
            return codec;
        }
    }


    public JSONObject computeRTPStats(Map<String, RTCStats> statsMap) {
        JSONObject localStats = new JSONObject();
        JSONObject remoteStats = new JSONObject();
        Double mediaSourceAudioLevel = 0.0;
        String codecId;
        for (RTCStats item : statsMap.values()) {
            if (item.getType().equals("media-source")) {
                mediaSourceAudioLevel = (Double) item.getMembers().get("audioLevel");
            }
            if (item.getType().equals("remote-inbound-rtp")) {
                codecId = (String) item.getMembers().get("codecId");
                codec  = extractCodec((String) Objects.requireNonNull(Objects.requireNonNull(statsMap.get(codecId)).getMembers().get("mimeType")));
            }
        }


        for (RTCStats item : statsMap.values()) {
            if (item.getType().equals("outbound-rtp")) {
                localStats = processStats((getLocalStats(item.getMembers(), mediaSourceAudioLevel)));
            }
            if (item.getType().equals("inbound-rtp")) {
                remoteStats = processStats((getRemoteStats(item.getMembers())));
            }
        }

        JSONObject rtpStats = new JSONObject();
        try {
            rtpStats.put("codec", codec);
            localStats.remove("codec");
            rtpStats.put("local", localStats);
            rtpStats.put("remote", remoteStats);
            rtpStats.put("networkDownlinkSpeed", getNetworkDownlinkSpeed());
            rtpStats.put("networkType", getNetworkType());
            rtpStats.put("networkEffectiveType", getNetworkEffectiveType());
            printMediaMetric(localStats, remoteStats);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return rtpStats;

    }

    private String extractCodec(String mimeType) {
        String[] parts = mimeType.split("/");
        return parts[parts.length - 1];
    }
}
