package com.plivo.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.webrtc.StatsReport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class RtpStatsTest {
    RtpStats rtpStats;

    @Before
    public void setUp() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        rtpStats = new RtpStats(true, appContext);

    }

    @Test
    public void getNetworkType() {
        ArrayList<String> networkType = new ArrayList<>(Arrays.asList("unknown", "mobile", "wifi"));
        String type = rtpStats.getNetworkType();
        assertTrue(networkType.contains(type));
    }

    @Test
    public void getNetworkEffectiveType() {
        assertEquals("unknown", rtpStats.getNetworkEffectiveType());
    }

    @Test
    public void getNetworkDownlinkSpeed() {
        int value = -1;
        assertTrue(rtpStats.getNetworkDownlinkSpeed()!=value);
    }

    @Test
    public void getMos() {
        assertEquals(4, (int) rtpStats.getMOS(200, 10));

    }

    @Test
    public void calculateFractionLoss_local(){
        assertEquals(0,(int) rtpStats.calculateFractionLoss(200,2000,"local"));
    }

    @Test
    public void calculateFractionLoss_remote(){
        assertEquals(0,(int) rtpStats.calculateFractionLoss(200,2000,"remote"));
    }

    @Test
    public void calculateFractionLoss_zero_packetsent(){
        assertEquals(0,(int) rtpStats.calculateFractionLoss(0,0,"remote"));
    }

    @Test
    public void calculateFractionLoss_zero_packetsent2(){
        assertEquals(1,(int) rtpStats.calculateFractionLoss(20,0,"remote"));
    }


    @Test
    public void getLocalStats() {
        StatsReport.Value[] data = new StatsReport.Value[7];
        data[0] = new StatsReport.Value("audioInputLevel", "3.0");
        data[1] = new StatsReport.Value("bytesSent", "3.0");
        data[2] = new StatsReport.Value("googRtt", "3.0");
        data[3] = new StatsReport.Value("googJitterReceived", "3.0");
        data[4] = new StatsReport.Value("packetsLost", "3");
        data[5] = new StatsReport.Value("packetsSent", "3");
        data[6] = new StatsReport.Value("ssrc", "3");
//        assertNotNull(rtpStats.getLocalStats(data));
    }

    @Test
    public void getRemoteStats() {
        StatsReport.Value[] data = new StatsReport.Value[6];
        data[0] = new StatsReport.Value("audioOutputLevel", "3.0");
        data[1] = new StatsReport.Value("bytesReceived", "3.0");
        data[2] = new StatsReport.Value("googJitterReceived", "3.0");
        data[3] = new StatsReport.Value("packetsLost", "3");
        data[4] = new StatsReport.Value("packetsReceived", "3");
        data[5] = new StatsReport.Value("ssrc", "3");
//        assertNotNull(rtpStats.getRemoteStats(data));
    }

    @Test
    public void getAudioLevels() {
        assertNotNull(rtpStats.getAudioLevels());
    }

    @Test
    public void sendAlertCallback_rtt() {
        ArrayList<Double> list = new ArrayList<>();
        list.add(920.2);
        list.add(1000.2);
        list.add(1178.2);
        list.add(1234.2);
        assertNotEquals( -1.0,rtpStats.sendAlertCallback(list, "rtt"));
    }

    @Test
    public void sendAlertCallback_mos() {
        ArrayList<Double> list = new ArrayList<>();
        list.add(1.5);
        list.add(2.0);
        assertNotEquals( -1.0,rtpStats.sendAlertCallback(list, "mos"));
    }

    @Test
    public void sendAlertCallback_jitter_local() {
        ArrayList<Double> list = new ArrayList<>();
        list.add(45.0);
        list.add(52.0);
        assertNotEquals( -1.0,rtpStats.sendAlertCallback(list, "jitter_local"));
        assertNotEquals( -1.0,rtpStats.sendAlertCallback(list, "jitter_remote"));
    }

    @Test
    public void sendAlertCallback_packectloss_local() {
        ArrayList<Double> list = new ArrayList<>();
        list.add(0.2);
        list.add(0.4);
        assertNotEquals( -1.0,rtpStats.sendAlertCallback(list, "packectloss_local"));
        assertNotEquals( -1.0,rtpStats.sendAlertCallback(list, "packectloss_remote"));
    }

//    @Test
//    public void sendMedialMetricsCallBack() {
//    }

    @Test
    public void callMediaMatrices() {
    }

    @Test
    public void checkMicrophoneAccess() {
    }

    @Test
    public void getCodec_pcmu() {
        assertEquals("pcmu" ,rtpStats.getCodec("pcmu"));
    }

    @Test
    public void getCodec_opus() {
        assertEquals("opus" ,rtpStats.getCodec("opus"));
    }

    @Test
    public void getCodec_null() {
        assertEquals("null" ,rtpStats.getCodec("null"));
    }

//    @Test
//    public void computeRTPStats() {
//    }

    @Test
    public void computeRTPStats(){
        StatsReport[] data = new StatsReport[2];
        StatsReport.Value[] values = new StatsReport.Value[8];
        values[0] = new StatsReport.Value("audioInputLevel", "3.0");
        values[1] = new StatsReport.Value("bytesSent", "3.0");
        values[2] = new StatsReport.Value("googRtt", "3.0");
        values[3] = new StatsReport.Value("googJitterReceived", "3.0");
        values[4] = new StatsReport.Value("packetsLost", "3");
        values[5] = new StatsReport.Value("packetsSent", "3");
        values[6] = new StatsReport.Value("ssrc", "3");
        values[7] = new StatsReport.Value("jitter", "3");
        data[0] = new StatsReport("123", "ssrc", new Date().getTime(), values);


        StatsReport.Value[] receviedValues = new StatsReport.Value[6];
        receviedValues[0] = new StatsReport.Value("audioOutputLevel", "3.0");
        receviedValues[1] = new StatsReport.Value("bytesReceived", "3.0");
        receviedValues[2] = new StatsReport.Value("googJitterReceived", "3.0");
        receviedValues[3] = new StatsReport.Value("packetsLost", "3");
        receviedValues[4] = new StatsReport.Value("packetsReceived", "3");
        receviedValues[5] = new StatsReport.Value("ssrc", "3");
        data[1] = new StatsReport("123", "ssrc", new Date().getTime(), receviedValues);


//        assertNotNull(rtpStats.computeRTPStats(data));
    }
}