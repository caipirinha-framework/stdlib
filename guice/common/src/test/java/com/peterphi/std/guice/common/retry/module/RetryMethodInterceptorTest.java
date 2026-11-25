package com.peterphi.std.guice.common.retry.module;

import com.google.inject.Injector;
import com.peterphi.std.guice.apploader.BasicSetup;
import com.peterphi.std.guice.apploader.impl.GuiceBuilder;
import com.peterphi.std.guice.common.retry.annotation.Retry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RetryMethodInterceptorTest
{
	@Test
	public void testRetry() throws Exception
	{
		AtomicInteger var = new AtomicInteger();

		Injector injector = new GuiceBuilder().withSetup(new BasicSetup()).withNoScannerFactory().withAutoLoadRoles(false).build();

		RetryTest test = injector.getInstance(RetryTest.class);


		assertEquals(0, var.get());

		test.increment(var);

		assertEquals(1, var.get());

		try
		{
			test.fail();

			fail("fail method should not have succeeded!");
		}
		catch (Exception e)
		{
			// expected
		}

		test.incrementAndFailUnlessSeven(var);

		assertEquals(7, var.get());
	}


	public static class RetryTest
	{
		@Retry(maxAttempts = 3)
		public void increment(AtomicInteger var)
		{
			var.incrementAndGet();
		}


		@Retry(maxAttempts = 1)
		public void fail() throws Exception
		{
			throw new Exception("boom");
		}


		@Retry(maxAttempts = 10, backoffTime = 0)
		public void incrementAndFailUnlessSeven(AtomicInteger var) throws Exception
		{
			if (var.incrementAndGet() != 7)
			{
				throw new Exception("boom");
			}
		}
	}
}
