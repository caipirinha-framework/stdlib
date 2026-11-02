package com.peterphi.std.guice.restclient.converter;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
class LocalDateStringConverter implements ParamConverter<LocalDate>
{
	private static final DateTimeFormatter PARSER = ISODateTimeFormat.localDateParser();
	private static final DateTimeFormatter FORMATTER = ISODateTimeFormat.date();


	@Override
	public LocalDate fromString(final String str)
	{
		if (str == null)
			return null;
		else if (str.equalsIgnoreCase("today"))
			return new LocalDate();
		else
			return PARSER.parseLocalDate(str);
	}


	@Override
	public String toString(final LocalDate value)
	{
		if (value == null)
			return null;
		else
			return FORMATTER.print(value);
	}
}
