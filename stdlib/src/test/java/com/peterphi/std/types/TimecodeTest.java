package com.peterphi.std.types;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimecodeTest
{
	@Test
	public void testResample_IdentityFunction_Zero()
	{
		final Timecode src = Timecode.getInstance("00:00:00:00", Timebase.HZ_25);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals(src.toString(), dst.toString());
	}


	@Test
	public void testResample_IdentityFunction()
	{
		final Timecode src = Timecode.getInstance("01:02:03:04", Timebase.HZ_25);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals(src.toString(), dst.toString());
	}


	@Test
	public void testResample()
	{
		final Timecode src = Timecode.getInstance("09:08:07:04", Timebase.HZ_50);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals("09:08:07:02", dst.toString());
	}


	@Test
	public void testResampleRounding()
	{
		//tests resampling that results in rounding up to the next whole second
		final Timecode src = Timecode.getInstance("09:08:07:999", Timebase.HZ_1000);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals("09:08:08:00", dst.toString());
	}


	@Test
	public void testBounds()
	{
		final Timecode small = Timecode.getInstance("00:00:00:00", Timebase.HZ_50);
		final Timecode big = Timecode.getInstance("00:00:00:01", Timebase.HZ_50);

		assertTrue(small.lt(big));
		assertTrue(big.gt(small));
	}


	@Test
	public void testResamplePrecise_HappyPath()
	{
		final Timecode src = Timecode.getInstance("09:08:07:02", Timebase.HZ_50);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals("09:08:07:01", dst.toString());
	}


	@Test
	public void testResamplePrecise_HappyPath_Zero()
	{
		final Timecode src = Timecode.getInstance("00:00:00:00", Timebase.HZ_50);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals("00:00:00:00", dst.toString());
	}


	@Test
	public void testResamplePrecise_Imprecise() throws Exception
	{
		final Timecode src = Timecode.getInstance("09:08:07:03", Timebase.HZ_50);

		assertThrows(ResamplingException.class, () -> src.resamplePrecise(Timebase.HZ_25));
	}


	@Test
	public void testResample_Imprecise()
	{
		final Timecode src = Timecode.getInstance("09:08:07:03", Timebase.HZ_50);
		final Timecode dst = src.resample(Timebase.HZ_25);

		assertEquals("09:08:07:02", dst.toString());
	}


	@Test
	public void testGetSampleCountDelta_Zero()
	{
		final Timecode base = Timecode.getInstance("00:00:00:00", Timebase.HZ_50);
		final Timecode tc = Timecode.getInstance("00:00:00:00", Timebase.HZ_25);

		final SampleCount delta = tc.getSampleCount(base);

		assertEquals(0, delta.getSamples());
		assertEquals(0, tc.getSampleCount().getSamples());
	}


	@Test
	public void testGetSampleCountDelta()
	{
		// 2 timecodes which are 1h2m3s and 4 frames apart
		final Timecode base = Timecode.getInstance("01:02:03:04", Timebase.HZ_50);
		final Timecode tc = Timecode.getInstance("02:04:06:08", Timebase.HZ_50);

		final SampleCount delta = tc.getSampleCount(base);
		final Timecode deltaTC = Timecode.getInstance(delta);

		assertEquals("01:02:03:04", deltaTC.toString());
	}


	@Test
	public void testDurationInSeconds()
	{
		final Timecode base = Timecode.getInstance("01:02:03:49", Timebase.HZ_50);

		assertEquals(3723, base.getDurationInSeconds());
	}


	/**
	 * Test that -00:00:00:02 is parsed and correctly and when encoded to samples returns the right number of samples
	 */
	@Test
	public void testSampleCountForNegativeTimecode()
	{
		final Timecode timecode = Timecode.getInstance("-00:00:00:02@25");
		SampleCount samples = new SampleCount(-2, Timebase.HZ_25);

		assertEquals(samples, timecode.getSampleCount());
	}


	@Test
	@Deprecated
	public void testGetInstanceWithNegativeSampleCount()
	{
		final Timecode tc = Timecode.getInstance(new SampleCount(-2, Timebase.HZ_25), false);

		assertEquals("-00:00:00:02@25", tc.toEncodedString());
	}


	@Test
	public void testAddNegativeSamples()
	{
		final SampleCount samples = new SampleCount(-2, Timebase.HZ_25);

		final Timecode base = Timecode.getInstance("-00:00:00:02@25");

		final Timecode tc = base.add(samples);

		assertEquals("-00:00:00:04@25", tc.toEncodedString());
	}


	/**
	 * The SMPTE string shouldn't include days
	 */
	@Test
	public void testAddSmpte()
	{
		final Timecode base = Timecode.getInstance("23:29:59:05", Timebase.HZ_25);
		final Timecode add = Timecode.getInstance("00:59:59:22", Timebase.HZ_25);
		final Timecode tc = base.add(add.getSampleCount());

		assertEquals("00:29:59:02", tc.toSMPTEString());
	}


	/**
	 * The SMPTE string including days should include days
	 */
	@Test
	public void testAddSmpteIncludingDays()
	{
		final Timecode base = Timecode.getInstance("23:29:59:05", Timebase.HZ_25);
		final Timecode add = Timecode.getInstance("00:59:59:22", Timebase.HZ_25);
		final Timecode tc = base.add(add.getSampleCount());

		assertEquals("01:00:29:59:02", tc.toSMPTEString(true));
	}


	@Test
	public void testSubtractNegativeSamples()
	{
		final SampleCount samples = new SampleCount(-2, Timebase.HZ_25);

		final Timecode base = Timecode.getInstance("-00:00:00:02@25");

		final Timecode tc = base.subtract(samples);

		assertEquals("00:00:00:00@25", tc.toEncodedString());
	}


	/**
	 * Not a valid timecode representation (frame count is >= 1 second at this timebase)
	 */
	@Test
	public void testDenormalisedFrameField()
	{
		assertThrows(IllegalArgumentException.class, () -> Timecode.getInstance("00:00:00:25@25")); // frames >= timebase
	}


	/**
	 * Negative zero timecode should be converted to positive zero
	 */
	@Test
	public void testNegativeZeroTimecode()
	{
		final Timecode tc = Timecode.getInstance("-00:00:00:00@25");

		assertEquals("00:00:00:00@25", tc.toEncodedString());
	}


	/**
	 * Timecode eq should be based on the instant in time it refers to
	 */
	@Test
	public void testEq()
	{
		final Timecode a = Timecode.getInstance("01:02:03:04@25");
		final Timecode b = Timecode.getInstance("01:02:03:08@50");

		assertTrue(a.eq(b));
	}


	/**
	 * Negative zero timecode should be converted to positive zero
	 */
	@Test
	public void testBetween()
	{
		final Timecode start = Timecode.getInstance("-01:02:03:04@25");
		final Timecode end = Timecode.getInstance("03:02:03:04@25");

		final List<String> yes = Arrays.asList("-01:02:03:04",
		                                       "00:00:00:00",
		                                       "01:00:00:00",
		                                       "02:00:00:00",
		                                       "03:00:00:00",
		                                       "03:02:03:03",
		                                       "03:02:03:04");
		final List<String> no = Arrays.asList("-01:02:03:05", "03:02:03:05", "-02:03:04:05", "04:00:00:00");

		for (String smpte : yes)
		{
			final Timecode tc = Timecode.getInstance(smpte, Timebase.HZ_25);

			final boolean between = tc.between(start, end);

			assertTrue(between, smpte + " should be between " + start + " and " + end);
		}

		for (String smpte : no)
		{
			final Timecode tc = Timecode.getInstance(smpte, Timebase.HZ_25);

			final boolean between = tc.between(start, end);

			assertFalse(between, smpte + " shouldn't be between " + start + " and " + end);
		}
	}


	@Test
	public void testToSMPTEString()
	{
		assertEquals("00:00:00:00", Timecode.valueOf("00:00:00:00@25").toSMPTEString());
		assertEquals("00:00:00:24", Timecode.valueOf("00:00:00:24@25").toSMPTEString());
		assertEquals("00:00:10:24", Timecode.valueOf("00:00:10:24@25").toSMPTEString());
		assertEquals("00:15:10:24", Timecode.valueOf("00:15:10:24@25").toSMPTEString());
		assertEquals("20:15:10:24", Timecode.valueOf("20:15:10:24@25").toSMPTEString());
	}


	@Test
	public void testToEncodedString()
	{
		assertEquals("00:00:00:00@25", Timecode.valueOf("00:00:00:00@25").toEncodedString());
		assertEquals("00:00:00:24@25", Timecode.valueOf("00:00:00:24@25").toEncodedString());
		assertEquals("00:00:10:24@25", Timecode.valueOf("00:00:10:24@25").toEncodedString());
		assertEquals("00:15:10:24@25", Timecode.valueOf("00:15:10:24@25").toEncodedString());
		assertEquals("20:15:10:24@25", Timecode.valueOf("20:15:10:24@25").toEncodedString());
	}


	@Test
	public void testToEncodedStringWithDays()
	{
		assertEquals("20:15:10:24@25", Timecode.valueOf("20:15:10:24@25").toEncodedString(true));
		assertEquals("30:20:15:10:24@25", Timecode.valueOf("30:20:15:10:24@25").toEncodedString(true));
	}


	@Test
	public void testToISODurationWithFrames()
	{
		assertEquals("PT00H00M00S00F", Timecode.valueOf("00:00:00:00@25").toISODurationWithFrames(false));
		assertEquals("PT00H00M00S00F", Timecode.valueOf("00:00:00:00@25").toISODurationWithFrames(true));
		assertEquals("PT00H00M00S24F", Timecode.valueOf("00:00:00:24@25").toISODurationWithFrames(true));
		assertEquals("PT00H00M10S24F", Timecode.valueOf("00:00:10:24@25").toISODurationWithFrames(true));
		assertEquals("PT00H15M10S24F", Timecode.valueOf("00:15:10:24@25").toISODurationWithFrames(true));
		assertEquals("PT20H15M10S24F", Timecode.valueOf("20:15:10:24@25").toISODurationWithFrames(true));

		assertEquals("P01DT20H15M10S24F", Timecode.valueOf("01:20:15:10:24@25").toISODurationWithFrames(true));
		assertEquals("P30DT20H15M10S24F", Timecode.valueOf("30:20:15:10:24@25").toISODurationWithFrames(true));
	}


	@Test
	public void test107892FramesInDropFrameNTSCIsOneHourExactly()
	{
		final Timecode timecode = TimecodeBuilder.fromFrames(107892, Timebase.NTSC).build();

		assertEquals("01:00:00;00@30000:1001", timecode.toEncodedString(), "forwards: 107,892 frames of NTSC is 1 hour exactly");
		assertEquals(107892, timecode.getDurationInFrames(), "backwards: one hour in NTSC is 107,892 frames");
		assertEquals("01:00:00;01@30000:1001",
		             timecode.add(SampleCount.valueOf("1@NTSC")).toEncodedString(),
		             "adding 1 NTSC frame to 1 hour in NTSC should yield 1h and 1 frame");
	}


	@Test
	public void testDropFrameResampleToCarbon()
	{
		Timecode start = Timecode.valueOf("01:00:00;00@30000:1001");
		Timecode firstPartOut = Timecode.valueOf("01:30:49;29@30000:1001");
		final Timecode partOutAbsolute = firstPartOut.subtract(start.getSampleCount());

		assertEquals("00:30:49;29@30000:1001",partOutAbsolute.toEncodedString(),"input");
		assertEquals("55445@30000:1001",partOutAbsolute.getSampleCount().toString(),"input");

		assertEquals("00:30:50:450450@27000000",partOutAbsolute.resample(Timebase.HZ_27000000).toEncodedString(), "resample to 27MHz");
		assertEquals("49950450450@27000000",partOutAbsolute.resample(Timebase.HZ_27000000).getSampleCount().toString(), "resample to 27MHz");
	}


	@Test
	public void testNTSC23976Conversions()
	{
		// N.B. doing this conversion with milliseconds results in error creeping in (because 1 hour of 24p is actually 86313.6 frames
		final SampleCount sOneHourS = SampleCount.valueOf("3600@1");
		final SampleCount sOneHour24p = SampleCount.valueOf("86314@24000:1001");

		assertEquals(sOneHourS, sOneHour24p.resample(Timebase.HZ_1), "1 hour of 24p should resample down to one hour in seconds");
		assertEquals(sOneHour24p, sOneHourS.resample(Timebase.NTSC_24), "1 hour in seconds should resample to one hour in 24p");

		Timecode tOneHour24p = Timecode.getInstance(sOneHour24p);
		assertEquals("00:59:56:10@24000:1001", tOneHour24p.toEncodedString(), "one hour of 24p in non-drop frame timecode");
		assertEquals("00:59:56:10@24000:1001", tOneHour24p.toEncodedString(), "one hour of 24p in non-drop frame timecode");
	}

	@Test
	public void testOneHourAndOneMinuteDropFrameNTSC()
	{
		final long oneHour = 107892;
		final long frame1 = oneHour + 1800;
		final long frame2 = oneHour + 1799;

		final Timecode timecode1 = TimecodeBuilder.fromFrames(frame1, Timebase.NTSC).build();
		final Timecode timecode2 = TimecodeBuilder.fromFrames(frame2, Timebase.NTSC).build();

		// Make sure this results in the timecode we expect
		assertEquals("01:01:00;02@30000:1001", timecode1.toEncodedString(), "forward conversion (1h 1m)");
		assertEquals("01:00:59;29@30000:1001", timecode2.toEncodedString(), "forward conversion (1h 59s 29 frames)");

		// Make sure the duration in frames is what we expect
		assertEquals(frame1, timecode1.getDurationInFrames(), "backward conversion (1h 1m)");
		assertEquals(frame2, timecode2.getDurationInFrames(), "backward conversion (1h 59s 29 frames)");

		assertEquals(frame1, Timecode.valueOf("01:01:00;02@30000:1001").getDurationInFrames(), "backwards timecode conversion (1h 1m)");
		assertEquals(frame2, Timecode.valueOf("01:00:59;29@30000:1001").getDurationInFrames(), "backwards timecode conversion (1h 59s 29 frames)");

		// Make sure that adding and subtracting frames works
		assertEquals("01:00:59;29@30000:1001", timecode1.subtract(SampleCount.valueOf("1@NTSC")).toEncodedString(), "subtracting 1 sample");
		assertEquals("01:01:00;03@30000:1001", timecode1.add(SampleCount.valueOf("1@NTSC")).toEncodedString(), "adding 1 sample");
	}


	@Test
	public void testOneHourOfTimeTimeToDropFrameNTSCTimecode() throws ResamplingException
	{
		SampleCount oneHourInMillis = SampleCount.seconds(Timebase.HZ_1000, 3600);
		SampleCount ntscFrames = oneHourInMillis.resample(Timebase.NTSC);

		Timecode timecode = TimecodeBuilder.fromSamples(ntscFrames, true).build();

		assertEquals("01:00:00;00", timecode.toSMPTEString());
	}


	@Test
	public void testCountingToTwoMinutesInDropFrameTimecode() throws ResamplingException
	{
		Timecode timecode = TimecodeBuilder.fromFrames(0, Timebase.NTSC).build();
		final SampleCount oneFrame = new SampleCount(1, Timebase.NTSC);

		for (int i = 0; i < 1800 * 2; i++)
		{
			timecode = timecode.add(oneFrame);
		}

		assertEquals("00:02:00;04", timecode.toSMPTEString());
	}


	@Test
	public void testDropFrameNTSCParsing()
	{
		Timecode tc = Timecode.valueOf("01:23:45;06@NTSC");

		final long expected = 150606;

		assertEquals(expected, tc.getDurationInFrames());
	}


	@Test
	public void testDateToDropFrameNTSCTimecode()
	{
		final long expected = 111884;
		final Timecode tc = Timecode.valueOf("01:02:13;06@NTSC");

		assertEquals(expected, tc.getDurationInFrames(), "forward");
		final Timecode reversed = Timecode.getInstance(expected, true, Timebase.NTSC);
		assertEquals(tc, reversed, "backward");
	}
}
