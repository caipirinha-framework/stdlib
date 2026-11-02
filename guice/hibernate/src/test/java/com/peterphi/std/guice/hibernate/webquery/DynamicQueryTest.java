package com.peterphi.std.guice.hibernate.webquery;

import com.google.inject.Inject;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.hibernate.dao.QueryPrivilegeData;
import com.peterphi.std.guice.hibernate.module.TransactionHelper;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntityFactory;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.PrivatePropertyUseRejected;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPASearchExecutor;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPASearchStrategy;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties", classPackages = ParentEntity.class)
public class DynamicQueryTest
{
	@Inject
	HibernateDao<ParentEntity, Long> dao;

	@Inject
	HibernateDao<ChildEntity, Long> childDao;

	@Inject
	HibernateDao<MappedSuperclassEntity, Long> mappedSuperclassEntityDao;

	@Inject
	QEntityFactory entityFactory;

	@Inject
	TransactionHelper txutils;



	@Transactional
	@Before
	public void clearDatabaseBeforeTest()
	{
		JPASearchExecutor.ALWAYS_COMPUTE_SIZE = true;

		for (ChildEntity obj : childDao.getAll())
			childDao.delete(obj);

		for (ParentEntity obj : dao.getAll())
			dao.delete(obj);

		assertEquals(0, dao.getAll().size());
	}


	@After
	public void disableAlwaysComputeSize()
	{
		JPASearchExecutor.ALWAYS_COMPUTE_SIZE = false;
	}


	@Test
	public void testMappedSuperclassFieldSearch()
	{
		loadMappedSuperclassEntity();

		queryMappedSuperclassEntity();
	}

	@Transactional
	public void queryMappedSuperclassEntity()
	{
		final ConstrainedResultSet<MappedSuperclassEntity> resultset = mappedSuperclassEntityDao.findByUriQuery(new WebQuery().logSQL(true)
		                                                                                                                      .neq("id",
				                                                                                                             123)
		                                                                                                                      .eq("name",
				                                                                                                            "test")
		                                                                                                                      .le("created",
		                                                                                                                          DateTime.now()));
		final List<MappedSuperclassEntity> list = resultset.getList();

		System.out.println(resultset.getSql());

		assertEquals(1, list.size());

		System.out.println(list.get(0).getClass());
		System.out.println(list.get(0).getChild().getClass());
		System.out.println(list.get(0).getChild().getCreated());
		list.get(0).getChild().getName();
	}


	@Transactional
	public void loadMappedSuperclassEntity()
	{
		MappedSuperclassEntity toSave = new MappedSuperclassEntity();
		toSave.setName("test");
		toSave.setChild(new ChildEntity());
		toSave.getChild().setName("some name");

		childDao.persist(toSave.getChild());
		mappedSuperclassEntityDao.persist(toSave);
	}


	@Test
	public void testCreateSchema()
	{
		final Set<String> names = entityFactory.encode().entities.stream().map(s -> s.name).collect(Collectors.toSet());

		assertTrue(names.contains("inherit_base"));
		assertTrue(names.contains("inherit_one"));
		assertTrue(names.contains("inherit_two"));
	}


	@Test
	public void testQueryForStateEnum()
	{
		childDao.find(new WebQuery().eq("state", 0));
	}


	@Test(expected = PrivatePropertyUseRejected.class)
	public void testCannotConstrainPrivateAnnotatedField()
	{
		dao.find(new WebQuery().eq("somePrivateString", "secret"));
	}


	@Test(expected = PrivatePropertyUseRejected.class)
	public void testCannotProjectPrivateAnnotatedField()
	{
		dao.find(new WebQuery().fetch("id,somePrivateString"));
	}


	@Test(expected = PrivatePropertyUseRejected.class)
	public void testCannotSortByPrivateAnnotatedField()
	{
		dao.find(new WebQuery().orderAsc("somePrivateString"));
	}


	@Test(expected = PrivatePropertyUseRejected.class)
	public void testNormalPrivilegedCallCannotConstrainPrivateAnnotatedField()
	{
		dao.find(new WebQuery().eq("somePrivateString", "secret"), JPASearchStrategy.AUTO, null, QueryPrivilegeData.NORMAL);
	}

	@Test
	public void testPrivilegedCallCanConstrainPrivateAnnotatedField()
	{
		dao.find(new WebQuery().eq("somePrivateString", "secret"), JPASearchStrategy.AUTO, null, QueryPrivilegeData.FULL);
	}



	/**
	 * Test that supplying a comma-separated list of values to WebQuery.fetch results in those fields being returned from the db a an <code>Object[]</code>
	 *
	 * @throws Exception
	 */
	@Test
	@Transactional
	public void testMultipleFetchWorks() throws Exception
	{
		// Load first object
		final long id1;
		{
			ParentEntity obj = new ParentEntity();
			obj.setName("Name1");
			obj.setOtherObject(new ChildEntity());
			obj.getOtherObject().setName("CName1");

			childDao.persist(obj.getOtherObject());
			id1 = dao.merge(obj).getId();
		}

		// Load second
		final long id2;
		{
			ParentEntity obj = new ParentEntity();
			obj.setName("Name2");
			obj.setOtherObject(new ChildEntity());
			obj.getOtherObject().setName("CName2");

			childDao.persist(obj.getOtherObject());
			id2 = dao.merge(obj).getId();
		}

		// Selecting a list
		{
			final ConstrainedResultSet<Object[]> results = dao.project(new WebQuery()
					                                                        .fetch("id,name,otherObject.name,otherObject.entity")
					                                                        .orderAsc("name")
					                                                        .orderDesc("deprecated"), false);

			assertEquals("Expecting 2 rows", 2, results.getList().size());
			assertTrue("Expecting at least 3 columns", results.getList().get(0).length >= 3);

			// Assert that the expected value is output for the first row
			assertEquals("row1 id", id1, results.getList().get(0)[0]);
			assertEquals("row1 name", "Name1", results.getList().get(0)[1]);
			assertEquals("row1 otherObject.name", "CName1", results.getList().get(0)[2]);

			// Assert that the expected value is output for the second row
			assertEquals("row2 id", id2, results.getList().get(1)[0]);
			assertEquals("row2 name", "Name2", results.getList().get(1)[1]);
			assertEquals("row2 otherObject.name", "CName2", results.getList().get(1)[2]);
		}
	}

	/**
	 * WebQuery.fetch is otherObject.this, returning a relation
	 *
	 * @throws Exception
	 */
	@Test
	@Transactional
	public void testFetchRelationReturnsArray() throws Exception
	{
		// Load first object
		final long id1;
		{
			ParentEntity obj = new ParentEntity();
			obj.setName("Name1");
			obj.setOtherObject(new ChildEntity());
			obj.getOtherObject().setName("Name1");

			childDao.persist(obj.getOtherObject());
			id1 = dao.merge(obj).getId();
		}

		// Load second
		final long id2;
		{
			ParentEntity obj = new ParentEntity();
			obj.setName("Name2");
			obj.setOtherObject(new ChildEntity());
			obj.getOtherObject().setName("Name2");

			childDao.persist(obj.getOtherObject());
			id2 = dao.merge(obj).getId();
		}

		final ConstrainedResultSet<Object[]> results = dao.find(new WebQuery().fetch("otherObject.entity"), JPASearchStrategy.AUTO, row->(Object[])row);

		assertEquals("Expecting 2 rows", 2, results.getList().size());
		assertTrue("Expecting at least 1 column", results.getList().get(0).length >= 1);


		// N.B. we have not ordered the results so they may come back in any order, just assert that the set contains what we expect
		{
			final Object[] firstRow = results.getList().get(0);
			assertTrue(firstRow[0] instanceof ChildEntity);
		}
	}


	/**
	 * WebQuery.fetch is not "id" or "entity", but is only a single value (makes sure that the row type coming back from the db is an Object[])
	 *
	 * @throws Exception
	 */
	@Test
	@Transactional
	public void testSingleFetchReturnsAnObjectArray() throws Exception
	{
		// Load first object
		final long id1;
		{
			ParentEntity obj = new ParentEntity();
			obj.setName("Name1");
			obj.setOtherObject(new ChildEntity());
			obj.getOtherObject().setName("Name1");

			childDao.persist(obj.getOtherObject());
			id1 = dao.merge(obj).getId();
		}

		// Load second
		final long id2;
		{
			ParentEntity obj = new ParentEntity();
			obj.setName("Name2");
			obj.setOtherObject(new ChildEntity());
			obj.getOtherObject().setName("Name2");

			childDao.persist(obj.getOtherObject());
			id2 = dao.merge(obj).getId();
		}

		final ConstrainedResultSet<Object[]> results = dao.find(new WebQuery().fetch("name"), JPASearchStrategy.AUTO, r->(Object[])r);

		assertEquals("Expecting 2 rows", 2, results.getList().size());
		assertTrue("Expecting at least 1 column", results.getList().get(0).length >= 1);

		// N.B. we have not ordered the results so they may come back in any order, just assert that the set contains what we expect
		{
			Set<String> names = results.getList().stream().map(array -> (String) array[0]).collect(Collectors.toSet());

			assertEquals(new HashSet<>(Arrays.asList("Name1", "Name2")), names);
		}
	}


	@Test
	public void testNestedAssociatorConstraintWorks() throws Exception
	{
		// We'd get a org.hibernate.QueryException if Hibernate doesn't understand
		dao.findByUriQuery(new WebQuery().eq("otherObject.parent.name", "Alice"));
	}


	@Test
	public void testNotInConstraint()
	{
		dao.find(new WebQuery().notIn("otherObject.name", "Alice", "Bob", "Carol"));
	}


	@Test
	public void testInConstraint()
	{
		dao.find(new WebQuery().in("otherObject.name", "Alice", "Bob", "Carol"));
	}


	@Test
	public void testInConstraintWithOneItem()
	{
		dao.find(new WebQuery().in("otherObject.name", "Alice"));
	}


	/**
	 * This does not work natively with HSQLDB because HSQLDB cannot perform an ORDER BY on a column that isn't SELECTed, so this
	 * test confirms that WebQuery is able to implement it
	 *
	 * @throws Exception
	 */
	@Test
	public void testOrderingByLazyAssociatedRelationThatIsNotSelectedWorks() throws Exception
	{
		dao.findByUriQuery(new WebQuery().orderAsc("otherObject.name"));
	}


	@Test
	public void testPropertyRefWorks() throws Exception
	{
		// We'd get a org.hibernate.QueryException if Hibernate doesn't understand
		dao.findByUriQuery(new WebQuery().eqRef("otherObject.parent.name", "otherObject.parent.name"));

		// We'd get a org.hibernate.QueryException if Hibernate doesn't understand
		dao.findByUriQuery(new WebQuery().leRef("otherObject.parent.name", "otherObject.parent.name"));
	}


	@Test
	public void testEntityWrappedId() throws Exception
	{
		ParentEntity obj = new ParentEntity();
		obj.setName("Name");
		obj.setOtherObject(new ChildEntity());
		obj.getOtherObject().setName("Name");

		childDao.persist(obj.getOtherObject());
		dao.persist(obj);

		final ConstrainedResultSet<ParentEntity> results = dao.find(new WebQuery(), JPASearchStrategy.ENTITY_WRAPPED_ID);

		assertEquals(1, results.list.size());
		assertNotNull(results.uniqueResult().getId()); // should be populated
		assertNull(results.uniqueResult().getName()); // should not be populated
	}


	@Test
	public void testEqRefReturnsValue() throws Exception
	{
		ParentEntity obj = new ParentEntity();
		obj.setName("Name");
		obj.setOtherObject(new ChildEntity());
		obj.getOtherObject().setName("Name");

		childDao.persist(obj.getOtherObject());
		dao.persist(obj);

		assertEquals(1, dao.findByUriQuery(new WebQuery().eqRef("name", "otherObject.name")).getList().size());
		assertEquals(0, dao.findByUriQuery(new WebQuery().neqRef("name", "otherObject.name")).getList().size());

		// Test inversion of the above conditions
		assertEquals(1, dao.findByUriQuery(new WebQuery().decode("NOT(name NEQREF otherObject.name)")).getList().size());
		assertEquals(0, dao.findByUriQuery(new WebQuery().decode("NOT(name EQREF otherObject.name)")).getList().size());
	}


	@Test(expected = IllegalArgumentException.class)
	public void testNestedAssociatorThatIsMadeUpDoesNotWork() throws Exception
	{
		WebQuery query = new WebQuery().eq("otherObject.parent.fictionalfield.name", "Alice");

		// Nonsense field shouldn't work
		dao.findByUriQuery(query);
	}


	@Test
	public void testGetByUniqueProperty() throws Exception
	{
		// Hibernate will throw if the property doesn't work
		dao.getByUniqueProperty("otherObject.id", 1L);
	}


	@Test
	public void testGetByUniquePropertyOnCollection() throws Exception
	{
		// Hibernate will throw if the property doesn't work
		dao.getByUniqueProperty("children.id", 1L);
	}


	@Test
	public void testGetByUniquePropertyWithAliasOnCollection() throws Exception
	{
		dao.getByUniqueProperty("children[r0].id", 1L);
	}


	@Test
	public void testGetUsingPropertiesOfEmbeddedCollection() throws Exception
	{
		// Hibernate will throw if the property doesn't work
		WebQuery q = new WebQuery();
		q.eq("friends[r0].firstName", "Firstname");
		q.eq("friends[r0].lastName", "Surname");

		dao.findByUriQuery(q);
	}


	@Test
	public void testNestedAssociatorConstraintWorksInGetByUniqueProperty() throws Exception
	{
		// Hibernate will throw if the join doesn't get automatically set up
		dao.getByUniqueProperty("otherObject.parent.name", "Alice");
	}


	@Test
	public void testGetByRelationIdIsNull() throws Exception
	{
		ParentEntity obj = new ParentEntity();
		obj.setName("Name");
		dao.persist(obj);

		assertEquals(1, dao.findByUriQuery(new WebQuery().isNull("otherObject.id")).getList().size());
	}

	@Test
	public void testGetByNameStartsAndNotStarts() throws Exception
	{
		ParentEntity obj = new ParentEntity();
		obj.setName("Name");
		dao.persist(obj);


		assertEquals("starts Nam = 1", 1, dao.find(new WebQuery().decode("name STARTS Nam")).getList().size());
		assertEquals("starts Alice = 0", 0, dao.find(new WebQuery().decode("name STARTS Alice")).getList().size());

		assertEquals("not starts Alice = 1", 1, dao.find(new WebQuery().decode("name NOT STARTS Alice")).getList().size());
	}


	/**
	 * Test that the dynamic query logic sees a ManyToOne which references a column in another Entity (a property of type
	 * CollectionType)
	 *
	 * @throws IllegalArgumentException
	 * 		if the property is not correctly parsed and available to queries
	 */
	@Test
	public void testDynamicQuerySeesManyToOneRelation() throws IllegalArgumentException
	{
		// Will throw an IllegalArgumentException if the "children" field is not parsed from the entity
		dao.findByUriQuery(new WebQuery().isNull("children.id"));
	}


	@Test
	public void testByBooleanField() throws Exception
	{
		{
			ParentEntity obj1 = new ParentEntity();
			obj1.setName("Name1");
			obj1.setDeprecated(true);
			dao.persist(obj1);

			ParentEntity obj2 = new ParentEntity();
			obj2.setName("Name2");
			obj2.setDeprecated(true);
			dao.persist(obj2);
		}

		assertEquals("deprecated=true matches 2 rows (with dao.find)",
		             2,
		             dao.find(new WebQuery().eq("deprecated", true)).getList().size());

		// Test the same query with only COUNT
		assertEquals("deprecated=true matches 2 rows (with dao.count)",
		             2,
		             dao.count(new WebQuery().eq("deprecated", true)));


		// deprecated=false should match nothing
		final ConstrainedResultSet<ParentEntity> resultset = dao.find(new WebQuery().eq("deprecated", false));
		assertEquals("deprecated=false matches nothing (with dao.find)",
		             0,
		             resultset.getList().size());
	}


	@Test
	public void testOrderAsc() throws Exception
	{
		ParentEntity obj1 = new ParentEntity();
		obj1.setName("Name1");
		dao.persist(obj1);

		ParentEntity obj2 = new ParentEntity();
		obj2.setName("Name2");
		dao.persist(obj2);

		assertEquals("regular find",
		             getIds(Arrays.asList(obj1, obj2)),
		             getIds(dao.find(new WebQuery().orderAsc("id")).getList()));

		assertEquals("findIds", getIds(Arrays.asList(obj1, obj2)), dao.findIds(new WebQuery().orderAsc("id")).getList());
	}


	@Test
	public void testGetIdList() throws Exception
	{
		ParentEntity obj1 = new ParentEntity();
		obj1.setName("Name1");
		dao.persist(obj1);

		ParentEntity obj2 = new ParentEntity();
		obj2.setName("Name2");
		dao.persist(obj2);

		assertEquals(getIds(Arrays.asList(obj1, obj2)), dao.getIdList(new WebQuery().orderAsc("id").fetch("id")));
	}

	@Test
	@Transactional
	public void testReadOnlyTxAnnotationNestedInReadWriteTx()
	{
		assertNotNull(txutils.get());
		ParentEntity obj1 = new ParentEntity();
		obj1.setName("Name1");
		dao.persist(obj1);

		ParentEntity obj2 = new ParentEntity();
		obj2.setName("Name2");
		dao.persist(obj2);

		assertEquals(getIds(Arrays.asList(obj1, obj2)), dao.getIdList(new WebQuery().orderAsc("id").fetch("id")));
	}


	@Test
	public void testLogSQL() throws Exception
	{
		// For this unit test, disable always compute size
		JPASearchExecutor.ALWAYS_COMPUTE_SIZE = false;

		ParentEntity obj1 = new ParentEntity();
		obj1.setName("Name1");
		dao.persist(obj1);

		ParentEntity obj2 = new ParentEntity();
		obj2.setName("Name2");
		dao.persist(obj2);

		final ConstrainedResultSet<ParentEntity> resultset = dao.findByUriQuery(new WebQuery()
				                                                                        .orderAsc("id")
				                                                                        .limit(1000)
				                                                                        .logSQL(true));

		assertEquals(getIds(Arrays.asList(obj1, obj2)), getIds(resultset.getList())); // must have the right answer
		assertNotNull(resultset.getSql());

		System.out.println(resultset.getSql());
		assertEquals("Number of SQL statements executed", 2, resultset.getSql().size());
	}


	@Test
	public void testOrderDesc() throws Exception
	{
		ParentEntity obj1 = new ParentEntity();
		obj1.setName("Name1");
		dao.persist(obj1);

		ParentEntity obj2 = new ParentEntity();
		obj2.setName("Name2");
		dao.persist(obj2);

		assertEquals(getIds(obj2, obj1), getIds(dao.findByUriQuery(new WebQuery().orderDesc("id")).getList()));
	}


	@Test
	public void testCustomProjection()
	{
		ParentEntity obj1 = new ParentEntity();
		obj1.setName("Name1");
		dao.persist(obj1);

		ParentEntity obj2 = new ParentEntity();
		obj2.setName("Name2");
		dao.persist(obj2);

		final Object[] a = (Object[]) (Object) dao.find(new WebQuery().eq("deprecated", false).fetch("id").orderAsc("name"),
		                                                JPASearchStrategy.CUSTOM_PROJECTION).list.get(0);

		final Object[] b = (Object[]) (Object) dao.find(new WebQuery().eq("deprecated", false).fetch("id,name").orderAsc("name"),
		                                                JPASearchStrategy.CUSTOM_PROJECTION).list.get(0);

		final Object[] c = (Object[]) (Object) dao.find(new WebQuery().eq("deprecated", false).fetch("id,name,id").orderAsc("name").orderDesc("id"),
		                                                JPASearchStrategy.CUSTOM_PROJECTION).list.get(0);

		// will not use distinct
		final Object[] d = (Object[]) (Object) dao.find(new WebQuery().eq("deprecated", false).fetch("id,deprecated").orderAsc("name"),
		                                                JPASearchStrategy.CUSTOM_PROJECTION).list.get(0);

		// will use distinct
		final Object[] e = (Object[]) (Object) dao.find(new WebQuery().eq("deprecated", false).fetch("id,deprecated,deprecated").orderAsc("name"),
		                                                JPASearchStrategy.CUSTOM_PROJECTION).list.get(0);


		// will use distinct
		final Object[] f = (Object[]) (Object) dao.find(new WebQuery().eq("deprecated", false).fetch("name").orderAsc("name"),
		                                                JPASearchStrategy.CUSTOM_PROJECTION).list.get(0);

		assertEquals("a", new Object[] {1L}, a);
		assertEquals("b", new Object[] {1L, "Name1"}, b);
		assertEquals("c: uses distinct, but order reuses existing select", new Object[] {1L, "Name1", 1L}, c);
		assertEquals("d", new Object[] {1L, false}, d); // shouldn't need to use distinct
		assertEquals("e: distinct must add name to select", new Object[] {1L, false, false, "Name1"}, e); // need to use DISTINCT (unfortunately)
		assertEquals("f: distinct required but no select modify", new Object[] {"Name1"}, f);
	}


	@Test
	public void testConstraintWorksWithComputeSize() throws Exception
	{
		assertNotNull(dao.findByUriQuery(new WebQuery().eq("id", "1").computeSize(true)).total);
	}


	@Test
	public void testComputeSizeWorks() throws Exception
	{
		assertNotNull(dao.findByUriQuery(new WebQuery().computeSize(true)).total);
	}


	/**
	 * Tests that computing size while applying ordering and limiting to the resultset still works
	 *
	 * @throws Exception
	 */
	@Test
	public void testComputeSizeWithOrder() throws Exception
	{
		{
			ParentEntity obj1 = new ParentEntity();
			obj1.setName("Name1");
			dao.persist(obj1);

			ParentEntity obj2 = new ParentEntity();
			obj2.setName("Name2");
			dao.persist(obj2);

			ParentEntity obj3 = new ParentEntity();
			obj3.setName("Name3");
			dao.persist(obj3);
		}


		final WebQuery query = new WebQuery().orderDesc("name").limit(2);

		// First, test that the correct results are returned with computeSize=false
		{
			ConstrainedResultSet<ParentEntity> results = dao.findByUriQuery(query);

			// Must honour limit
			assertEquals("without computeSize; must only have returned 2 results", 2, results.getList().size());
			assertEquals("without computeSize; must have returned right 2 entities",
			             Arrays.asList("Name3", "Name2"),
			             results.getList().stream().map(e -> e.getName()).collect(Collectors.toList()));
		}

		// Next, test that we get the same results with computeSize=true (ensures the order is still present after size is computed)
		query.computeSize(true);
		{
			ConstrainedResultSet<ParentEntity> results = dao.findByUriQuery(query);

			// Must have correct total size
			assertEquals("with computeSize; must have computed correct total size", Long.valueOf(3), results.getTotal());

			// Must honour limit
			assertEquals("with computeSize; must only have returned 2 results", 2, results.getList().size());
			assertEquals("with computeSize; must have returned right 2 entities",
			             Arrays.asList("Name3", "Name2"),
			             results.getList().stream().map(e -> e.getName()).collect(Collectors.toList()));
		}
	}


	/**
	 * Test that it is possible to search based on the size of a collection
	 *
	 * @throws Exception
	 * 		on error
	 */
	@Test
	public void testConstrainSize() throws Exception
	{
		// Set up 2 MyObject instances, one of which has 2 child instances
		{
			ParentEntity obj1 = new ParentEntity();
			obj1.setName("Name1");
			dao.persist(obj1);

			ParentEntity obj2 = new ParentEntity();
			obj2.setName("Name2");
			dao.persist(obj2);

			ChildEntity child1a = new ChildEntity();
			child1a.setParent(obj1);
			child1a.setName("a");
			childDao.persist(child1a);

			ChildEntity child1b = new ChildEntity();
			child1b.setParent(obj1);
			child1b.setName("b");
			childDao.persist(child1b);
		}

		// Search for entries without constraints
		{
			ConstrainedResultSet<ParentEntity> results = dao.findByUriQuery(new WebQuery());

			assertEquals(2, results.getList().size());
		}

		// Search for entries with at least 2 children
		{
			ConstrainedResultSet<ParentEntity> results = dao.findByUriQuery(new WebQuery().ge("children:size", 2));

			assertEquals(1, results.getList().size());
			assertEquals("Name1", results.getList().get(0).getName());
		}

		// Search for entries with no children
		{
			ConstrainedResultSet<ParentEntity> results = dao.findByUriQuery(new WebQuery().eq("children:size", 0));

			assertEquals(1, results.getList().size());
			assertEquals("Name2", results.getList().get(0).getName());
		}
	}


	private List<Long> getIds(ParentEntity... objs)
	{
		return getIds(Arrays.asList(objs));
	}


	private List<Long> getIds(List<ParentEntity> objs)
	{
		List<Long> list = new ArrayList<>();
		for (ParentEntity obj : objs)
			list.add(obj.getId());

		return list;
	}
}
