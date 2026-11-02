package com.peterphi.std.guice.restclient.converter;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
public class CommonTypesParamConverterProvider implements ParamConverterProvider
{
	private final ParamConverter dateTime = new DateTimeStringConverter();
	private final ParamConverter localDateTime = new LocalDateTimeStringConverter();
	private final ParamConverter localDate = new LocalDateStringConverter();
	private final ParamConverter period = new PeriodStringConverter();
	private final ParamConverter duration = new DurationStringConverter();
	private final ParamConverter localTime = new LocalTimeStringConverter();


	@Override
	@SuppressWarnings("unchecked")
	public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType, final Annotation[] annotations)
	{
		if (rawType == null)
			return null;
		else if (rawType.equals(DateTime.class))
			return dateTime;
		else if (rawType.equals(LocalDateTime.class))
			return localDateTime;
		else if (rawType.equals(LocalDate.class))
			return localDate;
		else if (rawType.equals(Period.class))
			return period;
		else if (rawType.equals(Duration.class))
			return duration;
		else if (rawType.equals(LocalTime.class))
			return localTime;
		else
			return null;
	}
}
