package com.peterphi.std.com.peterphi.std.guice.testing.annotations;

import com.google.inject.Inject;
import com.peterphi.std.guice.testing.GuiceTest;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.Automock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the use of the {@link com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.Automock} annotation
 */
@GuiceTest
public class AutomockTest
{
	@Inject
	@Automock
	SomeInterface iface;


	@BeforeEach
	public void before()
	{
		Mockito.when(iface.whoami()).thenReturn("hello");
	}


	@Test
	public void x()
	{
		assertEquals("hello", iface.whoami());
	}
}
