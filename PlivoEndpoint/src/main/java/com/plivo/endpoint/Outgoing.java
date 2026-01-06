package com.plivo.endpoint;

import static com.plivo.endpoint.Global.CALL;
import static com.plivo.endpoint.Global.JWT_HEADER;

import android.text.TextUtils;


import androidx.annotation.NonNull;

import java.util.*;

public class Outgoing extends IO {
    private final Endpoint endpoint;
    SipController sipController;

    public Outgoing(Endpoint endpoint, SipController sipController) {
        super(sipController);
        this.endpoint = endpoint;
        this.sipController = sipController;
        isOnMute = false;

    }

    /**
     * Call an endpoint.
     * @param dest destination to which call will be sent
     * @return true if call was successfull
     */
    public boolean call(String dest) {
        String sipUri;
        Log.D("call " + dest);
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(CALL,"Call Cannot be Placed. No internet.");
            return false;
        }
        if (TextUtils.isEmpty(dest)) {
            Log.InfoLogs(CALL,"Call Cannot be Placed. Entered SIP endpoint is empty.");
            return false;
        }
        if (!endpoint.getRegistered()) {
            Log.InfoLogs(CALL,"Cannot make call() without endpoint already logged in.");
            return false;
        }

        if (endpoint.isCallRunning()) {
            Log.InfoLogs(CALL,"Cannot make call() when there is another call is active.");
            return false;
        }

        if(!Utils.validateDestination(dest)){
            Log.InfoLogs(CALL,"Cannot make call() Invalid destination");
            return false;
        }

        if(!dest.startsWith("sip:")){
            sipUri = "sip:" + dest + "@" + Global.DOMAIN;
        } else {
            sipUri = dest + "@" + Global.DOMAIN;
        }
        setToContact(sipUri);

        try {
            Log.D("Call Placed : "+ sipUri);
            return  makeCall(dest);
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
            Log.E("errload loading plivo:" + ule);
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("call failed");
        }

        setActive(false);
        return false;
    }

    /* Call method with headers */
    /* the map during initialization should be ConcurrentHashMap */
    public boolean call(String dest, Map<String, String> headers) {
        String sipUri;
        Log.D("callH " + dest + "headers:" + headers);
        if (!NetworkChangeReceiver.isConnected()) {
            return false;
        }
        if (TextUtils.isEmpty(dest)) {
            Log.InfoLogs(CALL,"Call Cannot be Placed. Entered SIP endpoint is empty.");
            return false;
        }
        if (!endpoint.getRegistered()) {
            Log.InfoLogs(CALL,"Cannot make callH() without endpoint already logged in.");
            return false;
        }

        if (endpoint.isCallRunning()) {
            Log.InfoLogs(CALL,"Cannot make call() when there is another call is active.");
            return false;
        }

        if(!Utils.validateDestination(dest)){
            Log.InfoLogs(CALL,"Cannot make call() Invalid destination");
            return false;
        }
        if(!dest.startsWith("sip:")){
            sipUri = "sip:" + dest + "@" + Global.DOMAIN;
        } else {
            sipUri = dest + "@" + Global.DOMAIN;
        }
        setToContact(sipUri);

        if (!Utils.validateCallHeaders(headers)) {
            Log.W("No Valid Header. Placing call without headers..");
        }

        String headers_str = Utils.mapToString(headers);
        try {
            Log.D("Call Placed");
            if(TextUtils.isEmpty(headers_str)){
                return makeCall(dest);
            } else {
                return makeCallH(dest, headers);
            }
        } catch (UnsatisfiedLinkError ule) {
            ule.printStackTrace();
            Log.E("errload loading plivo:" + ule);
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("callH failed");
        }

        setActive(false);
        return false;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    @NonNull
    public String toString() {
        return "[Plivo Outgoing Call]callId = " + this.callId + ". to = " + this.toContact;
    }

    private boolean makeCall(String dest){
        HashMap<String, String> headers = new HashMap<>();
        return makeCallH(dest,headers);
    }

    private boolean makeCallH(String dest, Map<String, String> headers){
        if(headers.isEmpty())  Log.InfoLogs(CALL, "Outgoing call initiated for : "+ sipController.username + " without header");
        else Log.InfoLogs(CALL, "Outgoing call initiated for : "+ sipController.username + " to destination:" + dest + " with header: "+ headers);
        if(endpoint.isLoginWithToken()){
            if(endpoint.isOutgoingGrant()) headers.put(JWT_HEADER,endpoint.getJwtAccessToken());
            else{
                Log.InfoLogs(CALL, "Outgoing call permission not granted.");
                endpoint.eventListener.onPermissionDenied("INVALID_ACCESS_TOKEN_GRANTS");
                return false;
            }
        }
        endpoint.eventListener.onOutgoingCall(this);
        setActive(true);
        //Create Local Peer connection
        sipController.createLocalPeerConnection();
        //Create Local Offer
        sipController.createLocalOffer(dest, headers);
        return true;
    }


}
