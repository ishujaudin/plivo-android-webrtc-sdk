package com.plivo.endpoint;

import java.util.HashMap;
import java.util.Map;

public class Global {
    public static boolean DEBUG;
    public static Log.LogLevel LOG_LEVEL = Log.LogLevel.debug;  // default for now
    static String DOMAIN = BuildConfig.SIP_DOMAIN;
    static String OUTBOUND_PROXY = BuildConfig.OUTBOUND_PROXY;
    static String FALLBACK_PROXY = BuildConfig.FALLBACK_PROXY;
    static final String VERSION = BuildConfig.VERSION;
    static final String SDK_NAME= "PlivoWebRTCAndroidSDK";
    static boolean isJniLoaded = false;


    private static final String statsKeyURL_PROD = "https://stats.plivo.com/v1/browser/validate/";
    private static final String statsWSURL_PROD = "wss://insights.plivo.com/ws";
    private static final String S3BUCKET_API_URL_PROD = "https://stats.plivo.com/v1/browser/bucketurl/";
    private static final String S3BUCKET_API_URL_JWT_PROD = "https://stats.plivo.com/v1/browser/bucketurl/jwt/";

    public static final String SYNC_SERVER_LOGS_JWT = "https://nimbus.plivo.com/collect/logs/jwt/";
    public static final String SYNC_SERVER_LOGS_USERNAME_PROD = "https://nimbus.plivo.com/collect/logs/";

    public static final String  STATS_API_URL_ACCESS_TOKEN_PROD = "https://stats.plivo.com/v1/browser/validate/jwt/";

    static String statsKeyURL = statsKeyURL_PROD ;
    static String statsWSURL = statsWSURL_PROD ;
    static String S3BUCKET_API_URL = S3BUCKET_API_URL_PROD ;
    static String S3BUCKET_API_JWT_URL = S3BUCKET_API_URL_JWT_PROD ;
    static String STATS_API_URL_ACCESS_TOKEN = STATS_API_URL_ACCESS_TOKEN_PROD ;
    static String SYNC_SERVER_LOGS_USERNAME = SYNC_SERVER_LOGS_USERNAME_PROD ;

    static String xCallUUID = "X-CallUUID";
    static String callUUID = "call_id";

    static final long rtpBatchSize = 1;
    static final long rtpColectionFequency = 5000;
    static final long speechTimer = 3000;
    static final long firstRTPCall = 0;
    static final long minAverageBitrate = 8000;
    static final long maxAverageBitrate = 48000;
    static final int defaultRegTimeout = 3600 * 24 * 30;


    public static Map<String, String> DEFAULT_COMMENTS = new HashMap<String, String>() {{
        put("AUDIO_LAG", "audio_lag");
        put("BROKEN_AUDIO", "broken_audio");
        put("CALL_DROPPED", "call_dropped");
        put("CALLERID_ISSUES", "callerid_issue");
        put("DIGITS_NOT_CAPTURED", "digits_not_captured");
        put("ECHO", "echo");
        put("HIGH_CONNECT_TIME", "high_connect_time");
        put("LOW_AUDIO_LEVEL", "low_audio_level");
        put("ONE_WAY_AUDIO", "one_way_audio");
        put("OTHERS", "others");
        put("ROBOTIC_AUDIO", "robotic_audio");
    }};



    //keys in JWT
    public static final String JWT_HEADER = "X-Plivo-Jwt";
    public static final String APP_KEY = "app";
    public static final String EXPIRY_KEY = "exp";
    public static final String NBF_KEY = "nbf";
    public static final String INCOMING_ALLOW_KEY = "incoming_allow";
    public static final String OUTGOING_ALLOW_KEY = "outgoing_allow";
    public static final String SUB_KEY = "sub";
    public static final String PER_KEY = "per";
    public static final String VOICE_KEY = "voice";
    public static final String ISS_KEY = "iss";

    public  static final String INIT = "INIT";
    public  static final String LOGIN = "LOGIN";
    public  static final String CALL = "CALL";
    public  static final String LOGOUT = "LOGOUT";
    public  static final String CRASH = "CRASH";
    public  static final String CALL_QUALITY = "CALL_QUALITY";
    public  static final String NETWORK_CHANGE = "NETWORK_CHANGE";
    public  static final String AUDIO_TOGGLE = "AUDIO_TOGGLE";
}
