package com.plivo.endpoint;

public interface HTTPRequestCallback {
    void onResponse(String response);
    void onFailure(int statusCode);
}
