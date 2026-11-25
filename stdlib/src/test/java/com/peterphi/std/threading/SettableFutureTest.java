package com.peterphi.std.threading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SettableFutureTest
{
	@Test
	public void testSetThenGetPrimitiveTimeout() throws Exception
	{
		final Object val = new Object();
		SettableFuture<Object> future = new SettableFuture<Object>();

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());
		future.set(val);
		assertTrue(future.isDone());

		assertEquals(val, future.get(1, TimeUnit.SECONDS));
	}


	@Test
	public void testSetThenGetPrimitiveTimeoutMaxLong() throws Exception
	{
		final Object val = new Object();
		SettableFuture<Object> future = new SettableFuture<Object>();

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());
		future.set(val);
		assertTrue(future.isDone());

		assertEquals(val, future.get(Long.MAX_VALUE, TimeUnit.MILLISECONDS));
	}


	@Test
	public void testSetThenGetWithTimeout() throws Exception
	{
		final Object val = new Object();
		SettableFuture<Object> future = new SettableFuture<Object>();

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());
		future.set(val);
		assertTrue(future.isDone());

		assertEquals(val, future.get(Timeout.ONE_SECOND.start()));
	}


	@Test
	public void testSetThenGetNoTimeout() throws Exception
	{
		final Object val = new Object();
		SettableFuture<Object> future = new SettableFuture<Object>();

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());
		future.set(val);
		assertTrue(future.isDone());

		assertEquals(val, future.get());
	}
}
