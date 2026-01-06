package com.plivo.endpoint.init;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.plivo.endpoint.Endpoint;
import com.plivo.endpoint.EventListener;
import com.plivo.endpoint.testutils.SynchronousExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EndpointInitTest {

    @Mock
    private EventListener eventListener;

    private Endpoint endpoint;

    private Context context;


    @Before
    public void setUp() {
        eventListener = mock(EventListener.class);
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
//        HashMap<String, Object> options = new HashMap<String, Object>() {{
//            put("enableTracking", true);
//            put("context", context);
//        }};
        endpoint = Endpoint.newInstance(context,true, eventListener);

    }

    @After
    public void tearDown(){
        reset(eventListener);
        endpoint.resetEndpoint();
        endpoint = null;
    }

    @Test
    public void endpoint_isInitialized_test() {
        loadLib();
    }

    @Test
    public void endpoint_isInitialized_async_test() {
        SynchronousExecutor bkgTask = new SynchronousExecutor();
        bkgTask.execute(this::loadLib);
    }

    private void loadLib() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Endpoint endpoint = Endpoint.newInstance(context, true,eventListener);
        assertThat(endpoint).isNotNull(); // library .so loaded
    }
}