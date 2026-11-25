package com.peterphi.std.guice.hibernate.usertype.datetime;

import com.google.inject.Inject;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.testing.GuiceTest;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that a DateTime field marked with a Version annotation gets a new value when an update occurs
 */
@GuiceTest
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties",
		            classPackages = DateTimeUserTypeVersionFieldTest.class)
public class DateTimeUserTypeVersionFieldTest
{

	@Inject
	HibernateDao<ObjWithDateTimeVersionField, Long> dao;


	@Test
	public void testThatDateTimeVersionGetsUpdatedOnStore() throws Exception
	{
		ObjWithDateTimeVersionField obj = new ObjWithDateTimeVersionField();
		final DateTime initialVersion = obj.getLastUpdated();

		obj = dao.merge(obj);

		assertEquals(initialVersion, obj.getLastUpdated(), "Version should be unmodified for initial store");

		obj.setSomeString("something else");
		dao.update(obj);

		obj = dao.getById(obj.getId());

		final DateTime nextVersion = obj.getLastUpdated();

		assertNotEquals(initialVersion, nextVersion, "Version should be changed after update");
		assertTrue(initialVersion.isBefore(nextVersion), "Version date for later update should be later than original time");
	}
}
