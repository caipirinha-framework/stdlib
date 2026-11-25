package com.peterphi.std.guice.common.retry.retry.backoff;

import com.peterphi.std.threading.Timeout;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExponentialBackoffTest
{

	public static final Timeout ONE_MILLISECOND = new Timeout(1, TimeUnit.MILLISECONDS);

	@Test
	public void testGetBackoff() throws Exception
	{
		ExponentialBackoff svc = new ExponentialBackoff(ONE_MILLISECOND, 2);

		assertEquals(1, svc.getBackoff(1), "initial backoff for first attempt");
		assertEquals(2, svc.getBackoff(2));
		assertEquals(4, svc.getBackoff(3));
		assertEquals(8, svc.getBackoff(4));
		assertEquals(16, svc.getBackoff(5));
	}


	@Test
	public void testGetLimitedBackoff() throws Exception
	{
		ExponentialBackoff svc = new ExponentialBackoff(ONE_MILLISECOND, 8, Timeout.ONE_SECOND);

		assertEquals(1, svc.getBackoff(1), "initial backoff for first attempt");
		assertEquals(8, svc.getBackoff(2));
		assertEquals(64, svc.getBackoff(3));
		assertEquals(512, svc.getBackoff(4));

		// Test the maximum backoff is hit
		assertEquals(1000, svc.getBackoff(5), "max backoff at 5th attempt");
		assertEquals(1000, svc.getBackoff(5000000), "max backoff still used for subsequent attempts");
	}
}
