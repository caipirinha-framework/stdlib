package com.peterphi.std.guice.restclient.jaxb.webquery;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class WQConstraintTest
{
	@Test
	public void testFractionalTimestampRange()
	{
		final String encoded = "_f_range_2017-01-25T11:02:30.680Z..2017-01-25T12:14:35.040Z";
		final WQConstraint constraint = WQConstraint.decode("start", encoded);

		assertRange(constraint, "2017-01-25T11:02:30.680Z", "2017-01-25T12:14:35.040Z");
		assertEquals(encoded, constraint.encodeValue());
	}


	@Test
	public void testZeroMillisecondsAndTimezoneOffsets()
	{
		assertRange(WQConstraint.decode("start", "_f_range_2026-10-05T10:00:00.000+01:00..2026-10-05T11:00:00.000+01:00"),
		            "2026-10-05T10:00:00.000+01:00",
		            "2026-10-05T11:00:00.000+01:00");
	}


	@Test
	public void testDecimalRange()
	{
		assertRange(WQConstraint.decode("start", "_f_range_1.25..2.75"), "1.25", "2.75");
	}


	@Test
	public void testIntegerRange()
	{
		assertRange(WQConstraint.decode("start", "_f_range_1..2"), "1", "2");
	}


	@Test
	public void testMalformedRanges()
	{
		for (String value : new String[]{"", "1", "1.2", "..", "..2", "1.."})
		{
			final IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
			                                                   () -> WQConstraint.decode("start", "_f_range_" + value));
			assertEquals("Range constraint for start requires two endpoints separated by '..'", error.getMessage());
		}
	}


	private static void assertRange(final WQConstraint constraint, final String left, final String right)
	{
		assertEquals("start", constraint.field);
		assertEquals(WQFunctionType.RANGE, constraint.function);
		assertEquals(left, constraint.value);
		assertEquals(right, constraint.value2);
	}
}
