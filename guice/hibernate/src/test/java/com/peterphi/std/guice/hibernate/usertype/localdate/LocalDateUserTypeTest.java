package com.peterphi.std.guice.hibernate.usertype.localdate;

import com.google.inject.Inject;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.joda.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties",
               classPackages = LocalDateUserTypeTest.class)
public class LocalDateUserTypeTest
{
	@Inject
	HibernateDao<LocalDateEntity, Long> dao;

	@Test
	public void testThatDateTimeVersionGetsUpdatedOnStore() throws Exception
	{
		LocalDateEntity obj = new LocalDateEntity();
		obj.someDate = new LocalDate("2001-01-01");

		obj = dao.merge(obj);

		assertEquals(new LocalDate("2001-01-01"), obj.someDate);
		assertNull(obj.someNullDate);

		// Now make sure we can change the date
		obj.someDate = new LocalDate();

		dao.update(obj);
	}
}
