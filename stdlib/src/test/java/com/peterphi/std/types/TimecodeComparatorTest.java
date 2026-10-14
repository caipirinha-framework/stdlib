package com.peterphi.std.types;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimecodeComparatorTest
{
	@Test
	public void testComparatorSameRate()
	{
		List<Timecode> timecodes = new ArrayList<Timecode>();

		timecodes.add(Timecode.getInstance("01:02:03:04", Timebase.HZ_25));
		timecodes.add(Timecode.getInstance("01:02:03:05", Timebase.HZ_25));

		timecodes.add(Timecode.getInstance("01:02:04:04", Timebase.HZ_25));
		timecodes.add(Timecode.getInstance("01:02:05:04", Timebase.HZ_25));

		timecodes.add(Timecode.getInstance("01:03:03:04", Timebase.HZ_25));
		timecodes.add(Timecode.getInstance("01:04:03:04", Timebase.HZ_25));

		timecodes.add(Timecode.getInstance("02:02:03:04", Timebase.HZ_25));
		timecodes.add(Timecode.getInstance("03:02:03:04", Timebase.HZ_25));

		List<Timecode> shuffled = new ArrayList<Timecode>(timecodes);
		Collections.shuffle(shuffled);

		// Order the shuffled list
		Collections.sort(shuffled, new TimecodeComparator());

		assertEquals(timecodes, shuffled);
	}


	@Test
	public void testComparatorDifferentTimebases()
	{
		List<Timecode> timecodes = new ArrayList<Timecode>();

		timecodes.add(Timecode.getInstance("01:02:03:05", Timebase.HZ_50)); // 5 frames @50 (=2.5 @25)
		timecodes.add(Timecode.getInstance("01:02:03:04", Timebase.HZ_25)); // 4 frames @25
		timecodes.add(Timecode.getInstance("01:02:03:09", Timebase.HZ_50)); // 9 frames @50 (=8.5 @25)

		List<Timecode> shuffled = new ArrayList<Timecode>(timecodes);
		Collections.shuffle(shuffled);

		// Order the shuffled list
		Collections.sort(shuffled, new TimecodeComparator());

		assertEquals(timecodes, shuffled);
	}


	@Test
	public void testBetween()
	{
		final Timecode start = Timecode.getInstance("01:00:00:00", Timebase.HZ_25);
		final Timecode end = Timecode.getInstance("10:00:00:00", Timebase.HZ_25);

		final Timecode testA = Timecode.getInstance("00:05:00:00", Timebase.HZ_25); // out of range (left of range)
		final Timecode testB = Timecode.getInstance("01:00:01:00", Timebase.HZ_25); // within range
		final Timecode testC = Timecode.getInstance("11:00:00:00", Timebase.HZ_25); // out of range (right of range)

		assertFalse("A should not be between start+end", TimecodeComparator.between(testA, start, end));
		assertTrue("B should be between start+end", TimecodeComparator.between(testB, start, end));
		assertFalse("C should not be between start+end", TimecodeComparator.between(testC, start, end));
	}


	@Test
	public void testMin()
	{
		Timecode min = TimecodeComparator.minTimecode(Timecode.getInstance("00:00:00:00@25"),
		                                      Timecode.getInstance("-10:10:10:10@25"),
		                                      Timecode.getInstance("-10:00:00:00@25"),
		                                      Timecode.getInstance("-09:09:09:09@25"),
		                                      Timecode.getInstance("10:10:10:10@25"));

		assertEquals("min", "-10:10:10:10@25", min.toEncodedString());
	}


	@Test
	public void testMax()
	{
		Timecode max = TimecodeComparator.maxTimecode(Timecode.getInstance("00:00:00:00@25"),
		                                      Timecode.getInstance("-10:10:10:10@25"),
		                                      Timecode.getInstance("-10:00:00:00@25"),
		                                      Timecode.getInstance("-09:09:09:09@25"),
		                                      Timecode.getInstance("10:10:10:10@25"));

		assertEquals("max", "10:10:10:10@25", max.toEncodedString());
	}
}
