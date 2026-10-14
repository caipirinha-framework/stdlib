package com.peterphi.std.types;

/**
 * Represents a range between two non-null timecodes
 */
public class TimecodeRange
{
	private final Timecode start;
	private final Timecode end;


	public TimecodeRange(Timecode start, Timecode end)
	{
		if (start == null)
			throw new IllegalArgumentException("Start timecode may not be null!");
		else if (end == null)
			throw new IllegalArgumentException("End timecode may not be null!");
		else if (end.lt(start))
			throw new IllegalArgumentException("End of range " + end + " is before start " + start + "!");

		this.start = start;
		this.end = end;
	}


	public TimecodeRange(Timecode start, SampleCount duration)
	{
		this(start, start.add(duration));
	}

	//
	// Getters
	//


	public Timecode getStart()
	{
		return start;
	}


	public Timecode getEnd()
	{
		return end;
	}


	public SampleCount getDuration()
	{
		return end.getSampleCount(start);
	}

	//
	// Modification
	//


	/**
	 * Constructs a new TimecodeRange where the values are expressed as offsets from <code>reference</code>.
	 *
	 * @param reference
	 * 		the reference timecode. <b>Must be less than or equal to the start timecode for this range</b>
	 *
	 * @return
	 */
	public TimecodeRange toOffset(Timecode reference)
	{
		if (reference.gt(start))
			throw new IllegalArgumentException("Reference timecode " + reference + " is before start " + start + "!");

		final SampleCount samples = reference.getSampleCount();

		return subtract(samples);
	}


	/**
	 * Move the range left by the specified number of samples
	 *
	 * @param samples
	 *
	 * @return
	 */
	public TimecodeRange subtract(SampleCount samples)
	{
		final Timecode newStart = start.subtract(samples);
		final Timecode newEnd = end.subtract(samples);

		return new TimecodeRange(newStart, newEnd);
	}


	/**
	 * Move the range right by the specified number of samples
	 *
	 * @param samples
	 *
	 * @return
	 */
	public TimecodeRange add(SampleCount samples)
	{
		final Timecode newStart = start.add(samples);
		final Timecode newEnd = end.add(samples);

		return new TimecodeRange(newStart, newEnd);
	}

	public TimecodeRange resample(Timebase timebase)
	{
		return resample(timebase, timebase.canBeDropFrame());
	}


	public TimecodeRange resample(Timebase timebase, final boolean toDropFrame)
	{
		return new TimecodeRange(start.resample(timebase, toDropFrame), end.resample(timebase, toDropFrame));
	}


	//
	// Comparison
	//


	/**
	 * Tests whether a timecode lies within (or on the boundaries of) this range
	 *
	 * @param test
	 *
	 * @return
	 */
	public boolean within(Timecode test)
	{
		return TimecodeComparator.between(test, start, end);
	}


	/**
	 * Test whether a given timecode range lies wholly within (or on the boundaries of) this range
	 *
	 * @param test
	 * @return
	 */
	public boolean within(TimecodeRange test) {
		return within(test.getStart()) && within(test.getEnd());
	}

	/**
	 * Determines whether two timecode ranges overlap (or are equivalent)
	 *
	 * @param that
	 * 		some other timecode range. comparisons will be made based on the time represented
	 *
	 * @return true if the ranges overlap, otherwise false
	 */
	public boolean overlaps(TimecodeRange that)
	{
		if (this.within(that.getStart()) || this.within(that.getEnd()))
		{
			return true; // we contain one of their timecodes
		}
		else if (that.within(this.getStart()) || that.within(this.getEnd()))
		{
			return true; // they contain one of our timecodes
		}
		else
		{
			return false;
		}
	}


	/**
	 * Produce a new Timecode range which includes all timecodes from <code>a</code> and <code>b</code>. This may result in
	 * coverage
	 * of additional timecodes if the two ranges do not overlap.
	 *
	 * @param a
	 * @param b
	 *
	 * @return
	 */
	public static TimecodeRange merge(TimecodeRange a, TimecodeRange b)
	{
		final Timecode start = TimecodeComparator.minTimecode(a.getStart(), b.getStart());
		final Timecode end = TimecodeComparator.maxTimecode(a.getEnd(), b.getEnd());

		return new TimecodeRange(start, end);
	}


	public String toString()
	{
		return "[" + start.toEncodedString() + " -> " + end.toEncodedString() + "]";
	}


	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((end == null) ? 0 : end.hashCode());
		result = prime * result + ((start == null) ? 0 : start.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TimecodeRange other = (TimecodeRange) obj;
		if (end == null)
		{
			if (other.end != null)
				return false;
		}
		else if (!end.equals(other.end))
			return false;
		if (start == null)
		{
			if (other.start != null)
				return false;
		}
		else if (!start.equals(other.start))
			return false;
		return true;
	}
}
