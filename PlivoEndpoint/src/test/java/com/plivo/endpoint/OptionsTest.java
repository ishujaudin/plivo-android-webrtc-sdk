package com.plivo.endpoint;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;

public class OptionsTest {
    Options options;
    JSONObject mock;
    @Before
    public void setUp() throws Exception {
        HashMap<String, Object> map = new HashMap<>();
        map.put("enableTracking", true);
        options = new Options(map);
    }

    @Test
    public void isEnableTacking_returnTrue() {
        assertEquals(true, options.isEnableTacking());
    }

    @Test
    public void getOptions() throws JSONException {
        HashMap<String, Object> map = new HashMap<>();
        map.put("enableQualityTracking", "all");
        map.put("enableTracking", true);
        JSONObject jsonOptions = new JSONObject(map);
        assertEquals(jsonOptions.toString(), options.getOptions().toString());
    }
}