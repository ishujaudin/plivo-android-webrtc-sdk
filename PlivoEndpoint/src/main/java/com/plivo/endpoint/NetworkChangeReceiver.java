package com.plivo.endpoint;

import static com.plivo.endpoint.Global.NETWORK_CHANGE;
import static com.plivo.endpoint.Utils.getPrivateIPAddress;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Pair;

import java.util.Date;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class NetworkChangeReceiver extends BroadcastReceiver {

    static String CONNECTED_DEVICE_TYPE = "type";
    private static boolean isConnected = true;
    private Date lastNetworkChange;
    private HashMap<String, String>lastNetworkInfo = null;
    private boolean firstTimeCall;
    private NetworkInfo netInfo;

    public NetworkChangeReceiver() {
        lastNetworkChange = new Date(System.currentTimeMillis());
        firstTimeCall = true;
    }

    public static boolean isConnected() {
        if (!isConnected) {
            Log.E("Network not available. Please check your network connection.");
        }
        return isConnected;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.D("NetworkChangeReceiver " + "Network changed");
        if(lastNetworkInfo == null){
            lastNetworkInfo = Utils.getConnectedDeviceDetails(context, true);
        }

        if (!firstTimeCall) {
            if (!Global.isJniLoaded) {
                Log.D("PlivoSDK is not loaded yet!");
                return;
            }
            try {
                isOnline(context);
            } catch (UnsatisfiedLinkError ule) {
                ule.printStackTrace();
                Log.E("errload loading plivo:" + ule);
            } catch (Exception e) {
                e.printStackTrace();
                Log.E("handleIPChange failed");
            }
        }
        firstTimeCall = false;
    }

    private void isOnline(Context context) {
        if (context == null) return;

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            netInfo = cm.getActiveNetworkInfo();
            Log.InfoLogs(NETWORK_CHANGE, "Network change initiated");



            HashMap<String, String> currentNetworkInfo = Utils.getConnectedDeviceDetails(context, true);
            isConnected = !currentNetworkInfo.isEmpty();
            if(!isConnected) return;
            if (Endpoint.isCreated) {
                Date currentTime = new Date();
                if (lastNetworkChange != null) {
                    long diffInMs = currentTime.getTime() - lastNetworkChange.getTime();
                    long diffInSec = TimeUnit.MILLISECONDS.toSeconds(diffInMs);
                    lastNetworkChange = currentTime;
                    if (diffInSec <= 5) {
                        Log.D("Frequent network change");
                    }
                    if (checkIfNewDeviceConnected(lastNetworkInfo, currentNetworkInfo)) {
                        Log.D("Network change : registration requested by Endpoint");
                        Endpoint endpoint = Endpoint.getInstance();
                        endpoint.networkChange();
                        getPrivateIPAddress(true);
                        Log.InfoLogs(NETWORK_CHANGE, "The network changed from "+ lastNetworkInfo + " to "+ currentNetworkInfo);
                    }
                }
            }
            lastNetworkInfo = currentNetworkInfo;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkIfNewDeviceConnected(HashMap<String, String> lastNetworkInfo, HashMap<String, String> currentNetworkInfo) {
        if(!Objects.equals(lastNetworkInfo.get(CONNECTED_DEVICE_TYPE), currentNetworkInfo.get(CONNECTED_DEVICE_TYPE))){
            return true;
        } else {
            return (!Objects.equals(currentNetworkInfo.get("IP"), lastNetworkInfo.get("IP")));
        }
    }
}
