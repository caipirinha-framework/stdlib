package com.peterphi.std.types;

import java.util.Comparator;

/**
 * Compares Timecodes in order of how much time they represent<br />
 * This means that 00:00:00:02@25 is greater than 00:00:00:1920@48kHz
 */
public class TimecodeComparator implements Comparator<Timecode>
{
	private static final TimecodeComparator INSTANCE = new TimecodeComparator();


	public static TimecodeComparator getInstance()
	{
		return INSTANCE;
	}


	@Override
	public int compare(Timecode a, Timecode b)
	{
		return cmp(a, b);
	}


	public static int cmp(Timecode a, Timecode b)
	{
		final long[] aFields = getFields(a);
		final long[] bFields = getFields(b);

		for (int i = 0; i < aFields.length; i++)
		{
			final int result = compare(aFields[i], bFields[i]);

			if (result != 0)
				return result;
		}

		return 0;
	}


	/**
	 * Translate a timecode into a series of long fields, adjusted to be positive/negative based on the timecode
	 *
	 * @param tc
	 *
	 * @return
	 */
	private static long[] getFields(final Timecode tc)
	{
		// Flip the sign on all fields for negative timecodes
		final int negativiser = tc.isNegative() ? -1 : 1;

		return new long[]{negativiser * tc.getDaysPart(),
		                  negativiser * tc.getHoursPart(),
		                  negativiser * tc.getMinutesPart(),
		                  negativiser * tc.getSecondsPart(),
		                  negativiser * tc.getFramesPartAsMicroseconds()};
	}


	private static int compare(long a, long b)
	{
		if (a < b)
			return -1;
		else if (a == b)
			return 0;
		else
			return 1;
	}


	/**
	 * Returns true if <code>test</code> is between <code>start</code> and <code>end</code> (inclusive)
	 *
	 * @param test
	 * @param start
	 * @param end
	 *
	 * @return
	 */
	public static boolean between(Timecode test, Timecode start, Timecode end)
	{
		return (test.ge(start) && test.le(end));
	}


	public static boolean eq(final Timecode a, final Timecode b)
	{
		return cmp(a, b) == 0;
	}


	public static boolean gt(final Timecode a, final Timecode b)
	{
		return cmp(a, b) > 0;
	}


	public static boolean lt(final Timecode a, final Timecode b)
	{
		return cmp(a, b) < 0;
	}


	public static boolean ge(final Timecode a, final Timecode b)
	{
		return cmp(a, b) >= 0;
	}


	public static boolean le(final Timecode a, final Timecode b)
	{
		return cmp(a, b) <= 0;
	}


	/**
	 * Returns the larger of a number of timecodes
	 *
	 * @param timecodes
	 * 		some timecodes
	 *
	 * @return
	 */
	public static Timecode maxTimecode(final Timecode... timecodes)
	{
		Timecode max = null;

		for (Timecode timecode : timecodes)
			if (max == null || lt(max, timecode))
				max = timecode;

		return max;
	}


	/**
	 * Returns the smaller of some timecodes
	 *
	 * @param timecodes
	 * 		some timecodes
	 *
	 * @return
	 */
	public static Timecode minTimecode(final Timecode... timecodes)
	{
		Timecode min = null;

		for (Timecode timecode : timecodes)
			if (min == null || ge(min, timecode))
				min = timecode;

		return min;
	}
}
