package com.peterphi.std.guice.common.daemon;

import com.peterphi.std.threading.Timeout;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * A recurring daemon that runs at particular scheduled times (although it can be triggered to run early)
 */
public abstract class GuiceScheduledDaemon extends GuiceRecurringDaemon
{
	protected GuiceScheduledDaemon()
	{
		super(Timeout.THIRTY_MINUTES); // N.B. this timeout is never used but is populated to make sure the daemon doesn't run constantly
	}


	@Override
	@NotNull
	public Timeout getInitialSleepTime()
	{
		return getSleepTime();
	}


	@Override
	@NotNull
	public Timeout getSleepTime()
	{
		final long now = System.currentTimeMillis();
		final long next = getNextRunTime().toEpochMilli();

		final long nextRunIn = (next - now);

		if (nextRunIn < 0)
			return Timeout.ZERO;
		else
			return new Timeout(nextRunIn, TimeUnit.MILLISECONDS);
	}


	@NotNull
	public abstract Instant getNextRunTime();
}
