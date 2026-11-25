package com.peterphi.std.guice.liquibase.hibernate;

import com.google.inject.Inject;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.testing.GuiceTest;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@GuiceTest
@GuiceConfig(config = {"liquibase-hibernate-tests-in-memory-hsqldb.properties"}, classPackages = SomeEntity.class)
public class LiquibaseCoreTest
{
	@Inject
	HibernateDao<SomeEntity, Long> dao;


	@Test
	public void testThatLiquibaseMigrationInsertedTestData()
	{
		assertEquals(1, dao.getAll().size(), "Initial data insert should have succeeded");
		assertEquals("Example Object", dao.getById(1L).name, "Initial data insert name field");
	}
}
