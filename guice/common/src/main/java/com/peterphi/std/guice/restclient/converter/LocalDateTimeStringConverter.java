package com.peterphi.std.guice.restclient.converter;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
class LocalDateTimeStringConverter implements ParamConverter<LocalDateTime>
{
	private static final DateTimeFormatter PARSER = ISODateTimeFormat.localDateParser();
	private static final DateTimeFormatter FORMATTER = ISODateTimeFormat.dateTime();


	@Override
	public LocalDateTime fromString(final String str)
	{
		if (str == null)
			return null;
		else if (str.equalsIgnoreCase("now"))
			return new LocalDateTime();
		else
			return PARSER.parseLocalDateTime(str);
	}


	@Override
	public String toString(final LocalDateTime value)
	{
		if (value == null)
			return null;
		else
			return FORMATTER.print(value);
	}
}
