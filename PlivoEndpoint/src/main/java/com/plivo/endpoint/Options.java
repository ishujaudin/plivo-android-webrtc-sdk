package com.plivo.endpoint;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class Options {
    HashMap<String, Object> setupOptions;
    private boolean enableCallStates;
    private boolean enableMediaMetrics;
    private String qualityTracking = "all";

    public Options(HashMap<String, Object> setupOptions) {
        this.setupOptions = setupOptions;
        setupTrackingOptions();
    }

    private void setupTrackingOptions() {
        if (setupOptions.containsKey("enableQualityTracking")) {
            if (setupOptions.get("enableQualityTracking") instanceof CallAndMediaMetrics) {
                CallAndMediaMetrics tracking = (CallAndMediaMetrics) setupOptions.get("enableQualityTracking");
                if (tracking != null) {
                    qualityTracking = tracking.getValue();
                    Log.D("enableQualityTracking : " + qualityTracking);
                    switch (tracking) {
                        case LOCAL_ONLY:
                            this.setEnableCallStates(false);
                            this.setEnableMediaMetrics(true);
                            break;
                        case REMOTE_ONLY:
                            this.setEnableCallStates(true);
                            this.setEnableMediaMetrics(false);
                            break;
                        case NONE:
                            this.setEnableCallStates(false);
                            this.setEnableMediaMetrics(false);
                            break;
                        case ALL:
                        default:
                            this.setEnableCallStates(true);
                            this.setEnableMediaMetrics(true);
                    }
                }else {
                    this.setEnableCallStates(true);
                    this.setEnableMediaMetrics(true);
                }
            } else {
                Log.E("Invalid enableQualityTracking option, use Tracking enum");
                this.setEnableCallStates(true);
                this.setEnableMediaMetrics(true);
            }
        } else if (setupOptions.containsKey("enableTracking")) {
            if (setupOptions.get("enableTracking") instanceof Boolean) {
                boolean tracking = (boolean) setupOptions.get("enableTracking");
                Log.D("enableTracking : " + tracking);
                this.setEnableCallStates(tracking);
                this.setEnableMediaMetrics(tracking);
            } else {
                Log.E("Invalid enableTracking option, use boolean");
                this.setEnableCallStates(true);
                this.setEnableMediaMetrics(true);
            }
        } else {
            Log.D("enableQualityTracking : " + qualityTracking);
            this.setEnableCallStates(true);
            this.setEnableMediaMetrics(true);
        }
    }

    public Boolean isEnableTacking() {
        return this.enableCallStates;
    }

    private void setEnableCallStates(boolean enableCallStates) {
        this.enableCallStates = enableCallStates;
    }

    public boolean isEnableMediaMetrics() {
        return enableMediaMetrics;
    }

    private void setEnableMediaMetrics(boolean enableMediaMetrics) {
        this.enableMediaMetrics = enableMediaMetrics;
    }

    public JSONObject getOptions() {
        JSONObject options = new JSONObject(this.setupOptions);
        try {
            options.put("enableTracking", this.enableCallStates);
            options.put("enableQualityTracking", this.qualityTracking);
        } catch (JSONException exception) {
            exception.printStackTrace();
        }
        return options;
    }
}
