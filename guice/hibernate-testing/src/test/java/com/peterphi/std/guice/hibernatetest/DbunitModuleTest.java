package com.peterphi.std.guice.hibernatetest;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.peterphi.std.guice.common.shutdown.iface.ShutdownManager;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.hibernate.module.HibernateTransaction;
import com.peterphi.std.guice.hibernate.module.TransactionHelper;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.dbunit.assertion.DbUnitAssert;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.StringReader;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "com/peterphi/std/guice/hibernatetest/hsqldb-in-memory.properties", classPackages = GroupEntity.class)
public class DbunitModuleTest
{
	@Inject
	ShutdownManager shutdownManager;

	@Inject
	HibernateDao<SimpleEntity, Long> dao;

	@Inject
	TransactionHelper txutils;

	@Inject
	Provider<IDataSet> dataset;


	@Test
	public void test() throws Exception
	{
		// DB is initially empty
		new DbUnitAssert().assertEquals(new FlatXmlDataSetBuilder().build(new StringReader("<dataset><SimpleEntity/><GroupEntity/><simple_entity_join_table/></dataset>")),
		                                dataset.get());

		// Add some db contents
		try (HibernateTransaction tx = txutils.start().withAutoCommit())
		{
			dao.persist(new SimpleEntity(1, "alice", new GroupEntity(1), new GroupEntity(2)));
			dao.persist(new SimpleEntity(2, "bob"));
			dao.persist(new SimpleEntity(3, "carol"));
			dao.persist(new SimpleEntity(4, "dave"));
		}

		// DB should now have 4 rows
		FlatXmlDataSet expected = new FlatXmlDataSetBuilder().build(this.getClass()
		                                                                .getResourceAsStream("/com/peterphi/std/guice/hibernatetest/alice-bob-carol-dave-dataset.xml"));
		new DbUnitAssert().assertEquals(expected, dataset.get());
	}
}
