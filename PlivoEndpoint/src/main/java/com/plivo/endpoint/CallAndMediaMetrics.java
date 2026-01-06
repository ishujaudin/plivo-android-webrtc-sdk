package com.plivo.endpoint;

public enum CallAndMediaMetrics {

    ALL("all"), REMOTE_ONLY("remoteonly"), LOCAL_ONLY("localonly"), NONE("none");

    CallAndMediaMetrics(String value) {
        this.setValue(value);
    }

    private String value;

    public String getValue() {
        return value;
    }

    private void setValue(String value) {
        this.value = value;
    }
}