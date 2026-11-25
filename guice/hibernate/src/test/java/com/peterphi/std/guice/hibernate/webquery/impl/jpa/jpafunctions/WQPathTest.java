package com.peterphi.std.guice.hibernate.webquery.impl.jpa.jpafunctions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WQPathTest
{
	@Test
	public void testX()
	{
		WQPath path = new WQPath("a.b.c");

		assertEquals("c", path.getHead().getPath());
		assertEquals("a.b", path.getTail().getPath());
	}
}
