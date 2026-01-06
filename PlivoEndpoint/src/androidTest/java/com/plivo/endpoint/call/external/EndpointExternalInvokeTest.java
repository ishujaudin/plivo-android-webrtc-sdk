package com.plivo.endpoint.call.external;

import static com.google.common.truth.Truth.assertThat;
import static com.plivo.endpoint.call.EndpointOutgoingCallTest.ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT;
import static com.plivo.endpoint.login.EndpointLoginTest.LOGIN_TIMEOUT;
import static com.plivo.endpoint.testutils.TestConstants.LOGIN_TEST_ENDPOINT;
import static com.plivo.endpoint.testutils.TestConstants.PLIVO_OUTGOING_ENDPOINT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.plivo.endpoint.Endpoint;
import com.plivo.endpoint.EventListener;
import com.plivo.endpoint.Outgoing;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@RunWith(AndroidJUnit4.class)
public class EndpointExternalInvokeTest {

//    @Rule
//    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
//            Manifest.permission.RECORD_AUDIO,
//            Manifest.permission.READ_PHONE_STATE,
//            Manifest.permission.READ_CONTACTS
//    );

    @Mock
    EventListener eventListener;

    @Mock
    Outgoing outgoing;

    Endpoint endpoint;

    @Before
    public void setUp() {
        eventListener = mock(EventListener.class);
        outgoing = mock(Outgoing.class);


        if (endpoint == null) {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            endpoint = Endpoint.newInstance(context, true, eventListener);
        }
        assertThat(endpoint).isNotNull();

        endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();

        outgoing = endpoint.createOutgoingCall();
        assertThat(outgoing).isNotNull();
    }


    @After
    public void tearDown() throws InterruptedException {
        Thread.sleep(5000);
        endpoint.resetEndpoint();
        endpoint = null;
        outgoing = null;
    }

    // Outgoing
    @Test
    public void endpoint_make_outcall_answer_test() {
        assertThat(outgoing.call(PLIVO_OUTGOING_ENDPOINT)).isTrue();
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCall(outgoing);
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCallAnswered(outgoing);
        outgoing.hangup();
    }

    @Test
    public void endpoint_make_outcall_answer_hangup_test() {
        assertThat(outgoing.call(PLIVO_OUTGOING_ENDPOINT)).isTrue();
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCall(outgoing);
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCallAnswered(outgoing);
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCallHangup(outgoing);
        outgoing.hangup();
    }

    @Test
    public void endpoint_make_outcall_reject_test() {
        assertThat(outgoing.call(PLIVO_OUTGOING_ENDPOINT)).isTrue();
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCall(outgoing);
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCallRejected(outgoing);
        outgoing.hangup();
    }
}
