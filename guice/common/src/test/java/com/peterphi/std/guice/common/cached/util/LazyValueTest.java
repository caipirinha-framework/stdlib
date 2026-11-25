package com.peterphi.std.guice.common.cached.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LazyValueTest
{
	@Test
	public void testConstant()
	{
		final var val = LazyValue.of("test");

		assertEquals(true, val.isComputed(), "isComputed before val requested");
		assertEquals("test", val.get(), "value");
		assertEquals(true, val.isComputed(), "isComputed after val requested");
	}


	@Test
	public void testCancelIsNoopForConstant()
	{
		final var val = LazyValue.of("test");
		val.cancel();
		assertEquals("test", val.get(), "value");
	}


	@Test
	public void testSupplier()
	{
		final var val = new LazyValue<>("test" :: toString);

		assertEquals(false, val.isComputed(), "isComputed before val requested");
		assertEquals("test", val.get(), "value");
		assertEquals(true, val.isComputed(), "isComputed after val requested");
		val.cancel();
		assertEquals("test", val.get(), "value after post-compute cancel request");
	}


	@Test
	public void testCancel()
	{
		final var val = new LazyValue<>("test" :: toString);
		val.cancel();
		assertThrows(CancellationException.class, () -> val.get());
	}
}
