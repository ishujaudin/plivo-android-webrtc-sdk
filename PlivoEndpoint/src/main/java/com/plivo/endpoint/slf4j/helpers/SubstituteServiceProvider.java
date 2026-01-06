package com.plivo.endpoint.slf4j.helpers;

import com.plivo.endpoint.slf4j.ILoggerFactory;
import com.plivo.endpoint.slf4j.IMarkerFactory;
import com.plivo.endpoint.slf4j.spi.MDCAdapter;
import com.plivo.endpoint.slf4j.spi.SLF4JServiceProvider;

public class SubstituteServiceProvider implements SLF4JServiceProvider {
    private SubstituteLoggerFactory loggerFactory = new SubstituteLoggerFactory();
    private IMarkerFactory markerFactory = new BasicMarkerFactory();
    private MDCAdapter mdcAdapter = new BasicMDCAdapter();

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    public SubstituteLoggerFactory getSubstituteLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }


    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }


    @Override
    public String getRequesteApiVersion() {
       throw new UnsupportedOperationException();
    }
    
    @Override
    public void initialize() {
    	
    }
}
