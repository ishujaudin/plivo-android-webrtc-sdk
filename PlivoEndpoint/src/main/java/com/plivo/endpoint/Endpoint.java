package com.plivo.endpoint;

import static com.plivo.endpoint.Global.AUDIO_TOGGLE;
import static com.plivo.endpoint.Global.CALL;
import static com.plivo.endpoint.Global.CALL_QUALITY;
import static com.plivo.endpoint.Global.CRASH;
import static com.plivo.endpoint.Global.INIT;
import static com.plivo.endpoint.Global.JWT_HEADER;
import static com.plivo.endpoint.Global.LOGIN;
import static com.plivo.endpoint.Global.LOGOUT;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Endpoint  implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "Endpoint";
    private static final int MIN_REG_TIMEOUT;
    private static final int MAX_REG_TIMEOUT;
    public static boolean isCreated = false;
    private static HashMap<String, Object> setupOptions;
    @SuppressLint("StaticFieldLeak")
    private static Endpoint endpoint;
    private Context context;
    public boolean incomingGrant = false;
    public boolean outgoingGrant = false;
    protected EventListener eventListener;
    protected AccessTokenListener accessTokenListener;
    SipController sipController;
    String deviceToken = "";
    String certId = "";
    private int regTimeout = Global.defaultRegTimeout;
    private boolean isRegistered;
    private boolean isLogoutInProgress;
    private boolean isRegWithDeviceToken;
    private String userName;
    private String password;
    private Outgoing curOutgoing;
    private NetworkChangeReceiver networkChangeReceiver;
    protected AudioOutputDeviceChangeReceiver audioOutputDeviceChangeReceiver;
    private boolean loginWithToken = false;
    private boolean loginTriedWithJWT = false;
    private String jwtAccessToken;
    private String sub_auth_ID = "";
    private String lastXCallUID = "";
    private String lastCallUUID;

    private String loginCallID;

    static {
        MIN_REG_TIMEOUT = (int) TimeUnit.MINUTES.toSeconds(2L);
        MAX_REG_TIMEOUT = (int) TimeUnit.DAYS.toSeconds(30L);
        setupOptions = new HashMap<>();
    }

    protected static Endpoint getInstance() {
        return endpoint;
    }

    protected Context getContext() {
        return context;
    }


    public String getJwtAccessToken() {
        return jwtAccessToken;
    }

    public String getSub_auth_ID() {
        return sub_auth_ID;
    }

    public boolean isOutgoingGrant() {
        return outgoingGrant;
    }

    public boolean isLoginWithToken() {
        return loginWithToken;
    }

    public boolean isLoginTriedWithJWT() {
        return loginTriedWithJWT;
    }

    private Endpoint(Context mContext, EventListener eventListener) {
        try {
            this.context = mContext;
            isCreated = true;
            this.eventListener = eventListener;
            this.initLib();
        }catch (Exception e){
            Log.InfoLogs(INIT,"Plivo SDK initializing with options:  "+setupOptions + " failed due to "+ Arrays.toString(e.getStackTrace()));
        }
    }

    public static Endpoint newInstance(Context mContext, boolean debug, EventListener listener) {
        if(mContext == null){
            android.util.Log.d(TAG, "newInstance: mContext can't be null");
            return null;
        }
        Log.enable(debug, mContext.getApplicationContext());
        if (endpoint == null) {
            endpoint = new Endpoint(mContext.getApplicationContext(), listener);
            Log.D("newInstance " + debug);
        }
        Log.InfoLogs(INIT, "Plivo SDK initialized successfully in "+ debug+ " mode");
        return endpoint;
    }

    public static Endpoint newInstance(Context mContext, boolean debug, EventListener eventListener, HashMap<String, Object> options) {
        if(mContext == null){
            android.util.Log.d(TAG, "newInstance: mContext can't be null");
            return null;
        }
        Log.enable(debug, mContext.getApplicationContext());
        setupOptions = options;
        Log.D("newInstance " + debug);
        if (endpoint == null) {
            endpoint = new Endpoint(mContext.getApplicationContext(), eventListener);
            Log.D("newInstance " + debug);
        }
        Log.InfoLogs(INIT, "Plivo SDK initialized successfully with options:- "+options + " in "+ debug +" mode");
        return endpoint;
    }

    public void setRegTimeout(int timeout) {
        if (timeout >= MIN_REG_TIMEOUT && timeout <= MAX_REG_TIMEOUT) {
            this.regTimeout = timeout;
            Log.InfoLogs(LOGIN, "Timeout set for registration:- "+ regTimeout);

        } else {
            Log.E("Allowed values of regTimeout are between 120 and 2592000 seconds only");
            Log.InfoLogs(LOGIN, "Invalid Registration Timeout:-  "+ regTimeout);
        }
    }

    public boolean login(String username, String password) {
        return this.login(username, password, "");
    }

    public boolean login(String username, String password, String deviceToken) {
        return this.login(username, password, deviceToken, "");
    }

    public boolean login(String username, String password,
                         String deviceToken, String certificateId) {

        Log.InfoLogs(LOGIN,"Login initiated with username:- "+ username + " deviceToken: "+ deviceToken + " certificateId: "+certificateId);
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(LOGIN, "Login failed : No internet connection!");
            eventListener.onLoginFailed();
            return false;
        } else if (!Utils.isAlphaNumeric(username)) {
            Log.InfoLogs(LOGIN, "Login failed : Invalid Username");
            Log.E("Invalid Username");
            eventListener.onLoginFailed();
            return false;
        } else if (password.isEmpty()) {
            Log.InfoLogs(LOGIN, "Login failed : password empty");
            eventListener.onLoginFailed();
            return false;
        } else if (deviceToken == null) {
            Log.InfoLogs(LOGIN, "Login failed : device token null");
            eventListener.onLoginFailed();
            return false;
        } else if(endpoint.isCallRunning()){
            Log.InfoLogs(LOGIN, "Login failed : can't login, call in-progress ");
            eventListener.onLoginFailed();
            return false;
        } else{
            this.userName = username;
            this.password = password;
            this.deviceToken = deviceToken;
            this.certId = certificateId;
            if (this.isRegistered) {
                Log.InfoLogs(LOGIN, "Login failed : Already logged in with the endpoint");
                return true;
            } else {
                this.isRegWithDeviceToken = deviceToken.length() > 0;
                try {
                    sipController.registerTimeOut(this.regTimeout);
                    sipController.login(username, password, deviceToken, certificateId);
                    this.logDebug(this.isRegWithDeviceToken ?
                            "Logging in with device token..." : "Logging in...");
                    return true;
                } catch (UnsatisfiedLinkError var6) {
                    var6.printStackTrace();
                    Log.E("errload loading libresipplivo:" + var6);
                } catch (Exception var7) {
                    var7.printStackTrace();
                }
                eventListener.onLoginFailed();
                return false;
            }
        }
    }

    public boolean loginWithJwtToken(String JWTToken, String deviceToken) {
        return loginWithJwtToken(JWTToken, deviceToken, "");
    }

    public boolean loginWithJwtToken(String JWTToken, String deviceToken, String certificateId) {
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(LOGIN, "Login failed : No internet connection!");
            eventListener.onLoginFailed();
            return false;
        } else if (this.isRegistered) {
            loginWithToken = true;
            Log.InfoLogs(LOGIN, "Login failed : Already logged in with the endpoint");
            return true;
        } else if(endpoint.isCallRunning()){
            Log.InfoLogs(LOGIN, "Login failed : can't login, call in-progress ");
            eventListener.onLoginFailed();
            return false;
        } else {
            if (JWTToken != null && JWTToken.length() != 0) {
                return loginJwt(JWTToken, deviceToken, certificateId, null);
            }
            eventListener.onLoginFailed("INVALID_ACCESS_TOKEN");
            return false;
        }
    }

    public boolean loginWithJwtToken(String JWTToken) {
        return loginWithJwtToken(JWTToken, null);
    }

    private String getRandomUserSub() {
        return "puser" +
                ((long) Math.floor(Math.random() * 9_000_000_000L) + 1_000_000_000L) +
                "jt";
    }

    private boolean validateJWTAccessToken(JSONObject jwtToken) {
        if (jwtToken == null) {
            eventListener.onLoginFailed("INVALID_ACCESS_TOKEN");
            Log.InfoLogs(LOGIN,"validateJWTAccessToken: INVALID_ACCESS_TOKEN");
            return false;
        }
        try {
            boolean isValid = JWTUtils.checkNullNTypeTokenValue(jwtToken);
            if (!isValid) {
                eventListener.onLoginFailed("INVALID_ACCESS_TOKEN");
                Log.InfoLogs(LOGIN,"validateJWTAccessToken: INVALID_ACCESS_TOKEN");
                return false;
            }
        } catch (JSONException e) {
            eventListener.onLoginFailed("INVALID_ACCESS_TOKEN");
            Log.InfoLogs(LOGIN,"validateJWTAccessToken: INVALID_ACCESS_TOKEN");
            e.printStackTrace();
            return false;
        }
        this.incomingGrant = JWTUtils.getIncomingGrantPer(jwtToken);
        this.outgoingGrant = JWTUtils.getOutgoingGrantPer(jwtToken);
        return true;
    }

    protected void registerReceivers() {
        registerNetworkChangeReceiver();
        registerAudioOutputDeviceReceiver();
    }

    private void registerAudioOutputDeviceReceiver() {
        Log.InfoLogs(AUDIO_TOGGLE, "Audio Toggle listener registered successfully.");
        if (audioOutputDeviceChangeReceiver == null)
            audioOutputDeviceChangeReceiver = new AudioOutputDeviceChangeReceiver();

        IntentFilter filter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            filter.addAction(AudioManager.ACTION_SPEAKERPHONE_STATE_CHANGED);
        }
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        filter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        filter.addAction(BluetoothDevice.EXTRA_DEVICE);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        context.registerReceiver(audioOutputDeviceChangeReceiver, filter);
    }

    private void registerNetworkChangeReceiver() {
        if (networkChangeReceiver == null)
            networkChangeReceiver = new NetworkChangeReceiver();

        IntentFilter mIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(networkChangeReceiver, mIntentFilter);

    }

    protected void unregisterReceivers() {
        unregisterNetworkChangeReceiver();
        unregisterAudioOutputDeviceReceiver();
    }

    private void unregisterAudioOutputDeviceReceiver() {
        if (audioOutputDeviceChangeReceiver != null) {
            context.unregisterReceiver(audioOutputDeviceChangeReceiver);
            audioOutputDeviceChangeReceiver = null;
            Log.InfoLogs(AUDIO_TOGGLE, "Audio Toggle listener unregistered successfully.");
        }
    }

    private void unregisterNetworkChangeReceiver() {
        if (networkChangeReceiver != null) {
            context.unregisterReceiver(networkChangeReceiver);
            networkChangeReceiver = null;
        }
    }

    public boolean logout() {
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        } else if (!this.isRegistered) {
            Log.InfoLogs(LOGOUT,"Cannot logout without endpoint already logged in.");
            return false;
        } else if (this.isLogoutInProgress) {
            Log.InfoLogs(LOGOUT,"Logout is already in progress. Check for onLogout() callback.");
            return false;
        } else {
            try {
                if (this.isLoginWithToken()) {
                    String[] keys = new String[]{JWT_HEADER};
                    String[] values = new String[]{jwtAccessToken};
                    sipController.unregisterUserHeader(keys, values);
                } else {
                    sipController.unregisterUser();
                }
                setLastXCallUID("");
                resetJWTParam(true);
                this.regTimeout = Global.defaultRegTimeout;
                com.plivo.endpoint.Log.InfoLogs(LOGOUT, "Logout successful");
                this.isLogoutInProgress = true;
                setRegistered(false);
//                eventListener.onLogout();
                return true;
            } catch (UnsatisfiedLinkError var2) {
                var2.printStackTrace();
                Log.E("errload loading plivo:" + var2);
            } catch (Exception var3) {
                var3.printStackTrace();
            }
            return false;
        }
    }

    protected String getLogsUsername() {
        if(isLoginWithToken()){
            return getSub_auth_ID();
        }else{
            return userName;
        }
    }

    protected void resetJWTParam(boolean resetAccessTokenListener) {
        loginTriedWithJWT = false;
        loginWithToken = false;
        incomingGrant = false;
        outgoingGrant = false;
        sub_auth_ID = "";
        jwtAccessToken = "";
        if (resetAccessTokenListener) accessTokenListener = null;
    }


    public Outgoing createOutgoingCall() {
        this.logDebug("createOutgoingCall");
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(CALL,"Cannot createOutgoingCall() without internet connection");
            return null;
        } else if (!this.isRegistered) {
            Log.InfoLogs(CALL,"Cannot createOutgoingCall() without endpoint logged in. Call login() before.");
            return null;
        } else {
            Outgoing out = new Outgoing(this, sipController);
            this.curOutgoing = out;
            Log.I("outgoing object created");
            return out;
        }
    }

    public boolean checkDtmfDigit(String digit) {
        return Utils.VALID_DTMF.contains(digit);
    }

    protected Outgoing getOutgoing() {
        return this.curOutgoing;
    }

    protected Incoming getIncoming() {
        return sipController.getIncoming();
    }

    public HashMap<String, Object> getSetupOptions() {
        return setupOptions;
    }

    public boolean isCallRunning() {
        boolean isOutgoingActive = false;
        boolean isIncomingActive = false;
        if (getOutgoing() != null) isOutgoingActive = getOutgoing().isActive();
        if (getIncoming() != null) isIncomingActive = getIncoming().isActive();

        return isOutgoingActive || isIncomingActive;
    }

    void setRegistered(boolean status) {
        this.isRegistered = status;
        if (!this.isRegistered) {
            this.isLogoutInProgress = false;
            this.isRegWithDeviceToken = false;
        }else{
            Log.InfoLogs(LOGIN, "User logged in successfully! id:" + endpoint.getLoginCallID());
        }
    }

    public int getRegistrationTimeout() {
        return regTimeout;
    }

    public boolean getRegistered() {
        return this.isRegistered;
    }

    private void logDebug(String str) {
        Log.D("[endpoint]" + str);
    }

    private void initLib() throws Exception{
        Thread.setDefaultUncaughtExceptionHandler(this);
        this.loadJNI();
        this.logDebug("Starting module..");
        sipController = SipController.getInstance(this);
        ServerSettings serverSettings = new ServerSettings();
        serverSettings.domain = Global.DOMAIN;
        serverSettings.dnsServer = "";
        serverSettings.proxyServer = Global.OUTBOUND_PROXY;
        serverSettings.fallbackProxyServer = Global.FALLBACK_PROXY;
        serverSettings.userAgent = Global.SDK_NAME + "-" + Global.VERSION;
        sipController.init(serverSettings, Global.DEBUG);
        registerReceivers();
    }

    private void loadJNI() {
        try {
            System.loadLibrary("rtcsip_jni");
            Global.isJniLoaded = true;
            this.logDebug("librtcsip loaded");
        } catch (UnsatisfiedLinkError var2) {
            var2.printStackTrace();
            Log.E("errload loading librtcsip:" + var2);
        } catch (Exception var3) {
            var3.printStackTrace();
        }

    }

    public void resetEndpoint() {
        try {
            Log.InfoLogs(LOGOUT, "Endpoint instance Destroyed");
            sipController.deinit();
            sipController.reset();
            sipController = null;
            Log.syncServerLogs(getJwtAccessToken(),getLogsUsername());
            endpoint = null;
        }catch(Exception e){
            Log.InfoLogs(LOGOUT, "Endpoint instance destruction failed:- "+ Arrays.toString(e.getStackTrace()));
        }
    }

    public void networkChange() {
        if (this.isLoginWithToken()) {
            String[] keys = new String[]{JWT_HEADER};
            String[] values = new String[]{jwtAccessToken};
            sipController.networkChangeHeader(keys, values);
        } else {
            sipController.networkChange();
        }
    }

    public boolean loginForIncomingWithJwt(String JWTToken, String deviceToken, String certificateId, Map<String, String> push_headers) {
        this.logDebug("loginForIncomingWithJwt: " + push_headers + "JWT : " + JWTToken);
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(LOGIN, "Incoming Login failed : No internet connection!");
            eventListener.onLoginFailed();
            return false;
        } else if (deviceToken == null) {
            Log.InfoLogs(LOGIN, "Incoming Login failed: device token null");
            eventListener.onLoginFailed();
            return false;
        } else if (Utils.invalidatePushHeaders(push_headers)) {
            Log.InfoLogs(LOGIN, "Incoming Login failed: Invalid Notification header");
            eventListener.onLoginFailed();
            return false;
        } else if(endpoint.isCallRunning()){
            Log.InfoLogs(LOGIN, "Incoming Login failed : can't login, call in-progress ");
            eventListener.onLoginFailed();
            return false;
        } else {
            try {
                if (loginWithToken && !this.incomingGrant) {
                    eventListener.onPermissionDenied("INVALID_ACCESS_TOKEN_GRANTS");
                    Log.InfoLogs(CALL, "Incoming call permission not granted");
                    return false;
                } else if (JWTToken != null && JWTToken.length() != 0) {
                    return loginJwt(JWTToken, deviceToken, certificateId, push_headers);
                }
            } catch (UnsatisfiedLinkError var3) {
                var3.printStackTrace();
                Log.InfoLogs(LOGIN,"Login failed: errload loading lib:" + var3);
            } catch (Exception var4) {
                var4.printStackTrace();
            }
            eventListener.onLoginFailed("INVALID_ACCESS_TOKEN");
            return false;
        }
    }

    private boolean loginJwt(String JWTToken, String deviceToken, String certificateId, Map<String, String> push_headers) {
        Log.InfoLogs(LOGIN,"Login initiated with JWT with credentials:- jwt: "+ JWTToken + " deviceToken: "+ deviceToken + " certificateId: "+certificateId);
        try {
            JSONObject jwt = JWTUtils.decodeJWT(JWTToken);
            String jwtString = jwt.toString();
            Log.InfoLogs(LOGIN,"Parsed JWT with params:- "+ jwtString);
            if (!validateJWTAccessToken(jwt)) return false;
            if (deviceToken != null) {
                this.isRegWithDeviceToken = deviceToken.length() > 0;
            } else {
                deviceToken = "";
            }
            assert jwt != null;
            this.certId = certificateId;
            this.userName = JWTUtils.getSub(jwt);
            if (userName == null || userName.isEmpty()) userName = getRandomUserSub();
            String authId = JWTUtils.getIss(jwt);
            sub_auth_ID = userName + "_" + authId;
        } catch (Exception e) {
            eventListener.onLoginFailed("INVALID_ACCESS_TOKEN");
            e.printStackTrace();
            return false;
        }

        sipController.registerTimeOut(this.regTimeout);
        loginTriedWithJWT = true;
        this.jwtAccessToken = JWTToken;
        if (push_headers != null) {
            Log.InfoLogs(LOGIN, "Incoming call registration initiated with "+ push_headers + " "+ sub_auth_ID);
            sipController.relayVoipPushNotification(sub_auth_ID, "", deviceToken, certificateId, push_headers);
        } else {
            sipController.loginWithAccessToken(sub_auth_ID, deviceToken, JWTToken, certificateId);
        }
        this.isRegistered = true;
        return true;
    }
    /**
     *
     * @deprecated This method is deprecated.
         *             Use {@link #loginForIncomingWithUsername(String username, String password, String deviceToken, String certificateID, Map)} ()} ()} when login with username.
         *             Use {@link #loginForIncomingWithJwt(String JWTToken, String deviceToken, String certificateID, Map)} when login with jwt token
     *             This method may be removed in future releases.
     */
    @Deprecated
    public void relayVoipPushNotification(Map<String, String> push_headers) {
        this.logDebug("relayVoipPushNotification: " + push_headers);
        if (!this.isRegistered && !this.isRegWithDeviceToken) {
            Log.InfoLogs(LOGIN, "Login failed : Cannot call relayVoipPushNotification() without successful login with device " +
                    "token. Use login(String username, String password, String deviceToken).");
        } else if (Utils.invalidatePushHeaders(push_headers)) {
            Log.InfoLogs(LOGIN,"Login failed: Invalid Notification");
        } else {
            try {
                Log.InfoLogs(LOGIN, "Incoming call registration initiated with "+ push_headers + " "+userName);
                sipController.relayVoipPushNotification(this.userName, this.password, this.deviceToken, certId, push_headers);
            } catch (UnsatisfiedLinkError var3) {
                var3.printStackTrace();
                Log.InfoLogs(LOGIN,"Login failed: errload loading lib:" + var3);
            } catch (Exception var4) {
                var4.printStackTrace();
            }

        }
    }

    /**
     * This method provides an alternative to the deprecated {@link #relayVoipPushNotification(Map headers)} ()}.
     * When the app is in the killed state and wakes up from the FCM notification, no need to call
     * login(username, password, deviceToken) before calling loginForIncomingWithUsername(remoteMessage.getData()).
     */
    public boolean loginForIncomingWithUsername(String username,
                                                String password,
                                                String deviceToken,
                                                String certificateId,
                                                Map<String, String> push_headers) {
        this.logDebug("loginForIncomingWithUsername: " + push_headers);
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(LOGIN,"Login failed: No internet connection!");
            eventListener.onLoginFailed();
            return false;
        } else if (!Utils.isAlphaNumeric(username)) {
            Log.InfoLogs(LOGIN,"Login failed: Invalid Username");
            eventListener.onLoginFailed();
            return false;
        } else if (null == deviceToken) {
            Log.InfoLogs(LOGIN,"Login failed: device token null");
            eventListener.onLoginFailed();
            return false;
        } else {
            this.userName = username;
            this.password = password;
            this.deviceToken = deviceToken;

            if (Utils.invalidatePushHeaders(push_headers)) {
                Log.InfoLogs(LOGIN,"Login failed: Invalid Notification");
                return false;
            } else {
                try {
                    if (loginWithToken && !this.incomingGrant) {
                        eventListener.onPermissionDenied("INVALID_ACCESS_TOKEN_GRANTS");
                        return false;
                    }
                    sipController.registerTimeOut(this.regTimeout);
                    Log.InfoLogs(LOGIN, "Incoming call registration initiated with "+ push_headers + " "+userName);
                    sipController.relayVoipPushNotification(username, password, deviceToken, certificateId, push_headers);
                    return true;
                } catch (UnsatisfiedLinkError var3) {
                    var3.printStackTrace();
                    Log.InfoLogs(LOGIN,"Login failed: errload loading lib:" + var3);
                    return false;
                } catch (Exception var4) {
                    var4.printStackTrace();
                    return false;
                }

            }
        }
    }

    public String getLastCallUUID() {
        return this.lastCallUUID;
    }

    protected void setLastCallUUID(String lastCallUUID) {
        this.lastCallUUID = lastCallUUID;
    }

    public String getLastXCallUUID() {
        return this.lastXCallUID;
    }

    protected void setLastXCallUID(String lastXCallUID) {
        this.lastXCallUID = lastXCallUID;
    }

    public String getLoginCallID() {
        return this.loginCallID;
    }

    protected void setLoginCallID(String loginCallID) {
        this.loginCallID = loginCallID;
    }


    public Map<String, String> getValidationStatus(String callUUID,
                                                   Integer starRating,
                                                   ArrayList<String> issues,
                                                   String note,
                                                   Boolean sendConsoleLogs) {
        Map<String, String> status = new HashMap<>();
        status.put("true", "No Error");
        if (sendConsoleLogs == null) {
            Log.E("Flag 'sendConsoleLogs' can't be null");
            status.put("false", "Flag 'sendConsoleLogs' can't be null");
            return status;
        } else if (!this.isRegistered) {
            Log.E("Cannot submit feedback without endpoint logged in");
            status.put("false", "Cannot submit feedback without endpoint logged in.");
            return status;
        } else if (callUUID != null && !callUUID.isEmpty()) {
            if (starRating != null && starRating > 0 && starRating <= 5) {
                ArrayList<String> issue_final = new ArrayList<>();
                ArrayList<String> issuesNotFromPredefinedList = new ArrayList<>();
                if (note != null && !note.equals("") && note.length() > 280) {
                    Log.E("Note can be maximum 280 characters");
                    status.put("false", "Note can be maximum 280 characters");
                    return status;
                }

                if (starRating != 5 && (issues == null || issues.isEmpty())) {
                    Log.E("Atleast one issue is mandatory for feedback");
                    status.put("false", "Atleast one issue is mandatory for feedback");
                    return status;
                }

                if (issues != null && !issues.isEmpty()) {

                    for (String issue : issues) {
                        String _issue = issue.toUpperCase();
                        Log.I("Issue : " + _issue);
                        if (Global.DEFAULT_COMMENTS.containsKey(_issue)) {
                            String extractedIssue = Global.DEFAULT_COMMENTS.get(_issue);
                            Log.I("Extracted Issue : " + extractedIssue);
                            issue_final.add(extractedIssue);
                        } else {
                            issuesNotFromPredefinedList.add(issue);
                        }
                    }

                    issues.removeAll(issuesNotFromPredefinedList);
                }

                if (issue_final.isEmpty()) {
                    Set<String> validIssues = Global.DEFAULT_COMMENTS.keySet();
                    if (starRating != 5) {
                        Log.E("Issues must be from the predefined list of issues for" +
                                " feedback -" + validIssues);
                        status.put("false", "Issues must be from the predefined list of" +
                                " issues for feedback -" + validIssues);
                        return status;
                    }
                    Log.D("Feedback with full rating without any Issues or matches " +
                            "rom predefined list of issues -" + validIssues);
                }

            } else {
                Log.E("Star rating should be between 1 to 5");
                status.put("false", "Star rating should be between 1 to 5");
            }
            return status;
        } else {
            Log.E("Caller UUID is mandatory");
            status.put("false", "Caller UUID is mandatory");
            return status;
        }
    }

    public static JSONObject getRequestPayload(String callUUID, String xCallUUID, String overall, String comment, final ArrayList<String> issueList) {
        JSONObject consoleBody = new JSONObject();
        JSONObject infoBody = new JSONObject();

        try {
            String issues = TextUtils.join(",", issueList);
            String finalComment = issues + " " + comment;
            infoBody.put("overall", overall);
            infoBody.put("comment", finalComment);
            /*infoBody.put("overall", "\"" + "5" + "\"");
            infoBody.put("comment", "\"" + "" + "\"");*/
//            consoleBody.put("callstats_key", "\"" + password + "\"");
            consoleBody.put("calluuid", callUUID);
            consoleBody.put("corelationId", callUUID);
            consoleBody.put("xcallUUID", xCallUUID);
            consoleBody.put("info", infoBody);
            return consoleBody;
        } catch (JSONException var6) {
            var6.printStackTrace();
            return null;
        }
    }

    public void submitCallQualityFeedback(final String callUUID,
                                          final Integer starRating,
                                          final ArrayList<String> issues,
                                          final String note,
                                          final Boolean sendConsoleLogs,
                                          final FeedbackCallback callback) {
        Map<String, String> status = this.getValidationStatus(callUUID,
                starRating, issues, note, sendConsoleLogs);
        if (status.containsKey("false")) {
            Log.InfoLogs(CALL_QUALITY, "SubmitCallQualityFeedback failed "+ status.get("false"));
            if (callback != null) {
                callback.onValidationFail(status.get("false"));
            } else {
                Log.D("Validation error : " + status.get("false"));
            }
        } else {
            JSONObject postBody = null;
            try {
                String _note = note;
                if (_note == null) {
                    _note = "";
                }
                postBody = getRequestPayload(this.getLastCallUUID(), this.getLastXCallUUID(),
                        String.valueOf(starRating), _note, issues);
            } catch (Exception e) {
                e.printStackTrace();
                Log.InfoLogs(CALL_QUALITY, "SubmitCallQualityFeedback failed "+ e.getLocalizedMessage());
            }

            sipController.sendFeedbackEvent(postBody);

            JSONObject s3BucketRequest = new JSONObject();
            try {
                if (isLoginTriedWithJWT()) {
                    s3BucketRequest.put("jwt", this.getJwtAccessToken());
                    s3BucketRequest.put("call_uuid", this.getLastCallUUID());
                } else {
                    s3BucketRequest.put("username", this.userName);
                    s3BucketRequest.put("password", this.password);
                    s3BucketRequest.put("calluuid", this.getLastCallUUID());
                    s3BucketRequest.put("domain", Global.DOMAIN);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            try {
                HttpPostTask postClient = new HttpPostTask(s3BucketRequest,
                        "POST", new HTTPRequestCallback() {
                    public void onResponse(String response) {
                        Log.D("onResponse : " + response);
                        if (response.equals("")) {
                            Log.E(" s3 url is empty");
                        } else {
                            try {
                                JSONObject jsonObject = new JSONObject(response);
                                String s3URL = jsonObject.get("data").toString();
                                Map<String, Object> feedback = new HashMap<>();
                                String putRequestLoad = "";
                                feedback.put("overall", starRating);
                                String _note = note;
                                if (_note == null) {
                                    _note = "";
                                }

                                String finalIssueList = TextUtils.join(",", issues);
                                feedback.put("comment", finalIssueList + " " + _note);
                                putRequestLoad = putRequestLoad + feedback + "\n";
                                if (sendConsoleLogs) {
                                    putRequestLoad = putRequestLoad + Log.deviceLog.toString();
                                }

                                try {
                                    HttpPutTask putClient =
                                            new HttpPutTask(putRequestLoad,
                                                    "PUT", new HTTPRequestCallback() {
                                        public void onResponse(String response) {
                                            Log.D("onResponse : putRequestLoad | " + response);
                                            if (callback != null) {
                                                callback.onSuccess(response);
                                            } else {
                                                Log.D("Success : " + response);
                                            }

                                        }

                                        public void onFailure(int statusCode) {
                                            Log.E("Log file was not uploaded to server");
                                            if (callback != null) {
                                                callback.onFailure(statusCode);
                                            } else {
                                                Log.D("Failure : " + statusCode);
                                            }

                                        }
                                    });
                                    putClient.execute(s3URL);
                                } catch (Exception var11) {
                                    var11.printStackTrace();
                                }
                            } catch (Exception var12) {
                                var12.printStackTrace();
                            }
                            Log.syncServerLogs(getJwtAccessToken(),getLogsUsername());
                        }
                    }

                    public void onFailure(int statusCode) {
                        Log.InfoLogs(CALL_QUALITY, "SubmitCallQualityFeedback failed statusCode "+ statusCode);
                        Log.E(" Error while making the POST request to get s3url");
                        if (callback != null) {
                            callback.onFailure(statusCode);
                        } else {
                            Log.D("Failure : " + statusCode);
                        }
                        Log.syncServerLogs(getJwtAccessToken(),getLogsUsername());
                    }
                });
                String url = (this.isLoginTriedWithJWT()) ?
                        Global.S3BUCKET_API_JWT_URL : Global.S3BUCKET_API_URL;
                postClient.execute(url);
            } catch (Exception var10) {
                var10.printStackTrace();
            }

        }
    }

    public void setJWTStatus() {
        if (loginTriedWithJWT) {
            this.loginWithToken = true;
        }
    }

    public boolean loginWithAccessTokenGenerator(AccessTokenListener accessTokenListener) {
        Log.InfoLogs(LOGIN,"Login initiated with AccessTokenGenerator");
        if (accessTokenListener != null) {
            this.accessTokenListener = accessTokenListener;
            accessTokenListener.getAccessToken();
            return true;
        }
        return false;
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        Log.InfoLogs(CRASH, "crash: "+ Arrays.toString(e.getStackTrace()));
    }
}