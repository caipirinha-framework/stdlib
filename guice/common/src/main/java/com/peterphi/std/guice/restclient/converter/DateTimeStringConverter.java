package com.peterphi.std.guice.restclient.converter;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
class DateTimeStringConverter implements ParamConverter<DateTime>
{
	private static final DateTimeFormatter PARSER = ISODateTimeFormat.dateTimeParser();
	private static final DateTimeFormatter FORMATTER = ISODateTimeFormat.dateTime();


	@Override
	public DateTime fromString(final String str)
	{
		if (str == null)
			return null;
		else if (str.equalsIgnoreCase("now"))
			return new DateTime();
		else
			return PARSER.parseDateTime(str);
	}


	@Override
	public String toString(final DateTime value)
	{
		if (value == null)
			return null;
		else
			return FORMATTER.print(value);
	}
}
