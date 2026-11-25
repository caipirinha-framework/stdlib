package com.peterphi.std.guice.restclient.jaxb.webquery;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WQDatesTest
{
	@Test
	public void testNowPlusMinusZero()
	{
		assertEquals("now", WQDates.nowMinus(Period.millis(0)));
		assertEquals("now", WQDates.nowPlus(Period.millis(0)));
	}

	@Test
	public void testEpochMs()
	{
		assertEquals(new DateTime(0), WQDates.resolve("epochms_0"));
	}


	@Test
	public void testNowPlusMinusMinutes()
	{
		assertEquals("now-PT62M", WQDates.nowMinus(Period.minutes(62)));
		assertEquals("now+PT62M", WQDates.nowPlus(Period.minutes(62)));
	}


	@Test
	public void testNowPlusMinusDays()
	{
		assertEquals("now-P1000D", WQDates.nowMinus(Period.days(1000)));
		assertEquals("now+P1000D", WQDates.nowPlus(Period.days(1000)));
	}


	@Test
	public void testTodayPlusMinusDays()
	{
		assertEquals("today-P1000D", WQDates.encode(WQDates.WebQueryDateAnchor.TODAY,false, Period.days(1000)));
		assertEquals("today+P1000D", WQDates.encode(WQDates.WebQueryDateAnchor.TODAY, true, Period.days(1000)));
	}
}
