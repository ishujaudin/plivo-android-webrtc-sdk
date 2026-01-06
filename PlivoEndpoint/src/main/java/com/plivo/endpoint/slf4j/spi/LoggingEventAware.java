package com.plivo.endpoint.slf4j.spi;

import com.plivo.endpoint.slf4j.event.LoggingEvent;

public interface LoggingEventAware {

	void log(LoggingEvent event);
	
}
