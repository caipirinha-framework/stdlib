package com.peterphi.std.guice.hibernate.webquery.discriminatortest;

import com.google.inject.Inject;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.hibernate.webquery.ConstrainedResultSet;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.testing.GuiceTest;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GuiceTest
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties", classPackages = MyBaseObject.class)
public class DiscriminatorDynamicQueryTest
{
	@Inject
	HibernateDao<MyBaseObject, Long> dao;


	@Test
	public void testHibernateSubclassSupport()
	{
		MyChildObject1 a = new MyChildObject1();
		MyChildObject2 b = new MyChildObject2();

		a.id = dao.merge(a).id;
		b.id = dao.merge(b).id;

		// Make sure the right subclass is returned from a getById query
		assertTrue(dao.getById(a.id) instanceof MyChildObject1);
		assertTrue(dao.getById(b.id) instanceof MyChildObject2);
	}


	/**
	 * Tests that if we specify a subclass then the fields from that subclass become available (also true for a collection of
	 * subclasses in a nested hierarchy, but that complex hierarchy is rarely used in the db so we don't have a test for it)
	 */
	@Test
	public void testDiscriminatorInWebQueryAllowsUseOfSubclassFields()
	{
		MyChildObject1 a = new MyChildObject1();

		MyChildObject2 b = new MyChildObject2();

		a.id = dao.merge(a).id;
		b.id = dao.merge(b).id;


		final List<MyBaseObject> results = dao.findByUriQuery(new WebQuery().subclass("one").eq("someId", a.someId)).getList();

		assertEquals(1, results.size(), "should match exactly 1 entity");
		assertEquals(a.id, results.get(0).id, "should match the first child object stored");
	}


	@Test
	public void testDiscriminatorInWebQuery()
	{
		MyChildObject1 a = new MyChildObject1();
		MyChildObject2 b = new MyChildObject2();

		a.id = dao.merge(a).id;
		b.id = dao.merge(b).id;

		{
			// We'd get a org.hibernate.QueryException if Hibernate doesn't understand
			ConstrainedResultSet<MyBaseObject> results = dao.findByUriQuery(new WebQuery()
					                                                                .eq("id",
					                                                                    String.valueOf(a.id),
					                                                                    String.valueOf(b.id))
					                                                                .subclass("one"));

			assertEquals(1, results.getList().size(), "discriminator should match exactly one entity");
			assertEquals(a.id, results.getList().get(0).id);
		}
	}
}
