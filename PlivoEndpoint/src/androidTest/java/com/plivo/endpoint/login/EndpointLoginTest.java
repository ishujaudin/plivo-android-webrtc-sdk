package com.plivo.endpoint.login;

import static com.google.common.truth.Truth.assertThat;
import static com.plivo.endpoint.testutils.TestConstants.LOGIN_TEST_ENDPOINT;
import static com.plivo.endpoint.testutils.TestConstants.TEST_TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.plivo.endpoint.Endpoint;
import com.plivo.endpoint.EventListener;
import com.plivo.endpoint.testutils.SynchronousExecutor;
import com.plivo.endpoint.testutils.TestConstants;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EndpointLoginTest{
    public static final long LOGIN_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long ASYNC_LOGIN_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
    private static final long ASYNC_LOGOUT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);


    @Mock
    private EventListener eventListener;

    private Endpoint endpoint;

    private Context context;

    private final SynchronousExecutor bkgTask = new SynchronousExecutor();


    @Before
    public void setUp() {
        eventListener = mock(EventListener.class);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
//        HashMap<String, Object> options = new HashMap<String, Object>() {{
//            put("enableTracking", true);
//            put("context", context);
//        }};
        endpoint = Endpoint.newInstance(context,true, eventListener);

    }

    @After
    public void tearDown() throws InterruptedException {
        Thread.sleep(2000);
        endpoint.resetEndpoint();
        endpoint = null;
    }

    @Test
    public void t01_endpoint_isInitialized_test() {

        assertThat(endpoint).isNotNull();

    }

    //Login
    @Test
    public void t02_endpoint_login_success_test() {
        endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second, TEST_TOKEN);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();
        assertThat(endpoint.logout()).isTrue();

    }

    // Registered
    @Test
    public void t03_endpoint_isRegistered_test() {

        endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second, TEST_TOKEN);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();
        assertThat(endpoint.getRegistered()).isTrue();
        assertThat(endpoint.logout()).isTrue();
    }

    // Logout
    @Test
    public void t04_endpoint_logout_success_test() {
        endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second, TEST_TOKEN);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();
        assertThat(endpoint.logout()).isTrue();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();
    }


    @Test
    public void t05_endpoint_isNotRegistered_test() {
        assertThat(endpoint.getRegistered()).isFalse();
    }

    @Test
    public void t06_endpoint_login_failure_test() {
        endpoint.login("BLAH_BLAH", "12345");
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLoginFailed();
    }

    @Test
    public void t07_endpoint_async_login_success_test() {
        bkgTask.execute(() -> endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second, TEST_TOKEN));
        verify(eventListener, timeout(ASYNC_LOGIN_TIMEOUT)).onLogin();
        bkgTask.execute(() -> endpoint.logout());
        verify(eventListener, timeout(ASYNC_LOGOUT_TIMEOUT)).onLogout();
    }

    @Test
    public void t08_endpoint_async_logout_success_test() {
        bkgTask.execute(() -> endpoint.login(LOGIN_TEST_ENDPOINT.first, LOGIN_TEST_ENDPOINT.second, TEST_TOKEN));
        verify(eventListener, timeout(ASYNC_LOGIN_TIMEOUT)).onLogin();
        bkgTask.execute(() -> endpoint.logout());
        verify(eventListener, timeout(ASYNC_LOGOUT_TIMEOUT)).onLogout();
    }

    @Test
    public void t09_endpoint_async_login_failure_test() {
        bkgTask.execute(() -> endpoint.login("BLAH_BLAH", "12345"));
        verify(eventListener, timeout(ASYNC_LOGIN_TIMEOUT)).onLoginFailed();
    }

    @Test
    public void t10_endpoint_login_emptyUsername_failure_test(){
        endpoint.login("", "12345");
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLoginFailed();
        endpoint.resetEndpoint();
    }

    @Test
    public void t11_endpoint_login_validJWT_success_test(){
        String body = "{\n    \"iss\": \"" + TestConstants.JWT_AUTH_ID + "\",\n    \"sub\": \"abhi\",\n    \"per\": {\n        \"voice\": {\n            \"incoming_allow\": true,\n            \"outgoing_allow\": true\n        }\n    }\n    \n}\n";
        String jwt = getRequiredJWTtoken(body);
        endpoint.loginWithJwtToken(jwt);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();
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
    public void t12_endpoint_login_validJWT_devTokenNull_success_test(){
        String body = "{\n    \"iss\": \""+ TestConstants.JWT_AUTH_ID +"\",\n    \"sub\": \"abhi\",\n    \"per\": {\n        \"voice\": {\n            \"incoming_allow\": true,\n            \"outgoing_allow\": true\n        }\n    }\n    \n}\n";
        String jwt = getRequiredJWTtoken(body);
        endpoint.loginWithJwtToken(
                jwt,
                null
        );
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();
        endpoint.logout();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();
    }

    @Test
    public void t13_endpoint_login_invalidJWT_failure_test(){
        endpoint.loginWithJwtToken(".eyJhcHAiOC-rOvVT2KhK-RUzultLZmUvaHp_EeU");
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLoginFailed("INVALID_ACCESS_TOKEN");
    }

    @Test
    public void t14_endpoint_login_emptyJWT_failure_test(){
        endpoint.loginWithJwtToken("");
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLoginFailed("INVALID_ACCESS_TOKEN");
    }

    @Test
    public void t15_endpoint_loginWithJWT_isRegistered_success_test() {
        String body = "{\n    \"iss\": \""+ TestConstants.JWT_AUTH_ID +"\",\n    \"sub\": \"abhi\",\n    \"per\": {\n        \"voice\": {\n            \"incoming_allow\": true,\n            \"outgoing_allow\": true\n        }\n    }\n    \n}\n";
        String jwt = getRequiredJWTtoken(body);
        endpoint.loginWithJwtToken(jwt);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLogin();
        assertThat(endpoint.getRegistered()).isTrue();
        assertThat(endpoint.logout()).isTrue();
        verify(eventListener,timeout(LOGIN_TIMEOUT)).onLogout();
    }

    //to test this while generating JWT token pass sub as null
    @Test
    public void t16_endpoint_loginWithJWT_invalidSub_failure_test() {
        String body = "{\n    \"iss\": \""+ TestConstants.JWT_AUTH_ID +"\",\n    \"sub\": null,\n    \"per\": {\n        \"voice\": {\n            \"incoming_allow\": true,\n            \"outgoing_allow\": true\n        }\n    }\n    \n}\n";
        String jwt = getRequiredJWTtoken(body);
        endpoint.loginWithJwtToken(jwt);
        verify(eventListener, timeout(LOGIN_TIMEOUT)).onLoginFailed("INVALID_ACCESS_TOKEN");
    }
}