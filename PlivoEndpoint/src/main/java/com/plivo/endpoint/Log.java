package com.plivo.endpoint;

import static com.plivo.endpoint.Global.LOGIN;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Pair;

import com.plivo.endpoint.tape2.ObjectQueue;
import com.plivo.endpoint.tape2.QueueFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.PriorityQueue;


class Log {
    public static final String datePattern = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String TAG = "PlivoEndpoint";
    private static final String loggingName = "PlivoSDK";
    public static PriorityQueue<String> deviceLog = new PriorityQueue<>();
    private static ObjectQueue<String> queue;
    private static JSONArray logListJsonArray;

    private static Context context;

    private static int getCurrentLogLevel(LogLevel level){
        switch (level){
            case debug:   return 7;
            case info:  return  6;
            case notice: return  5;
            case warning: return  4;
            case error: return 3;
            case critical: return  2;
            case alert: return 1;
            case emergency: return  0;
            case none: return  -1;
        }
        return -1;
    }

    enum LogLevel {
        debug,
        info,
        notice,
        warning,
        error,
        critical,
        alert,
        emergency,
        none,
    }

    private static boolean checkLogLevel(LogLevel debug) {
        return  getCurrentLogLevel(Global.LOG_LEVEL) >= getCurrentLogLevel(debug);
    }

    private static String getLogLocation() {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        if (stackTraceElements.length >= 5) {
            StackTraceElement caller = stackTraceElements[4];
            return caller.getFileName() + "::" + caller.getMethodName() + "():" + caller.getLineNumber();
        } else {
            return "N/A";
        }
    }

    public static void InfoLogs(String tag, String logMessage) {
        android.util.Log.d(tag, logMessage);
        String message = getLogPreMessage(tag , getLogLocation());
        try {
            queue.add(message + logMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void D(String l) {
        updateDeviceLog("DEBUG", l);
        log(l, android.util.Log.DEBUG, LogLevel.debug);
    }


    public static void D(String TAG, String l) {
        updateDeviceLog("DEBUG", l);
        if(checkLogLevel(LogLevel.debug)) log(l, android.util.Log.DEBUG, TAG, LogLevel.debug);
    }

    public static void E(String l) {
        updateDeviceLog("ERROR", l);
        log(l, android.util.Log.ERROR,LogLevel.error);
    }

    public static void E(String TAG, String l) {
        updateDeviceLog("ERROR", l);
        log(l, android.util.Log.ERROR, TAG, LogLevel.error);
    }

    public static void W(String l) {
        updateDeviceLog("WARN", l);
        log(l, android.util.Log.WARN, LogLevel.warning);
    }

    public static void W(String TAG, String l) {
        updateDeviceLog("WARN", l);
        log(l, android.util.Log.WARN, TAG, LogLevel.warning);
    }

    public static void I(String l) {
        updateDeviceLog("INFO", l);
        log(l, android.util.Log.INFO, LogLevel.info);
    }

    public static void I(String TAG, String l) {
        updateDeviceLog("INFO", l);
        log(l, android.util.Log.INFO, TAG, LogLevel.info);
    }

    public static void log(String l, int priority, LogLevel logLevel) {
        if (Log.isEnabled() && checkLogLevel(logLevel)) {
            android.util.Log.println(priority, TAG, l);
        }
    }

    public static void log(String l, int priority, String TAG, LogLevel logLevel) {
        if (Log.isEnabled() && checkLogLevel(logLevel)) {
            android.util.Log.println(priority, TAG, l);
        }
    }

    public static void enable(boolean enable, Context mContext) {
        try {
            Global.DEBUG = enable;
            context = mContext;
            File appDirectory = new File(mContext.getCacheDir() + "/MyLogFolder.txt");
            QueueFile queueFile = new QueueFile.Builder(appDirectory).build();
            queue = ObjectQueue.create(queueFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled() {
        return Global.DEBUG;
    }

    public static void updateDeviceLog(String filter, String logMessage) {
        String preMessage = getLogPreMessage(filter, "");
        if (deviceLog.size() >= 900) {
            deviceLog.remove();
        }
        deviceLog.add(preMessage + logMessage);
    }

    public static void syncServerLogs(String jwtToken, String username) {
        if (areTestRunning(context, "com.plivo.endpoint.test")) return;
        Log.D(TAG, "syncServerLogs: ");
        try {
            if(queue.isEmpty()){
                Log.D(TAG, "syncServerLogs: No logs to sync");
                return;
            }
            Pair<Boolean, JSONArray> logsPair = queue.asList();
            if(logsPair.second.length() == 0) return;

            preparePostBody(logsPair,jwtToken, username);

            if(logsPair.first) {
                syncServerLogs(jwtToken, username);
            }else {
                queue.clear();
            }
        } catch (OutOfMemoryError o) {
            try {
                queue.clear();
                Log.InfoLogs(LOGIN,"clearing logs for OutOfMemoryError ");
            } catch (IOException e) {
                android.util.Log.d(TAG, "syncServerLogs: IOException: "+e.getMessage());
            }
        } catch (Exception e) {
            android.util.Log.d(TAG, "syncServerLogs: Exception: "+e.getMessage());
        }
    }

    private static boolean areTestRunning(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static void preparePostBody(Pair<Boolean, JSONArray> logsArray, String jwtToken, String username) {
        Log.D(TAG, "preparePostBody: ");
        JSONObject postBody = new JSONObject();

        try {
            postBody.put("username", username);
            postBody.put("logs", logsArray.second);
            postBody.put("jwt", jwtToken);
            postBody.put("user_agent", System.getProperty("http.agent"));
            postBody.put("sdk_name", Global.SDK_NAME);
            postBody.put("sdk_v", Global.VERSION);

            if(Endpoint.getInstance().getLastXCallUUID() != null && !Endpoint.getInstance().getLastXCallUUID().isEmpty()) {
                postBody.put("call_uuid", Endpoint.getInstance().getLastXCallUUID());
            }

            pushLogsToServer(logsArray,postBody, jwtToken);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }


    private static void pushLogsToServer(Pair<Boolean, JSONArray> logsArray, JSONObject postBody, String jwtToken) {
        Log.D(TAG, "pushLogsToServer: ");
        HttpPostTask client = new HttpPostTask(postBody, "POST", new HTTPRequestCallback() {
            @Override
            public void onFailure(int statusCode) {
                Log.E("failed in syncing logs statusCode: " + statusCode);
                try {
                    for (int i = 0; i < logsArray.second.length(); i++) {
                        queue.add((String) logsArray.second.get(i));
                    }
                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onResponse(String response) {
                Log.D("Successful sync logs with server");
                try {
                    if(!logsArray.first) queue.clear();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        client.execute(getRespectedURL(jwtToken));
    }

    private static String getRespectedURL(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return Global.SYNC_SERVER_LOGS_USERNAME;
        } else {
            return Global.SYNC_SERVER_LOGS_JWT;
        }
    }

    public static void saveSipMessage(String logs) {
        String[] splitStr = logs.split("##");
        for (String logMessage : splitStr) {
            String message = " [" + LOGIN + "] " + loggingName + " :: ";
            try {
                queue.add(message + logMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getLogPreMessage(String tag, String location) {
        DateFormat logDate = new SimpleDateFormat(datePattern, Locale.US);
        Date today = Calendar.getInstance().getTime();
        String logDateString = "[" + logDate.format(today) + "]";
        return logDateString + " [" + tag + "]" + "["+ location + "] ";
    }

}

