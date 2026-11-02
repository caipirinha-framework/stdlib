package com.peterphi.std.guice.restclient.converter;

import org.joda.time.Duration;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
class DurationStringConverter implements ParamConverter<Duration>
{
	@Override
	public Duration fromString(final String str)
	{
		if (str == null)
			return null;
		else
			return new Duration(str);
	}


	@Override
	public String toString(final Duration value)
	{
		if (value == null)
			return null;
		else
			return value.toString();
	}
}
