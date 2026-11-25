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
import com.peterphi.std.guice.testing.GuiceTest;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@GuiceTest
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
	@BeforeEach
	public void clearDatabaseBeforeTest()
	{
		JPASearchExecutor.ALWAYS_COMPUTE_SIZE = true;

		for (ChildEntity obj : childDao.getAll())
			childDao.delete(obj);

		for (ParentEntity obj : dao.getAll())
			dao.delete(obj);

		assertEquals(0, dao.getAll().size());
	}


	@AfterEach
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


	@Test
	public void testCannotConstrainPrivateAnnotatedField()
	{
		assertThrows(PrivatePropertyUseRejected.class, () -> dao.find(new WebQuery().eq("somePrivateString", "secret")));
	}


	@Test
	public void testCannotProjectPrivateAnnotatedField()
	{
		assertThrows(PrivatePropertyUseRejected.class, () -> dao.find(new WebQuery().fetch("id,somePrivateString")));
	}


	@Test
	public void testCannotSortByPrivateAnnotatedField()
	{
		assertThrows(PrivatePropertyUseRejected.class, () -> dao.find(new WebQuery().orderAsc("somePrivateString")));
	}


	@Test
	public void testNormalPrivilegedCallCannotConstrainPrivateAnnotatedField()
	{
		assertThrows(PrivatePropertyUseRejected.class, () -> dao.find(new WebQuery().eq("somePrivateString", "secret"), JPASearchStrategy.AUTO, null, QueryPrivilegeData.NORMAL));
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

			assertEquals(2, results.getList().size(), "Expecting 2 rows");
			assertTrue(results.getList().get(0).length >= 3, "Expecting at least 3 columns");

			// Assert that the expected value is output for the first row
			assertEquals(id1, results.getList().get(0)[0], "row1 id");
			assertEquals("Name1", results.getList().get(0)[1], "row1 name");
			assertEquals("CName1", results.getList().get(0)[2], "row1 otherObject.name");

			// Assert that the expected value is output for the second row
			assertEquals(id2, results.getList().get(1)[0], "row2 id");
			assertEquals("Name2", results.getList().get(1)[1], "row2 name");
			assertEquals("CName2", results.getList().get(1)[2], "row2 otherObject.name");
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

		assertEquals(2, results.getList().size(), "Expecting 2 rows");
		assertTrue(results.getList().get(0).length >= 1, "Expecting at least 1 column");


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

		assertEquals(2, results.getList().size(), "Expecting 2 rows");
		assertTrue(results.getList().get(0).length >= 1, "Expecting at least 1 column");

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


	@Test
	public void testNestedAssociatorThatIsMadeUpDoesNotWork() throws Exception
	{
		WebQuery query = new WebQuery().eq("otherObject.parent.fictionalfield.name", "Alice");

		// Nonsense field shouldn't work
		assertThrows(IllegalArgumentException.class, () -> dao.findByUriQuery(query));
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


		assertEquals(1, dao.find(new WebQuery().decode("name STARTS Nam")).getList().size(), "starts Nam = 1");
		assertEquals(0, dao.find(new WebQuery().decode("name STARTS Alice")).getList().size(), "starts Alice = 0");

		assertEquals(1, dao.find(new WebQuery().decode("name NOT STARTS Alice")).getList().size(), "not starts Alice = 1");
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

		assertEquals(2,
		             dao.find(new WebQuery().eq("deprecated", true)).getList().size(),
		             "deprecated=true matches 2 rows (with dao.find)");

		// Test the same query with only COUNT
		assertEquals(2,
		             dao.count(new WebQuery().eq("deprecated", true)),
		             "deprecated=true matches 2 rows (with dao.count)");


		// deprecated=false should match nothing
		final ConstrainedResultSet<ParentEntity> resultset = dao.find(new WebQuery().eq("deprecated", false));
		assertEquals(0,
		             resultset.getList().size(),
		             "deprecated=false matches nothing (with dao.find)");
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

		assertEquals(getIds(Arrays.asList(obj1, obj2)),
		             getIds(dao.find(new WebQuery().orderAsc("id")).getList()),
		             "regular find");

		assertEquals(getIds(Arrays.asList(obj1, obj2)), dao.findIds(new WebQuery().orderAsc("id")).getList(), "findIds");
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
		assertEquals(2, resultset.getSql().size(), "Number of SQL statements executed");
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

		assertArrayEquals(new Object[] {1L}, a, "a");
		assertArrayEquals(new Object[] {1L, "Name1"}, b, "b");
		assertArrayEquals(new Object[] {1L, "Name1", 1L}, c, "c: uses distinct, but order reuses existing select");
		assertArrayEquals(new Object[] {1L, false}, d, "d"); // shouldn't need to use distinct
		assertArrayEquals(new Object[] {1L, false, false, "Name1"}, e, "e: distinct must add name to select"); // need to use DISTINCT (unfortunately)
		assertArrayEquals(new Object[] {"Name1"}, f, "f: distinct required but no select modify");
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
			assertEquals(2, results.getList().size(), "without computeSize; must only have returned 2 results");
			assertEquals(Arrays.asList("Name3", "Name2"),
			             results.getList().stream().map(e -> e.getName()).collect(Collectors.toList()),
			             "without computeSize; must have returned right 2 entities");
		}

		// Next, test that we get the same results with computeSize=true (ensures the order is still present after size is computed)
		query.computeSize(true);
		{
			ConstrainedResultSet<ParentEntity> results = dao.findByUriQuery(query);

			// Must have correct total size
			assertEquals(Long.valueOf(3), results.getTotal(), "with computeSize; must have computed correct total size");

			// Must honour limit
			assertEquals(2, results.getList().size(), "with computeSize; must only have returned 2 results");
			assertEquals(Arrays.asList("Name3", "Name2"),
			             results.getList().stream().map(e -> e.getName()).collect(Collectors.toList()),
			             "with computeSize; must have returned right 2 entities");
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
