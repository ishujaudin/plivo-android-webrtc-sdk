package com.plivo.endpoint.call;

import static com.google.common.truth.Truth.assertThat;
import static com.plivo.endpoint.login.EndpointLoginTest.LOGIN_TIMEOUT;
import static com.plivo.endpoint.testutils.TestConstants.LOGIN_TEST_ENDPOINT;
import static com.plivo.endpoint.testutils.TestConstants.TEST_TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.plivo.endpoint.Endpoint;
import com.plivo.endpoint.EventListener;
import com.plivo.endpoint.testutils.SynchronousExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EndpointOutgoingInitTest {

    @Mock
    private EventListener eventListener;


    private Endpoint endpoint;

    private Context context;

    private final SynchronousExecutor bkgTask = new SynchronousExecutor();


    @Before
    public void setUp() {
        eventListener = mock(EventListener.class);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        endpoint = Endpoint.newInstance(context,true, eventListener);
        endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second,TEST_TOKEN);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();

    }

    @After
    public void tearDown(){
        reset(eventListener);
        endpoint.resetEndpoint();
        endpoint = null;
    }
    
    // Outgoing init
    @Test
    public void t1_endpoint_create_outgoing_test() {
        assertThat(endpoint.createOutgoingCall()).isNotNull();
    }

    @Test
    public void t2_endpoint_create_outgoing_async_test() {
        bkgTask.execute(() -> assertThat(endpoint.createOutgoingCall()).isNotNull());
    }

    @Test
    public void t3_endpoint_create_outgoing_fail() {
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();
        assertThat(endpoint.createOutgoingCall()).isNull();
    }

    @After
    public void endpoint_reset(){
        endpoint.resetEndpoint();
    }
}
