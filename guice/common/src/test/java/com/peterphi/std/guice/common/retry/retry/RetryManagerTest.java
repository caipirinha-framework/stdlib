package com.peterphi.std.guice.common.retry.retry;

import com.codahale.metrics.MetricRegistry;
import com.peterphi.std.guice.common.retry.retry.backoff.NoBackoffStrategy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RetryManagerTest
{
	/**
	 * Test that we only try maxAttempts times at most
	 *
	 * @throws Exception
	 */
	@Test
	public void testMaxAttempts() throws Exception
	{
		final MetricRegistry registry = new MetricRegistry();

		final AtomicInteger val = new AtomicInteger();

		// Try at most 3 times
		RetryManager mgr = new RetryManager(new NoBackoffStrategy(),
		                                    3,
		                                    registry.timer("dummy-timer"),
		                                    registry.meter("dummy-meter"));

		final Exception ex = new Exception("boom");

		try
		{
			mgr.runUnchecked(new Retryable<Void>()
			{
				public Void attempt(int attempt) throws Exception
				{
					val.incrementAndGet();

					throw ex;
				}


				public RetryDecision shouldRetry(int attempt, Throwable e)
				{
					return RetryDecision.BACKOFF_AND_RETRY; // always retry
				}
			});

			fail("Execution should not reach here");
		}
		catch (RuntimeException e)
		{
			// expected
			assertEquals(ex, e.getCause());
		}


		assertEquals(3, val.get());
	}
}
