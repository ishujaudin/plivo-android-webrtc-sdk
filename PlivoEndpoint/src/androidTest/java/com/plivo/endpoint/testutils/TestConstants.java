package com.plivo.endpoint.testutils;

import android.util.Pair;

public class TestConstants {
    public static final Pair<String, String> LOGIN_TEST_ENDPOINT =
            new Pair<>("auth_id", "auth_token");
    public static final String TEST_TOKEN = "some_test_token"; // invalid number call.status 404 || 408
    /**
     * In this outgoing endpoint, It will 3 receive incoming call. So login with this endpoint before starting test
     * First -> Answer the call and hangup.
     * Second -> Answer the call and hangup.
     * Third -> Reject the call.
     */
    public static final String PLIVO_OUTGOING_ENDPOINT = "some_outgoing_endpoint"; // todo: use original test endpoint
    public static final String MOBILE_TEST_NUM = "some_test_number"; // todo: use original test number
    public static final String INVALID_TEST_NUM = "some_invalid_test_number"; // invalid state call.status>=480 && <=489
    public static final String INVALID_TEST_NUM2 ="some_invalid_test_number";
    public static final String JWT_AUTH_ID = "some_jwt_auth_id";
    public static final String JWT_BEARER_TOKEN = "some_jwt_bearer_token";
    // invalid number call.status 404 || 408
}

