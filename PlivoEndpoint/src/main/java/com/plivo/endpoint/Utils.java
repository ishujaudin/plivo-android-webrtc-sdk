package com.plivo.endpoint;

import static com.plivo.endpoint.NetworkChangeReceiver.CONNECTED_DEVICE_TYPE;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Utils {

    public static HashMap<String, Object> options = new HashMap<String, Object>() {{
        put("enableTracking",true);
    }};

    @NonNull
    static String getCurrentTimeInMilliSeconds() {
        return new Date().getTime() + "";
    }

    static String mapToString(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;

        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!TextUtils.isEmpty(stringBuilder)) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return stringBuilder.toString();
    }

    static final Set<String> VALID_DTMF = new HashSet<>(Arrays.asList(
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "#", "*"));

    private static final String VALID_HEADER_KEY_REGEX = "[xX]-[pP][hH]-[a-zA-Z0-9\\-]{1,19}$";
    private static final String VALID_HEADER_VALUE_REGEX = "[a-zA-Z0-9\\-+_()%]{1,120}$";
    private static final String[] MIN_REQUIRED_PUSH_HEADERS = { "registrar", "index", "label" };

    /**
     *
     * @param string: comma separated value of map k1:v1,k2:v2
     * @return converted map
     */
    static Map<String, String> stringToMap(String string) {
        if (TextUtils.isEmpty(string)) return null;

        Map<String, String> map = new HashMap<>();
        String[] keyValuePairs = string.trim().split(",");
        int delimiterIndex;
        for (String kv : keyValuePairs) {
            delimiterIndex = kv.indexOf(":");
            if (delimiterIndex != -1)
                map.put(kv.substring(0, delimiterIndex).trim(), kv.substring(delimiterIndex+1).trim());
        }

        return map;
    }

    static boolean validateCallHeaders(Map<String, String> callHeaders) {
        if (callHeaders == null || callHeaders.isEmpty()) return false;

        Pattern keyPattern = Pattern.compile(VALID_HEADER_KEY_REGEX, Pattern.CASE_INSENSITIVE);
        Pattern valuePattern = Pattern.compile(VALID_HEADER_VALUE_REGEX);

        Iterator<Map.Entry<String, String>> entryIterator = callHeaders.entrySet().iterator();
        Map.Entry<String, String> entry;
        while (entryIterator.hasNext()) {
            entry = entryIterator.next();
            if (!keyPattern.matcher(entry.getKey()).matches() ||
                    !valuePattern.matcher(entry.getValue()).matches()) {
                Log.W("Invalid header. Skipping " + entry);
                entryIterator.remove();
            }
        }

        return !callHeaders.isEmpty();
    }

    /**
     * checkAndFilter push notification headers {inverted function}
     * @param pushHeaders header to push notification
     * @return false if valid
     */
    static boolean invalidatePushHeaders(Map<String, String> pushHeaders) {
        if (pushHeaders == null || pushHeaders.isEmpty()) return true;

        for (String rHeader : MIN_REQUIRED_PUSH_HEADERS) {
            if (!pushHeaders.containsKey(rHeader)) {
                return true;
            }
        }

        return false;
    }

    static boolean validateDestination(String dest){
        return  (isAlphaNumeric(dest) || dest.startsWith("+") && isNumeric(dest.replace("+",""))) || isAlphaNumeric(dest.replace("_",""));
    }

    static boolean isAlphaNumeric(String value){ return value !=null && !value.isEmpty() && value.matches("[a-zA-Z0-9]+");}
    static boolean isNumeric(String value){ return value !=null && !value.isEmpty() && value.matches("[0-9]+");}

    public static String getEndpointFromUri(String value){
        String replaced = value.replaceAll(".*:","");
        replaced = replaced.replaceAll("@.*","");
        return replaced;
    }

    public static String getPrivateIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        if (useIPv4) {
                            if (addr instanceof Inet4Address)
                                return sAddr;
                        } else {
                            if (!(addr instanceof Inet4Address)) {
                                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                                return delim<0 ? sAddr : sAddr.substring(0, delim);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    public static HashMap<String, String> getConnectedDeviceDetails(Context context, boolean useIPv4) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        HashMap<String, String> connectedDeviceDetails = new HashMap<>();
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnected()) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    connectedDeviceDetails.put(CONNECTED_DEVICE_TYPE, "Wifi");
                    connectedDeviceDetails.put("IP", getPrivateIPAddress(useIPv4));
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    connectedDeviceDetails.put(CONNECTED_DEVICE_TYPE, "MOBILE");
                    connectedDeviceDetails.put("IP", getPrivateIPAddress(useIPv4));
                }
            }
        }
        return connectedDeviceDetails;
    }

}

