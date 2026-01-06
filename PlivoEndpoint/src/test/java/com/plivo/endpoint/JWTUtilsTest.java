package com.plivo.endpoint;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;


public class JWTUtilsTest {
    JSONObject obj;

    @Test
    public void testDecodeJWT() throws Exception {
        obj = mockDecodeJWT(GlobalVarTest.JWT_ACCESS_TOKEN);
        Assert.assertNotNull(mockDecodeJWT(GlobalVarTest.JWT_ACCESS_TOKEN));
    }

    @Test
    public void testCheckNullNTypeTokenValue() throws Exception {
        obj = mockDecodeJWT(GlobalVarTest.JWT_ACCESS_TOKEN);
        Assert.assertTrue(JWTUtils.checkNullNTypeTokenValue(obj));
    }

    @Test
    public void testValidate_subNotAsString() throws Exception {
        obj = mockDecodeJWT("some_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }

    @Test
    public void testValidate_invalidJWT() throws Exception {
        obj = mockDecodeJWT("some_invalid_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }

    @Test
    public void testValidate_incomingAllowBoolean() throws Exception {
        obj = mockDecodeJWT("some_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }


    @Test
    public void testValidate_outgoingAllowBoolean() throws Exception {
        obj = mockDecodeJWT("some_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }

    @Test
    public void testValidate_appIDnil() throws Exception {
        obj = mockDecodeJWT("some_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }

    @Test
    public void testValidate_nbfasString() throws Exception {
        obj = mockDecodeJWT("some_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }

    @Test
    public void testValidate_expasString() throws Exception {
        obj = mockDecodeJWT("some_jwt_access_token");
        Assert.assertFalse(JWTUtils.checkNullNTypeTokenValue(obj));
    }



    /*
        mockDecode JWT as android.util.Base64.decode doesn't work
     */
    public static JSONObject mockDecodeJWT(String JWTEncoded) throws Exception {
        try {
            String[] split = JWTEncoded.split("\\.");
            if(split.length<2) {
                throw new UnsupportedEncodingException();
            }
            String jwtJson = getJson(split[1]);
            return new JSONObject(jwtJson);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getJson(String strEncoded) throws UnsupportedEncodingException{
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(strEncoded);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}