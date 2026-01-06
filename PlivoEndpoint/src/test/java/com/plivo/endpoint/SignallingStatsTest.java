package com.plivo.endpoint;

import static org.junit.Assert.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Date;
import java.util.HashMap;

public class SignallingStatsTest {
    SignallingStats signallingStats;

    @Before
    public void setUp() throws Exception {
        signallingStats = new SignallingStats();
    }

    @Test
    public void setAnswerTime() {
        signallingStats.setAnswerTime();
        assertNotNull(new Date().getTime() + "", signallingStats.getAnswerTime());
    }

    @Test
    public void setCallConfirmedTime() {
        signallingStats.setCallConfirmedTime();
        assertNotNull(signallingStats.getCallConfirmedTime());
    }

    @Test
    public void getCallConfirmedTime() {
        signallingStats.setCallConfirmedTime();
        assertNotNull(signallingStats.getCallConfirmedTime());
    }

    @Test
    public void setHangupTime() {
        signallingStats.setHangupTime();
        assertNotNull(signallingStats.getHangupTime());
    }

    @Test
    public void getPostDialDelay_null() {
        assertNull(signallingStats.getPostDialDelay());
    }

    @Test
    public void setPostDialDelay() {
        signallingStats.setPostDialDelay();
        assertNotNull(signallingStats.getPostDialDelay());
    }

    @Test
    public void setRingStartTime() {
        signallingStats.setRingStartTime();
        assertNotNull(signallingStats.getRingStartTime());
    }

    @Test
    public void getXCallUUID_null() {
        assertNull(signallingStats.getXCallUUID());
    }

    @Test
    public void setXCallUUID() {
        signallingStats.setXCallUUID("abcd");
        assertEquals("abcd",signallingStats.getXCallUUID());
    }

    @Test
    public void getSignallingData() throws JSONException {
        assertNotNull(signallingStats.getSignallingData().get("call_initiation_time"));
    }

    @Test
    public void setCallProgressTime() {
        signallingStats.setCallProgressTime();
        assertNotNull(signallingStats.getCallProgressTime());
    }

    @Test
    public void getCallUUID() {
        assertNull(signallingStats.getCallUUID());
    }

    @Test
    public void setCallUUID() {
        signallingStats.setCallUUID("abcd");
        assertEquals("abcd", signallingStats.getCallUUID());
    }
}