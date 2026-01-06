package com.plivo.endpoint;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;

@RunWith(MockitoJUnitRunner.class)
public class CallInsightsTest {
    CallInsights callInsights;
    private static final String TAG = "CallInsightsTest";


    @Before
    public void setUp() throws Exception {
        if(GlobalVarTest.isJWT){
            callInsights = new CallInsights("some_auth_id", "", "phone.plivo.com", new HashMap<>(),  new HashMap<String, String>(){{
                put("jwt", GlobalVarTest.JWT_ACCESS_TOKEN); }});
        }else {
            callInsights = new CallInsights("some_auth_id", "some_auth_token", "phone.plivo.com", new HashMap<>(), new HashMap<String, String>());
        }
    }

    @Test
    public void initRTPStats() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        callInsights.initRTPStats(appContext);
        assertNotNull(callInsights.getRtpStats());
    }

    @Test
    public void initOptions() {
        callInsights.initOptions(new HashMap<String, Object>());
        assertNotNull(callInsights.getOptions());
    }

    @Test
    public void sendAnswerEvent_false() {
        SignallingStats signallingStats = new SignallingStats();
        ArrayList<JSONObject> statsBuffer = callInsights.getStatBuffer();
        callInsights.setStatsKey(GlobalVarTest.CALL_STAT_KEY);
        callInsights.sendAnswerEvent(signallingStats, false);
        assertEquals(0, statsBuffer.size());
    }

    @Test
    public void sendAnswerEvent_true() {
        SignallingStats signallingStats = new SignallingStats();
        ArrayList<JSONObject> statsBuffer = callInsights.getStatBuffer();
        callInsights.setStatsKey(GlobalVarTest.CALL_STAT_KEY);
        callInsights.sendAnswerEvent(signallingStats, true);
        assertEquals(0, statsBuffer.size());
    }

    @Test
    public void sendSummaryEvent() {
        SignallingStats signallingStats = new SignallingStats();
        ArrayList<JSONObject> statsBuffer = callInsights.getStatBuffer();
        callInsights.setStatsKey(GlobalVarTest.CALL_STAT_KEY);
        callInsights.sendSummaryEvent(signallingStats);
        assertEquals(0, statsBuffer.size());
    }

    @Test
    public void sendRingingEvent() {
        SignallingStats signallingStats = new SignallingStats();
        ArrayList<JSONObject> statsBuffer = callInsights.getStatBuffer();
        callInsights.setStatsKey(GlobalVarTest.CALL_STAT_KEY);
        callInsights.sendRingingEvent(signallingStats);
        assertEquals(0, statsBuffer.size());
    }

    @Test
    public void sendFeedbackEvent() {
        JSONObject jsonObject = new JSONObject();
        callInsights.setStatsKey(GlobalVarTest.CALL_STAT_KEY);
        callInsights.sendFeedbackEvent(jsonObject);
        ArrayList<JSONObject> statsBuffer = callInsights.getStatBuffer();
        Log.d(TAG, "sendFeedbackEvent: "+statsBuffer.size());
        assertEquals(0, statsBuffer.size());
    }

    @Test
    public void stopTimer() {
    }
}