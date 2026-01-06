package com.plivo.endpoint.call;

import static com.google.common.truth.Truth.assertThat;
import static com.plivo.endpoint.login.EndpointLoginTest.LOGIN_TIMEOUT;
import static com.plivo.endpoint.testutils.TestConstants.INVALID_TEST_NUM;
import static com.plivo.endpoint.testutils.TestConstants.INVALID_TEST_NUM2;
import static com.plivo.endpoint.testutils.TestConstants.LOGIN_TEST_ENDPOINT;
import static com.plivo.endpoint.testutils.TestConstants.MOBILE_TEST_NUM;
import static com.plivo.endpoint.testutils.TestConstants.PLIVO_OUTGOING_ENDPOINT;
import static com.plivo.endpoint.testutils.TestConstants.TEST_TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.plivo.endpoint.Endpoint;
import com.plivo.endpoint.EventListener;
import com.plivo.endpoint.Outgoing;
import com.plivo.endpoint.SipController;
import com.plivo.endpoint.testutils.SynchronousExecutor;
import com.plivo.endpoint.testutils.TestConstants;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EndpointOutgoingCallTest {
    public static final long ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(60);
    public static final long ON_OUTGOING_REJECT_CB_RECEIVE_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

    /*@Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS
    );*/

    @Mock
    EventListener eventListener;

    Endpoint endpoint;

    @Mock
    Outgoing outgoing;

    private SynchronousExecutor bkgTask = new SynchronousExecutor();

    @BeforeClass
    public static void create() {
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        if (endpoint == null) {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            endpoint = Endpoint.newInstance(context, true, eventListener);
        }
        assertThat(endpoint).isNotNull();

        endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second,TEST_TOKEN);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();

        outgoing = endpoint.createOutgoingCall();
        assertThat(outgoing).isNotNull();
    }

    @After
    public void tearDown() throws InterruptedException {
        Thread.sleep(2000);
        endpoint.resetEndpoint();
        endpoint = null;
        outgoing = null;
    }

    // Outgoing call

    @Test
    public void t01_endpoint_make_outcall_to_plivo_endpoint_test() {
        makeOutCallAndHangupVerify(PLIVO_OUTGOING_ENDPOINT);
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();

    }

    @Test
    public void t02_endpoint_make_outcall_to_plivo_endpoint_async_test() {
        bkgTask.execute(() -> makeOutCallAndHangupVerify(PLIVO_OUTGOING_ENDPOINT));
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();


    }

    @Test
    public void t03_endpoint_make_outcall_to_mobile_test() {
        makeOutCallAndHangupVerify(MOBILE_TEST_NUM);
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();


    }

    @Test
    public void t04_endpoint_make_outcall_to_mobile_async_test() {
        bkgTask.execute(() -> makeOutCallAndHangupVerify(MOBILE_TEST_NUM));
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();


    }

    // Needs custom server to test it out.
//    @Test
//    public void endpoint_make_outcall_to_plivo_endpoint_with_custom_headers_test() {
//        Map<String, String> extraHeaders = new HashMap<>();
//        extraHeaders.put("X-PH-Header1", "12345");
//        extraHeaders.put("X-PH-Header2", "34567");
//
//        assertThat(outgoing.callH(PLIVO_ENDPOINT_TEST_NUM, extraHeaders)).isTrue();
//        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCall(outgoing);
//        hangupOutCallAndVerify();
//    }

    @Test
    public void t05_endpoint_make_outcall_to_plivo_endpoint_with_no_headers_test() {
        assertThat(outgoing.call(PLIVO_OUTGOING_ENDPOINT, null)).isTrue();
        outgoing.hangup();
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();


    }

    @Test
    public void t06_endpoint_make_outcall_to_invalid_endpoint_test() {
        makeOutcallInvalidStateVerify(INVALID_TEST_NUM);
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();

    }

    @Test
    public void t07_endpoint_make_outcall_to_invalid_endpoint_async_test() {
        bkgTask.execute(() -> makeOutcallInvalidStateVerify(INVALID_TEST_NUM));
        bkgTask.execute(() -> endpoint.logout());
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogout();


    }

    @Test
    public void t08_endpoint_make_outcall_to_invalid_endpoint2_test() {
        makeOutcallInvalidNumberVerify(INVALID_TEST_NUM2);
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();


    }


    //Verify
    @Test
    public void t09_endpoint_make_outcall_to_invalid_endpoint2_async_test() {
        bkgTask.execute(() -> makeOutcallInvalidNumberVerify(INVALID_TEST_NUM2));
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();


    }

    private void makeOutCallAndHangupVerify(String num) {
        assertThat(outgoing.call(num)).isTrue();
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCall(outgoing);
        hangupOutCallAndVerify();

    }

    private void hangupOutCallAndVerify() {
        outgoing.hangup();
        verify(eventListener, timeout(ON_OUTGOING_REJECT_CB_RECEIVE_TIMEOUT)).onOutgoingCallHangup(outgoing);
    }

    private void makeOutcallInvalidStateVerify(String num) {
        outgoing.call(num);
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCallInvalid(outgoing);
    }

    private void makeOutcallInvalidNumberVerify(String num) {
        assertThat(outgoing.call(num)).isTrue();
        verify(eventListener, timeout(ON_OUTGOING_CALL_CB_RECEIVE_TIMEOUT)).onOutgoingCallInvalid(outgoing);
    }

    @Test
    public void t10_testCall_success_test(){
        Outgoing outgoing = endpoint.createOutgoingCall();
        outgoing.call("+919728065388");
        verify(eventListener).onOutgoingCall(outgoing);
        outgoing.hangup();
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();

    }

    @Test
    public void t11_OutGoing_not_allowed_call_tokenLogin_failure_test(){
        String body = "{\n    \"iss\": \""+ TestConstants.JWT_AUTH_ID +"\",\n    \"sub\": \"abhi\",\n    \"per\": {\n        \"voice\": {\n            \"incoming_allow\": true,\n            \"outgoing_allow\": false\n        }\n    }\n    \n}\n";
        String jwt = getRequiredJWTtoken(body);
        endpoint.loginWithJwtToken(jwt);
        Outgoing outgoing = endpoint.createOutgoingCall();
        outgoing.call("+9197280");
        verify(eventListener).onPermissionDenied("INVALID_ACCESS_TOKEN_GRANTS");
        outgoing.hangup();
    }

    private String getRequiredJWTtoken(String bodyContent) {
        try {
            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, bodyContent);
            Request request = new Request.Builder()
                    .url("https://api.plivo.com/v1/Account/"+ TestConstants.JWT_AUTH_ID +"/JWT/Token")
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Basic "+ TestConstants.JWT_BEARER_TOKEN)
                    .build();
            Response response = client.newCall(request).execute();
            JSONObject json = new JSONObject(response.body().string());
            return json.getString("token");
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void t12_JWTToken_login_header_should_be_added_success_test(){
        Endpoint endpoint = mock(Endpoint.class);
        SipController sipController = mock(SipController.class);
        when(endpoint.isLoginWithToken()).thenReturn(true);
        when(endpoint.getRegistered()).thenReturn(true);
        when(endpoint.isOutgoingGrant()).thenReturn(true);
        Outgoing outgoing = new Outgoing(endpoint, sipController);
        outgoing.call("+9197280");
        verify(endpoint).getJwtAccessToken();
        outgoing.hangup();
    }

    @Test
    public void t13_endpoint_login_header_should_not_be_added_failure_test(){
        Endpoint endpoint = mock(Endpoint.class);
        SipController sipController = mock(SipController.class);
        when(endpoint.isLoginWithToken()).thenReturn(false);
        when(endpoint.getRegistered()).thenReturn(true);
        Outgoing outgoing = new Outgoing(endpoint, sipController);
        outgoing.call("+912134");
        verify(endpoint,never()).getJwtAccessToken();
    }

    @After
    public void endpoint_reset(){
        endpoint.resetEndpoint();
    }
}
