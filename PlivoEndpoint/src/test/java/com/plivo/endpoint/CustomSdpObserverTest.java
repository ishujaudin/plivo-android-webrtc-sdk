package com.plivo.endpoint;

import static org.junit.Assert.*;
import android.util.Log;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class CustomSdpObserverTest {
    CustomSdpObserver customSdpObserver;

    @Before
    public void setUp() throws Exception {
        customSdpObserver = new CustomSdpObserver("debug");
    }

    @Test
    public void onCreateSuccess() {

    }

    @Test
    public void onSetSuccess() {
    }

    @Test
    public void onCreateFailure() {
    }

    @Test
    public void onSetFailure() {
    }
}