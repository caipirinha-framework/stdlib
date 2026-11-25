package com.peterphi.std.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimecodeRangeTest
{
	private static final TimecodeRange RANGE = new TimecodeRange(Timecode.getInstance("-10:00:00:00@25"),
	                                                             Timecode.getInstance("10:00:00:00@25"));


	@Test
	public void testContains()
	{
		assertTrue(RANGE.within(Timecode.getInstance("01:01:01:01@25")));
		assertTrue(RANGE.within(Timecode.getInstance("-01:01:01:01@25")));
	}


	@Test
	public void testOverlapContained()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("-01:01:01:01@25"),
		                                              Timecode.getInstance("01:01:01:01@25"));

		assertTrue(RANGE.overlaps(other));
	}


	@Test
	public void testOverlapSelf()
	{
		assertTrue(RANGE.overlaps(RANGE));
	}


	@Test
	public void testOverlapLeft()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("-11:01:01:01@25"),
		                                              Timecode.getInstance("01:01:01:01@25"));

		assertTrue(RANGE.overlaps(other));
	}


	@Test
	public void testOverlapCompletely()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("-23:23:23:23@25"),
		                                              Timecode.getInstance("23:23:23:23@25"));

		assertTrue(RANGE.overlaps(other));
	}


	@Test
	public void testOverlapRight()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("-01:01:01:01@25"),
		                                              Timecode.getInstance("11:01:01:01@25"));

		assertTrue(RANGE.overlaps(other));
	}


	@Test
	public void testNoOverlapLeft()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("-11:01:01:01@25"),
		                                              Timecode.getInstance("-10:59:59:24@25"));

		assertFalse(RANGE.overlaps(other));
	}


	@Test
	public void testNoOverlapRight()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("10:01:01:01@25"),
		                                              Timecode.getInstance("11:01:01:01@25"));

		assertFalse(RANGE.overlaps(other));
	}


	@Test
	public void testMergeSelf()
	{
		assertEquals(RANGE, TimecodeRange.merge(RANGE, RANGE));
	}


	@Test
	public void testMerge()
	{
		final TimecodeRange other = new TimecodeRange(Timecode.getInstance("10:01:01:01@25"),
		                                              Timecode.getInstance("11:01:01:01@25"));

		final TimecodeRange expected = new TimecodeRange(RANGE.getStart(), other.getEnd());

		assertEquals(expected, TimecodeRange.merge(RANGE, other));
	}
}
