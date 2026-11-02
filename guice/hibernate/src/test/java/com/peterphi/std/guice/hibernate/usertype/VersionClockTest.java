package com.peterphi.std.guice.hibernate.usertype;

import org.joda.time.DateTime;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class VersionClockTest
{
	@Test
	public void dateVersionAdvancesWhenClockHasNotPassedCurrentVersion()
	{
		Date current = new Date(System.currentTimeMillis() + 86400000L);
		assertEquals(current.getTime() + 1, DateUserType.INSTANCE.next(current, null).getTime());
	}

	@Test
	public void dateTimeVersionAdvancesWhenClockHasNotPassedCurrentVersion()
	{
		DateTime current = DateTime.now().plusDays(1);
		assertEquals(current.getMillis() + 1, JodaDateTimeUserType.INSTANCE.next(current, null).getMillis());
	}
}
