package com.plivo.endpoint;


import static com.plivo.endpoint.Global.CALL;

import java.util.HashMap;
import java.util.Map;

public class Incoming extends IO {
    private String fromContact;
    private HashMap<String, String> header;
    private SipController sipController;
    private String stirVerification;

    public Incoming( String fromContact, String toContact, HashMap<String, String> header, String stirVerification, SipController sipController) {
        super(sipController);
        this.fromContact = fromContact;
        this.toContact = toContact;
        this.header = header;
        this.isOnMute = false;
        this.sipController = sipController;
        this.stirVerification = stirVerification;
    }

    /**
     * Answer an incoming call
     */
    public boolean answer() {
        Log.D("answer" );
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(CALL, "Cannot answer: No internet connection");
            return false;
        }
        if (isActive()) {
            Log.InfoLogs(CALL, "Cannot answer: Call is already answered");
            return false;
        }

        try {
            sipController.doAnswer();
            setActive(true);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("answer failed");
        }
        setActive(false);
        return false;
    }

    public boolean reject() {
        Log.D("reject");
        if (!NetworkChangeReceiver.isConnected()) {
            Log.InfoLogs(CALL, "Cannot reject: No internet connection");
            return false;
        }
        if (isActive()) {
            Log.InfoLogs(CALL, "Cannot reject: Call is already active. Try hangup");
            return false;
        }

        try {
            sipController.reject();
            Log.InfoLogs(CALL, "Call rejected");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            Log.E("reject failed");
        }
        return false;
    }

    public HashMap<String, String> getHeader() {
        return header;
    }

    public Map<String, String> getHeaderDict(){
        return header;
    }

    // From format: <sip:android1181024115518@phone.plivo.com>;tag=1r7387ubi7
    public String getFromContact() {
        return fromContact;
    }

    public String getFromSip() {
        String from = getFromContact();
        if (!from.contains("<") || !from.contains("@")) return null;

        String sipName = from.substring(from.indexOf("<") + 1, from.indexOf("@"));
        Log.D("getFromSip: " + sipName);
        return sipName;
    }

    public String getToSip() {
        String to = getToContact();
        if (!to.contains("<") || !to.contains("@")) return null;

        String sipId = to.substring(to.indexOf("<") + 1, to.indexOf("@"));
        Log.D("getToSip: " + sipId);
        return sipId;
    }

    public String getCallId() {
        return callId;
    }

    public String getStirVerification() {
        return stirVerification;
    }

}
