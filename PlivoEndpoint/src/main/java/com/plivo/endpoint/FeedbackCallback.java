package com.plivo.endpoint;

public interface FeedbackCallback {
    void onFailure(int statusCode);
    void onSuccess(String response);
    void onValidationFail(String message);
}
