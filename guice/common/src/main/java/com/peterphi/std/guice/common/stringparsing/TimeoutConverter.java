package com.peterphi.std.guice.common.stringparsing;

import com.peterphi.std.threading.Timeout;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets a number of milliseconds as a Timeout<br /> Supports the following string representations:
 * <ul>
 *     <li>{@link Duration#parse(java.lang.CharSequence) ISO-8601 Duration}</li>
 *     <li><em>###</em> - treated as milliseconds</li>
 *     <li><em>###</em><em>(d|h|m|s|ms)</em> (days, hours, minutes, seconds, milliseconds)</li>
 * </ul>
 */
public class TimeoutConverter
{
	private static final Pattern pattern = Pattern.compile("^([0-9]+)\\s*([a-zA-Z]+)$", Pattern.CASE_INSENSITIVE);


	public Timeout convert(String value)
	{
		return doConvert(value);
	}


	public static Timeout doConvert(String value)
	{
		if (value == null)
			return null;

		value = value.trim();

		if (value.isEmpty())
			return null;

		final char last = value.charAt(value.length() - 1);

		if (Character.isDigit(last))
		{
			return new Timeout(Long.parseLong(value), TimeUnit.MILLISECONDS);
		}
		else if (value.startsWith("P"))
		{
			// ISO-8601 duration
			try
			{
				final Duration iso = Duration.parse(value);
				final TimeUnit bestUnit = pickUnit(iso);

				return new Timeout(bestUnit.convert(iso.toMillis(), TimeUnit.MILLISECONDS), bestUnit);
			}
			catch (Throwable t)
			{
				throw new IllegalArgumentException("Error parsing ISO-8601 Duration: " + value, t);
			}
		}
		else
		{
			Matcher matcher = pattern.matcher(value);

			if (matcher.matches())
			{
				final long quantity = Long.valueOf(matcher.group(1));
				final String unit = matcher.group(2);

				return new Timeout(quantity, parseUnit(unit));
			}
			else
			{
				throw new IllegalArgumentException("Cannot parse duration: " + value);
			}
		}
	}


	/**
	 * Identify the largest unit that can accurately represent the provided duration
	 *
	 * @param iso
	 * @return
	 */
	private static @NotNull TimeUnit pickUnit(final @NotNull Duration iso)
	{
		final long millis = iso.toMillis();

		// Special-case values under 1 second
		if (millis < 1000)
			return TimeUnit.MILLISECONDS;

		final long SECOND = 1000;
		final long MINUTE = 60 * SECOND;
		final long HOUR = 60 * MINUTE;
		final long DAY = 24 * HOUR;

		if (millis % DAY == 0)
			return TimeUnit.DAYS;
		else if (millis % HOUR == 0)
			return TimeUnit.HOURS;
		else if (millis % MINUTE == 0)
			return TimeUnit.MINUTES;
		else if (millis % SECOND == 0)
			return TimeUnit.SECONDS;
		else
			return TimeUnit.MILLISECONDS;
	}


	private static TimeUnit parseUnit(final String unit)
	{
		if ("ms".equalsIgnoreCase(unit) || "millisecond".equalsIgnoreCase(unit) || "milliseconds".equalsIgnoreCase(unit))
		{
			return TimeUnit.MILLISECONDS;
		}
		else if ("s".equalsIgnoreCase(unit) ||
		         "sec".equalsIgnoreCase(unit) ||
		         "seconds".equalsIgnoreCase(unit) ||
		         "second".equalsIgnoreCase(unit))
		{
			return TimeUnit.SECONDS;
		}
		else if ("m".equalsIgnoreCase(unit) ||
		         "min".equalsIgnoreCase(unit) ||
		         "minute".equalsIgnoreCase(unit) ||
		         "minutes".equalsIgnoreCase(unit))
		{
			return TimeUnit.MINUTES;
		}
		else if ("h".equalsIgnoreCase(unit) || "hour".equalsIgnoreCase(unit) || "hours".equalsIgnoreCase(unit))
		{
			return TimeUnit.HOURS;
		}
		else if ("d".equalsIgnoreCase(unit) || "day".equalsIgnoreCase(unit) || "days".equalsIgnoreCase(unit))
		{
			return TimeUnit.DAYS;
		}
		else
			throw new IllegalArgumentException("Unknown unit: " + unit);
	}
}
