package com.plivo.endpoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.UUID;

public class SignallingStats {
    private String answerTime;
    private String callConfirmedTime;
    private final String callInitiationTime;
    private String callProgressTime;
    private String hangupTime;
    private String postDialDelay;
    private String ringStartTime;
    private String xcallUUID;
    private String callUUID;

    public SignallingStats() {
        callInitiationTime = Utils.getCurrentTimeInMilliSeconds();
    }


    public void setAnswerTime() {
        this.answerTime = Utils.getCurrentTimeInMilliSeconds();
    }

    public String getAnswerTime(){
        return this.answerTime;
    }

    public String getCallConfirmedTime() {
        return this.callConfirmedTime;
    }

    public void setCallConfirmedTime() {
        this.callConfirmedTime = Utils.getCurrentTimeInMilliSeconds();
    }

    public String getHangupTime() {
        return this.hangupTime;
    }

    public void setHangupTime() {
        this.hangupTime = Utils.getCurrentTimeInMilliSeconds();
    }

    public String getPostDialDelay() {
        return postDialDelay;
    }

    public void setPostDialDelay() {
        long delta = new Date().getTime() - Long.parseLong(callInitiationTime);
        postDialDelay = delta + "";
    }

    public String getRingStartTime() {
        return this.ringStartTime;
    }

    public void setRingStartTime() {
        this.ringStartTime = Utils.getCurrentTimeInMilliSeconds();
    }

    public String getXCallUUID() {
        return xcallUUID;
    }

    public void setXCallUUID(String xcalluuid) {
        if (xcallUUID == null || xcallUUID.equals("")) {
            if(xcalluuid != null){
                xcallUUID = xcalluuid.split("\\r\\n")[0];
            }
        }

    }

    public JSONObject getSignallingData() {
        JSONObject event = new JSONObject();
        try {
            event.put("answer_time", answerTime);
            event.put("call_progress_time", callProgressTime);
            event.put("call_confirmed_time", callConfirmedTime);
            event.put("call_initiation_time", callInitiationTime);
            event.put("hangup_time", hangupTime);
            event.put("post_dial_delay", postDialDelay);
            event.put("ring_start_time", ringStartTime);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return event;
    }

    public void setCallProgressTime() {
        this.callProgressTime = Utils.getCurrentTimeInMilliSeconds();
    }

    public String getCallProgressTime() {
        return this.callProgressTime;
    }

    public void setCallUUID(String callUUID) {
        if (this.callUUID == null || this.callUUID.equals("")) {
            if (callUUID != null && callUUID.equals(xcallUUID)) {
                this.callUUID = UUID.randomUUID().toString();
            } else {
                this.callUUID = callUUID;
            }
        }
    }

    public String getCallUUID() {
        return callUUID;
    }
}
