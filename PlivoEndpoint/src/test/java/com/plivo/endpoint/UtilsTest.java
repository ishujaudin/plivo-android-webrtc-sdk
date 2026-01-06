package com.plivo.endpoint;

import static org.junit.Assert.*;

import android.text.TextUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
//import org.mockito.Mockito;
//import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class UtilsTest {
    @Before
    public void setUp() throws Exception {
//        Mockito.mock(TextUtils.class);

    }

    @Test
    public void getCurrentTimeInMilliSeconds() {
        assertEquals(Utils.getCurrentTimeInMilliSeconds(), new Date().getTime() + "");
    }

    @Test
    public void mapToString_returnValue() {
        Map<String, String> map = new HashMap<>();
        map.put("example", "test");
        String result = ",example:test";
        try (MockedStatic<TextUtils> utilities = Mockito.mockStatic(TextUtils.class)) {
            utilities.when(() -> TextUtils.isEmpty(result))
                    .thenReturn(false);
            assertEquals(result, Utils.mapToString(map));
        }
    }
    @Test
    public void mapToString_returnNull() {
        Map<String, String> map = new HashMap<>();
        assertNull(Utils.mapToString(map));
    }

    @Test
    public void testValidateDestination_subAuth(){
        String test = "some_sub_auth";
        Assert.assertTrue(Utils.validateDestination(test));
    }

    @Test
    public void testValidateDestination_false(){
        String test = "some_invalid_sub_auth@@";
        Assert.assertFalse(Utils.validateDestination(test));
    }

    @Test
    public void testValidateDestination_mobile(){
        String test = "+912134567890";
        Assert.assertTrue(Utils.validateDestination(test));
    }

    @Test
    public void testValidateDestination_mobileString(){
        String test = "+912134567890sss";
        Assert.assertFalse(Utils.validateDestination(test));
    }

    @Test
    public void testValidateDestination_mobileSpecialChar(){
        String test = "+912134567890@@";
        Assert.assertFalse(Utils.validateDestination(test));
    }
}