package com.peterphi.std.guice.hibernate.webquery.impl;

import com.peterphi.std.guice.restclient.jaxb.webquery.WQDates;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQDataType;
import com.peterphi.std.types.SampleCount;
import com.peterphi.std.types.Timecode;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

public class WQTypeHelper
{
	public static WQDataType translate(Class<?> clazz)
	{
		if (clazz == String.class)
		{
			return WQDataType.STRING;
		}
		else if (clazz == Integer.class || clazz == int.class)
		{
			return WQDataType.NUMERIC;
		}
		else if (clazz == Long.class || clazz == long.class)
		{
			return WQDataType.NUMERIC;
		}
		else if (clazz == Double.class || clazz == double.class)
		{
			return WQDataType.NUMERIC;
		}
		else if (clazz == Float.class || clazz == float.class)
		{
			return WQDataType.NUMERIC;
		}
		else if (clazz == Short.class || clazz == short.class)
		{
			return WQDataType.NUMERIC;
		}
		else if (clazz == Boolean.class || clazz == boolean.class)
		{
			return WQDataType.BOOL;
		}
		else if (clazz == DateTime.class)
		{
			return WQDataType.DATETIME;
		}
		else if (clazz == Date.class)
		{
			return WQDataType.DATETIME;
		}
		else if (clazz == java.sql.Date.class)
		{
			return WQDataType.DATETIME;
		}
		else if (clazz == UUID.class)
		{
			return WQDataType.STRING;
		}
		else if (clazz == Timecode.class)
		{
			return WQDataType.TIMECODE;
		}
		else if (clazz == SampleCount.class)
		{
			return WQDataType.SAMPLE_COUNT;
		}
		else if (clazz.isEnum())
		{
			return WQDataType.ENUM;
		}
		else if (clazz.equals(byte[].class))
		{
			return WQDataType.BLOB;
		}
		else
		{
			throw new IllegalArgumentException("No primitives parser for type: " + clazz);
		}
	}


	public static Object parse(Class<?> clazz, String value)
	{
		if (clazz == String.class)
		{
			return value;
		}
		else if (clazz == Integer.class || clazz == int.class)
		{
			if (value.equalsIgnoreCase("min"))
				return Integer.MIN_VALUE;
			else if (value.equalsIgnoreCase("max"))
				return Integer.MAX_VALUE;
			else
				return Integer.parseInt(value);
		}
		else if (clazz == Long.class || clazz == long.class)
		{
			if (value.equalsIgnoreCase("min"))
				return Long.MIN_VALUE;
			else if (value.equalsIgnoreCase("max"))
				return Long.MAX_VALUE;
			else
				return Long.parseLong(value);
		}
		else if (clazz == Double.class || clazz == double.class)
		{
			if (value.equalsIgnoreCase("min"))
				return -Double.MAX_VALUE;
			else if (value.equalsIgnoreCase("max"))
				return Double.MAX_VALUE;
			else
				return Double.parseDouble(value);
		}
		else if (clazz == Float.class || clazz == float.class)
		{
			if (value.equalsIgnoreCase("min"))
				return -Float.MAX_VALUE;
			else if (value.equalsIgnoreCase("max"))
				return Float.MAX_VALUE;
			else
				return Float.parseFloat(value);
		}
		else if (clazz == Short.class || clazz == short.class)
		{
			if (value.equalsIgnoreCase("min"))
				return Short.MIN_VALUE;
			else if (value.equalsIgnoreCase("max"))
				return Short.MAX_VALUE;
			else
				return Short.parseShort(value);
		}
		else if (clazz == Boolean.class || clazz == boolean.class)
		{
			if (StringUtils.equalsIgnoreCase(value, "true") || StringUtils.equalsIgnoreCase(value, "yes") ||
			    StringUtils.equalsIgnoreCase(value, "on"))
				return true;
			else if (StringUtils.equalsIgnoreCase(value, "false") || StringUtils.equalsIgnoreCase(value, "no") ||
			         StringUtils.equalsIgnoreCase(value, "off"))
				return false;
			else
				throw new IllegalArgumentException("Cannot parse boolean: " + value);
		}
		else if (clazz == DateTime.class)
		{
			return WQDates.resolve(value);
		}
		else if (clazz == Date.class)
		{
			return WQDates.resolve(value).toDate();
		}
		else if (clazz == java.sql.Date.class)
		{
			return new java.sql.Date(WQDates.resolve(value).getMillis());
		}
		else if (clazz == UUID.class)
		{
			return UUID.fromString(value);
		}
		else if (clazz == Timecode.class)
		{
			return Timecode.getInstance(value);
		}
		else if (clazz == SampleCount.class)
		{
			return SampleCount.valueOf(value);
		}
		else if (clazz.isEnum())
		{
			return parseEnum((Class<? extends Enum>) clazz, value);
		}
		else if (clazz.equals(byte[].class))
		{
			return value.getBytes(Charset.forName("UTF-8"));
		}
		else
		{
			throw new IllegalArgumentException("No primitives parser for type: " + clazz + ", cannot interpret " + value);
		}
	}


	private static <E extends Enum> E parseEnum(final Class<E> clazz, final String value)
	{
		final E[] values = clazz.getEnumConstants();

		if (value.length() != 0 && Character.isDigit(value.charAt(0)))
		{
			// Try ordinal value
			try
			{
				return values[Integer.valueOf(value)];
			}
			catch (Throwable t)
			{
				// ignore, return to normal codepath
			}
		}
		else
		{
			for (E val : values)
			{
				if (value.equalsIgnoreCase(val.name()))
					return val;
			}
		}

		throw new IllegalArgumentException(value + " is not a valid " + clazz.getSimpleName() + ": expected one of " + Arrays.asList(values));
	}
}
