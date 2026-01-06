package com.plivo.endpoint;

public class GlobalVarTest {

    public static final boolean isJWT = true;
    public static final String JWT_ACCESS_TOKEN = "some_jwt_access_token";
    public static final String JWT_CALL_STAT_KEY = "some_jwt_call_stat_key";
    public static final String CALL_STAT_KEY_UP = "some_call_stat_key";
    public static final String CALL_STAT_KEY = isJWT? JWT_CALL_STAT_KEY:CALL_STAT_KEY_UP;

    
}
