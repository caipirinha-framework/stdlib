package com.peterphi.std.guice.common.cache.module;

import com.google.inject.Injector;
import com.peterphi.std.guice.apploader.BasicSetup;
import com.peterphi.std.guice.apploader.impl.GuiceBuilder;
import com.peterphi.std.guice.common.cached.annotation.Cache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CacheMethodInterceptorTest
{
	@Test
	public void testCache() throws Exception
	{
		Injector injector = new GuiceBuilder().withSetup(new BasicSetup())
		                                      .withNoScannerFactory()
		                                      .withAutoLoadRoles(false)
		                                      .build();

		CacheTest test = injector.getInstance(CacheTest.class);

		//no calls to the test class have been made yet
		assertEquals(0, test.getRealCalls());
		//make a call
		test.getValue();
		//one real call has been made
		assertEquals(1, test.getRealCalls());
		//make a call, a cached value should be returned
		test.getValue();
		//so the number of real calls is still 1
		assertEquals(1, test.getRealCalls());
		//wait a while (but not long enough for the result to go stale)
		Thread.sleep(500l);
		//make a call, a cached value should be returned
		test.getValue();
		//so the number of real calls is still 1
		assertEquals(1, test.getRealCalls());
		//wait a while longer (long enough for the result to go stale)
		Thread.sleep(2000l);
		//make a call, a fresh value should be returned
		test.getValue();
		//so now the number of real calls to the method is 2
		assertEquals(2, test.getRealCalls(), "number of real calls to method after longer sleep");
	}


	public static class CacheTest
	{
		int realCalls = 0;


		public CacheTest()
		{
			realCalls = 0;
		}


		@Cache(timeout = 1000)
		public Integer getValue()
		{
			realCalls++;

			return realCalls;
		}


		public int getRealCalls()
		{
			return realCalls;
		}
	}
}
