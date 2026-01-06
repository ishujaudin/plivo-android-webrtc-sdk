package com.plivo.endpoint;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.PriorityQueue;

public class LogTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void E_test() {
        Log.deviceLog = new PriorityQueue<>();
        assertEquals(0, Log.deviceLog.size());
        Log.E("test");
        assertEquals(1, Log.deviceLog.size());
    }

    @Test
    public void W_test() {
        Log.deviceLog = new PriorityQueue<>();
        assertEquals(0, Log.deviceLog.size());
        Log.W("test");
        assertEquals(1, Log.deviceLog.size());
    }

    @Test
    public void I_test() {
        Log.deviceLog = new PriorityQueue<>();
        assertEquals(0, Log.deviceLog.size());
        Log.I("test");
        assertEquals(1, Log.deviceLog.size());
    }

    @Test
    public void updateDeviceLog() {
        Log.deviceLog = new PriorityQueue<>();
        assertEquals(0, Log.deviceLog.size());
        Log.updateDeviceLog("debug", "example log");
        assertEquals(1, Log.deviceLog.size());
    }
}