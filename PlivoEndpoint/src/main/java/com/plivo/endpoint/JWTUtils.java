package com.plivo.endpoint;

import static com.plivo.endpoint.Global.APP_KEY;
import static com.plivo.endpoint.Global.EXPIRY_KEY;
import static com.plivo.endpoint.Global.INCOMING_ALLOW_KEY;
import static com.plivo.endpoint.Global.ISS_KEY;
import static com.plivo.endpoint.Global.NBF_KEY;
import static com.plivo.endpoint.Global.OUTGOING_ALLOW_KEY;
import static com.plivo.endpoint.Global.PER_KEY;
import static com.plivo.endpoint.Global.SUB_KEY;
import static com.plivo.endpoint.Global.VOICE_KEY;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

public class JWTUtils {

    private static final String TAG = "JWTUtils";

    public static JSONObject decodeJWT(String JWTEncoded) throws Exception {

        try {
            String[] split = JWTEncoded.split("\\.");
            if(split.length<2) {
                throw new UnsupportedEncodingException();
            }
            String jwtJson = getJson(split[1]);
            JSONObject obj = new JSONObject(jwtJson);
            Log.D(TAG, "decoded: -"+jwtJson);

            return obj;
        } catch (UnsupportedEncodingException e) {
            Log.D(TAG, "decoded: "+ e);
        }
        return null;
    }

    private static String getJson(String strEncoded) throws UnsupportedEncodingException{
        byte[] decodedBytes = Base64.decode(strEncoded, Base64.URL_SAFE);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }

    public static boolean checkNullNTypeTokenValue(JSONObject obj) throws JSONException {
        if(obj.isNull(ISS_KEY) || obj.isNull(EXPIRY_KEY) || obj.isNull(NBF_KEY)){
            return false;
        }

        Object exp = obj.get(EXPIRY_KEY);
        Object nbf = obj.get(NBF_KEY);
        Object iss = obj.get(ISS_KEY);
        if(!(exp instanceof Integer) || !(nbf instanceof Integer) || !(iss instanceof String)){
           return false;
        }

        if(iss.toString().equals("<nil>")) return false;

        if(!obj.isNull(PER_KEY) && !obj.getJSONObject(PER_KEY).isNull(VOICE_KEY)){
            if(!obj.getJSONObject(PER_KEY).getJSONObject(VOICE_KEY).isNull(INCOMING_ALLOW_KEY)){
                Object isIncoming = obj.getJSONObject(PER_KEY).getJSONObject(VOICE_KEY).get(INCOMING_ALLOW_KEY);
                if(!(isIncoming instanceof Boolean)){
                    return false;
                }
            }else{
                return false;
            }
            if(!obj.getJSONObject(PER_KEY).getJSONObject(VOICE_KEY).isNull(OUTGOING_ALLOW_KEY)){
                Object isOutgoing = obj.getJSONObject(PER_KEY).getJSONObject(VOICE_KEY).get(OUTGOING_ALLOW_KEY);
                if(!(isOutgoing instanceof Boolean)){
                    return false;
                }
            }else{
                return false;
            }
        }

        if(!obj.isNull(SUB_KEY)){
           Object sub = obj.get(SUB_KEY);
           if(!(sub instanceof String)){
               return false;
           }
        }
        if(!obj.isNull(APP_KEY)){
            Object app = obj.get(APP_KEY);
            if(!(app instanceof String)){
                return false;
            }else return !app.toString().equals("<nil>");
        }
        return true;
    }

    public static boolean getIncomingGrantPer(JSONObject obj) {
        try {
            return obj.getJSONObject(PER_KEY).getJSONObject(VOICE_KEY).getBoolean(INCOMING_ALLOW_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean getOutgoingGrantPer(JSONObject obj) {
        try {
            return obj.getJSONObject(PER_KEY).getJSONObject(VOICE_KEY).getBoolean(OUTGOING_ALLOW_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
    public static long getNBFTime(JSONObject obj) {
        try {
            return obj.getLong(NBF_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static long getEXPTime(JSONObject obj) {
        try {
            return obj.getLong(EXPIRY_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return 0;
        }
    }
     **/

    public static String getSub(JSONObject obj) {
        try {
            return obj.getString(SUB_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getIss(JSONObject obj) {
        try {
            return obj.getString(ISS_KEY);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
