package com.peterphi.std.guice.common.daemon;

import com.google.inject.Inject;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import org.apache.commons.lang.StringUtils;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * A recurring daemon that runs once a day (although it can be triggered to run early)
 */
public abstract class GuiceDailyDaemon extends GuiceScheduledDaemon
{
	/**
	 * The time we'll next run (N.B. should only be read via {@link #getScheduledTime()} so that implementations could override to provide for multiple times throughout the day
	 */
	@NotNull
	private LocalTime _time;


	public GuiceDailyDaemon(@NotNull final LocalTime defaultTime)
	{
		this._time = defaultTime;
	}


	/**
	 * Overrides the default time set in the constructor with the value from the application config (if set)
	 *
	 * @param config
	 *
	 * @throws java.time.format.DateTimeParseException
	 * 		if the LocalTime stored in config is invalid
	 */
	@Inject
	public void setTimeFromConfigIfSet(GuiceConfig config)
	{
		final String str = config.get("daemon." + getName() + ".time", null);

		if (StringUtils.isNotEmpty(str))
		{
			this._time = LocalTime.parse(str);
		}
	}


	/**
	 * Returns the time this daemon is configured to run at
	 *
	 * @return
	 */
	public LocalTime getScheduledTime()
	{
		return this._time;
	}


	@Override
	@NotNull
	public Instant getNextRunTime()
	{
		return getNextRunTime(getScheduledTime());
	}


	@NotNull
	private static Instant getNextRunTime(final LocalTime time)
	{
		// Figure out if our next run is later today or tomorrow
		final LocalDate day;
		if (time.isBefore(LocalTime.now()))
			day = LocalDate.now();
		else
			day = LocalDate.now().plusDays(1);

		return day.atTime(time).toInstant(ZoneOffset.UTC);
	}
}
