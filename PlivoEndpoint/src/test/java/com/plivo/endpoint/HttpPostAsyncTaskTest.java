package com.plivo.endpoint;

import org.junit.Before;
import org.junit.Test;

public class HttpPostAsyncTaskTest {

    HttpPostTask client1;

    @Before
    public void setUp() throws Exception {
//        JSONObject postBody = new JSONObject();
//        try {
//            postBody.put("username", "some_username");
//            postBody.put("password", "some_password");
//            postBody.put("domain", "phone.plivo.com");
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//        HttpPostAsyncTask client1 = new HttpPostAsyncTask(postBody, "POST", new HTTPRequestCallback(){
//            @Override
//            public void onFailure(int StatusCode) {
//                System.out.println("Did not get insights key" + StatusCode);
//            }
//
//            @Override
//            public void onResponse(String response) {
//                System.out.println("Successful call insights response." + response);
//            }
//        });
//
//        client1.execute(Global.statsKeyURL);
    }

    @Test
    public void doInBackground() {

    }

    @Test
    public void convertInputStreamToString(){
//        httpPostAsyncTask.convertInputStreamToString()
    }
}