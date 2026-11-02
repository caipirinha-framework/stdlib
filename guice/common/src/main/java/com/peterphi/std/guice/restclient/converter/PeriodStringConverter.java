package com.peterphi.std.guice.restclient.converter;

import org.joda.time.Period;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
class PeriodStringConverter implements ParamConverter<Period>
{
	@Override
	public Period fromString(final String str)
	{
		if (str == null)
			return null;
		else
			return new Period(str);
	}


	@Override
	public String toString(final Period value)
	{
		if (value == null)
			return null;
		else
			return value.toString();
	}
}
