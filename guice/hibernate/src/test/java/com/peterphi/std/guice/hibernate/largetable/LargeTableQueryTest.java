package com.peterphi.std.guice.hibernate.largetable;

import com.google.inject.Inject;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.hibernate.webquery.ConstrainedResultSet;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties", classPackages = LargeTableQueryTest.class)
public class LargeTableQueryTest
{
	@Inject
	MyTestDaoImpl dao;

	@Inject
	HibernateDao<LargeTableComplexPKEntity, SomePrimaryKey> complexPKdao;


	/**
	 * Test that searching works with a simple primary key
	 */
	@Test
	@Transactional
	public void testSearchForPrimaryKeyWorks()
	{
		dao.persist(new LargeTableSimplePKEntity("Alice"));
		dao.persist(new LargeTableSimplePKEntity("Bob"));
		dao.persist(new LargeTableSimplePKEntity("Carol"));
		dao.persist(new LargeTableSimplePKEntity("Dave"));
		dao.persist(new LargeTableSimplePKEntity("Eve"));

		// Try a regular query
		assertEquals("custom-coded count should return 2", 2, dao.countWithAInName());

		// Now try a web query
		final ConstrainedResultSet<Long> result = dao.findIdsByUriQuery(new WebQuery().computeSize(true).contains("name", "a"));

		assertEquals("total results for generic query should be 2", Long.valueOf(2), result.getTotal());
		assertEquals("generic get IDs should return 2 results",
		             new ArrayList<>(Arrays.asList(3L, 4L)),
		             new ArrayList<>(result.getList()));
	}


	/**
	 * Test that searching works with a simple primary key
	 */
	@Test
	@Transactional
	public void testSearchWorks()
	{
		dao.persist(new LargeTableSimplePKEntity("Alice"));
		dao.persist(new LargeTableSimplePKEntity("Bob"));
		dao.persist(new LargeTableSimplePKEntity("Carol"));
		dao.persist(new LargeTableSimplePKEntity("Dave"));
		dao.persist(new LargeTableSimplePKEntity("Eve"));

		// Try a regular query
		assertEquals(2, dao.countWithAInName());

		// Now try a web query
		assertEquals(2, dao.findByUriQuery(new WebQuery().contains("name", "a")).getList().size());
	}


	/**
	 * Test that searching works with ordering
	 */
	@Test
	@Transactional
	public void testSearchWorksWithOrder()
	{
		dao.persist(new LargeTableSimplePKEntity("Alice"));
		dao.persist(new LargeTableSimplePKEntity("Bob"));
		dao.persist(new LargeTableSimplePKEntity("Carol"));
		dao.persist(new LargeTableSimplePKEntity("Dave"));
		dao.persist(new LargeTableSimplePKEntity("Eve"));

		// Now try a web query

		List<LargeTableSimplePKEntity> list = dao
				                                      .findByUriQuery(new WebQuery().contains("name", "l").orderDesc("name"))
				                                      .getList();

		List<String> expected = Arrays.asList("Carol", "Alice");
		List<String> actual = list.stream().map(e -> e.name).collect(Collectors.toList());

		assertEquals(expected, actual);
	}


	/**
	 * Test that searching works even with a complex primary key
	 */
	@Test
	@Transactional
	public void testSearchComplexPK()
	{
		complexPKdao.persist(new LargeTableComplexPKEntity("Alice"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Bob"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Carol"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Dave"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Eve"));

		assertEquals(2, complexPKdao.findByUriQuery(new WebQuery().contains("name", "a")).getList().size());
	}


	/**
	 * Test that searching for IDs works even with a complex primary key
	 */
	@Test
	@Transactional
	public void testSearchComplexPKIDs()
	{
		complexPKdao.persist(new LargeTableComplexPKEntity("Alice"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Bob"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Carol"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Dave"));
		complexPKdao.persist(new LargeTableComplexPKEntity("Eve"));

		final List<SomePrimaryKey> results = complexPKdao.findIdsByUriQuery(new WebQuery().contains("name", "a")).getList();

		assertEquals("expect 2 results", 2, results.size());
		assertEquals("expect results to be of id type", SomePrimaryKey.class, results.get(0).getClass());
		assertEquals("expect results to be of id type", SomePrimaryKey.class, results.get(1).getClass());
	}
}
