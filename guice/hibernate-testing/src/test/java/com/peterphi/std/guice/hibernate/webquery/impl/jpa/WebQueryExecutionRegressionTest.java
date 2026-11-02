package com.peterphi.std.guice.hibernate.webquery.impl.jpa;

import com.peterphi.std.guice.hibernate.module.logging.HibernateObservingInterceptor;
import com.peterphi.std.guice.common.jackson.JacksonFactory;
import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.guice.hibernate.usertype.TimecodeUserType;
import com.peterphi.std.guice.hibernate.usertype.SampleCountUserType;
import com.peterphi.std.types.Timecode;
import com.peterphi.std.types.SampleCount;
import com.peterphi.std.types.Timebase;
import com.peterphi.std.guice.hibernate.webquery.ConstrainedResultSet;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntityFactory;
import com.peterphi.std.guice.hibernate.webquery.impl.WQTypeHelper;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Exercises SQL page boundaries against both supported test databases. */
@RunWith(Parameterized.class)
public class WebQueryExecutionRegressionTest
{
	@Parameterized.Parameters(name = "{0}")
	public static List<String> databases()
	{
		return List.of("hsqldb", "postgresql");
	}

	private static EmbeddedPostgres postgres;
	private final String database;
	private SessionFactory factory;
	private QEntityFactory schemas;
	private JPASearchExecutor executor;

	public WebQueryExecutionRegressionTest(String database)
	{
		this.database = database;
	}

	@BeforeClass
	public static void startPostgres() throws Exception
	{
		postgres = EmbeddedPostgres.builder().start();
	}

	@AfterClass
	public static void stopPostgres() throws Exception
	{
		if (postgres != null)
			postgres.close();
	}

	@Before
	public void setup()
	{
		Configuration config = new Configuration();
		if (database.equals("postgresql"))
			config.getProperties().put("hibernate.connection.datasource", postgres.getPostgresDatabase());
		else
		{
			config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
			config.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:execution_" + UUID.randomUUID());
			config.setProperty("hibernate.connection.username", "sa");
		}
		config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
		config.setProperty("hibernate.criteria.copy_tree", "false");
		config.registerTypeOverride(TimecodeUserType.INSTANCE, new String[]{Timecode.class.getName()});
		config.registerTypeOverride(SampleCountUserType.INSTANCE, new String[]{SampleCount.class.getName()});
		config.setProperty("hibernate.current_session_context_class", "thread");
		config.addAnnotatedClass(Parent.class).addAnnotatedClass(SpecialParent.class).addAnnotatedClass(SiblingParent.class);
		config.addAnnotatedClass(Child.class).addAnnotatedClass(SpecialChild.class).addAnnotatedClass(SiblingChild.class);
		config.addAnnotatedClass(CompositeParent.class).addAnnotatedClass(CompositeChild.class);
		config.addAnnotatedClass(GenericParent.class).addAnnotatedClass(Leaf.class);
		config.addAnnotatedClass(GenericIdentifierOwner.class).addAnnotatedClass(GenericIdentifierChild.class);
		HibernateObservingInterceptor observer = new HibernateObservingInterceptor();
		config.setStatementInspector(observer);
		factory = config.buildSessionFactory();
		schemas = new QEntityFactory(factory);
		executor = new JPASearchExecutor();
		executor.sessionFactory = factory;
		executor.hibernateObserver = observer;
		inTransaction(session -> {
			addParent(session, 1L, "equal", 1.25f, 5, 1, 100);
			addParent(session, 2L, "equal", -2.5f, 4, 3, 0);
			addParent(session, 3L, "other", 0f, 2, 9, -100);
			return null;
		});
	}

	@After
	public void tearDown()
	{
		if (factory != null)
			factory.close();
	}

	@Test
	public void countsAndPagesReferToUniqueEntities()
	{
		ConstrainedResultSet<Parent> result = find(new WebQuery().eq("children.flag", true)
				.orderAsc("id").limit(2).computeSize(true));
		assertEquals(Long.valueOf(3), result.getTotal());
		assertEquals(List.of(1L, 2L), ids(result.getList()));
		assertEquals(List.of(3L), ids(find(new WebQuery().eq("children.flag", true)
				.orderAsc("id").offset(2).limit(2)).getList()));
		assertEquals(List.of(1L, 2L, 3L), findIds(new WebQuery().eq("children.flag", true).orderAsc("id")));
	}

	@Test
	public void scalarSortsHaveStablePageBoundaries()
	{
		WebQuery first = new WebQuery().eq("children.flag", true).orderAsc("name").limit(1);
		WebQuery second = new WebQuery().eq("children.flag", true).orderAsc("name").offset(1).limit(1);
		assertEquals(List.of(1L), ids(find(first).getList()));
		assertEquals(List.of(2L), ids(find(second).getList()));
	}

	@Test
	public void ordinaryEntityPagesHaveStableScalarTies()
	{
		assertEquals(List.of(1L), ids(find(new WebQuery().dbfetch("none").orderAsc("name").limit(1)).getList()));
		assertEquals(List.of(2L), ids(find(new WebQuery().dbfetch("none").orderAsc("name").offset(1).limit(1)).getList()));
		assertEquals(List.of(3L), ids(find(new WebQuery().dbfetch("none").orderAsc("name").offset(2).limit(1)).getList()));
	}

	@Test
	public void collectionSortUsesMatchingMinimumOrMaximumAndPreservesPageOrder()
	{
		assertEquals(List.of(1L, 3L, 2L), ids(find(new WebQuery().eq("children.flag", true)
				.orderAsc("children.rank")).getList()));
		assertEquals(List.of(1L, 3L), ids(find(new WebQuery().eq("children.flag", true)
				.orderAsc("children.rank").limit(2)).getList()));
		assertEquals(List.of(2L), ids(find(new WebQuery().eq("children.flag", true)
				.orderAsc("children.rank").offset(2).limit(2)).getList()));
		assertEquals(List.of(3L, 1L), ids(find(new WebQuery().eq("children.flag", true)
				.orderDesc("children.rank").limit(2)).getList()));
	}

	@Test
	public void booleanCollectionSortWorksOnBothDatabases()
	{
		assertEquals(List.of(1L, 2L), ids(find(new WebQuery().orderAsc("children.flag").limit(2)).getList()));
	}

	@Test
	public void independentCollectionAliasesDoNotMultiplyIdsOrTotals()
	{
		ConstrainedResultSet<Parent> result = find(new WebQuery().eq("children[a].flag", true)
				.eq("children[b].flag", false).orderAsc("children[a].rank").limit(2).computeSize(true));
		assertEquals(Long.valueOf(3), result.getTotal());
		assertEquals(List.of(1L, 3L), ids(result.getList()));
	}

	@Test
	public void floatValuesCanBeConstrained()
	{
		assertEquals(List.of(1L), ids(find(new WebQuery().eq("ratio", "1.25")).getList()));
		assertEquals(List.of(2L), ids(find(new WebQuery().lt("ratio", "0")).getList()));
		assertEquals(-Float.MAX_VALUE, WQTypeHelper.parse(Float.class, "min"));
		assertEquals(Float.MAX_VALUE, WQTypeHelper.parse(float.class, "max"));
		assertEquals(-Double.MAX_VALUE, WQTypeHelper.parse(Double.class, "min"));
	}

	@Test
	public void builderReuseDiscardsConstraintsJoinsOrderingAndPagination()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("children.flag", true).orderDesc("id").limit(1));
			assertEquals(List.of(3L), builder.selectIDs());
			builder.forWebQuery(new WebQuery().eq("name", "equal").orderAsc("id"));
			assertEquals(List.of(1L, 2L), builder.selectIDs());
			builder.forIDs(null, List.of(1L, 2L));
			assertEquals(2, builder.selectEntity().size());
			return null;
		});
	}

	@Test
	public void builderReuseRestoresOriginalEntityAfterSubclassQuery()
	{
		inTransaction(session -> {
			SpecialParent special = new SpecialParent();
			special.id = 4L;
			special.name = "special";
			session.persist(special);
			session.flush();
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().subclass("special"));
			assertEquals(List.of(4L), builder.selectIDs());
			builder.forWebQuery(new WebQuery().eq("id", 1L));
			assertEquals(List.of(1L), builder.selectIDs());
			return null;
		});
	}

	@Test
	public void identifierPagesIntersectLaterConstraintsAcrossTerminalResults()
	{
		inTransaction(session -> {
			for (List<Long> page : List.of(List.of(1L, 3L), List.of(3L)))
			{
				JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
				builder.forIDs(new WebQuery().dbfetch("none"), page);
				builder.addConstraints(session.getCriteriaBuilder().equal(builder.getProperty("name"), "equal"));
				List<Long> expected = page.contains(1L) ? List.of(1L) : List.of();
				assertEquals(expected, builder.selectIDs());
				assertEquals(Long.valueOf(expected.size()), builder.selectCount());
				assertEquals(expected, ids(builder.selectEntity()));
				assertEquals(expected, builder.selectCustomProjection(false, "id", "name").stream().map(row -> row[0]).toList());
			}
			return null;
		});
	}

	@Test
	public void identifierPageConstraintsStayIndependentInPreviouslyCreatedQueries()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forIDs(null, List.of(1L, 3L));
			var originalIds = builder.createSelectIDs();
			var originalCount = builder.createSelectCount();
			builder.addConstraints(session.getCriteriaBuilder().equal(builder.getProperty("name"), "equal"));
			assertEquals(List.of(1L), builder.selectIDs());
			assertEquals(List.of(1L, 3L), originalIds.getResultList());
			assertEquals(Long.valueOf(2), originalCount.getSingleResult());
			return null;
		});
	}

	@Test
	public void customCallbackConstraintsIntersectIdentifierPagesWithoutMutatingTheBuilder()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forIDs(null, List.of(1L, 3L));
			List<Parent> values = builder.selectCustom((cb, cq, root, internal) -> {
				internal.addConstraints(cb.equal(root.get("name"), "equal"));
				cq.select(root);
			});
			assertEquals(List.of(1L), ids(values));
			assertEquals(List.of(1L, 3L), builder.selectIDs());
			assertEquals(Long.valueOf(2), builder.selectCount());
			return null;
		});
	}

	@Test
	public void projectionDistinctnessRemainsIndependentOfEntityIdDeduplication()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("children.flag", true));
			assertEquals(2, builder.selectCustomProjection(true, "name").size());
			builder.forWebQuery(new WebQuery().eq("children.flag", true));
			assertEquals(6, builder.selectCustomProjection(false, "name").size());
			return null;
		});
	}

	@Test
	public void collectionSizeProjectionAndSortingUseIntegerValues()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().orderAsc("id"));
			List<Object[]> rows = builder.selectCustomProjection(false, "id", "children:size");
			assertEquals(3, rows.size());
			assertEquals(3, rows.getFirst()[1]);
			return null;
		});
		assertEquals(List.of(1L, 2L), ids(find(new WebQuery().eq("children.flag", true)
				.orderAsc("children:size").limit(2)).getList()));
		assertEquals(List.of(3L), ids(find(new WebQuery().eq("children.flag", true)
				.orderAsc("children:size").offset(2).limit(2)).getList()));
	}

	@Test
	public void genericEnumCollectionsAreParsedFromResolvedPropertyTypes()
	{
		inTransaction(session -> {
			JPAQueryBuilder<GenericParent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(GenericParent.class), false);
			GenericParent value = new GenericParent();
			value.id = 1L;
			value.state = State.READY;
			session.persist(value);
			session.flush();
			builder.forWebQuery(new WebQuery().in("state", "READY", "DONE"));
			assertEquals(List.of(1L), builder.selectIDs());
			return null;
		});
	}

	@Test
	public void terminalOperationsDoNotShareProjectionGroupingOrFetchState()
	{
		for (boolean fetch : List.of(false, true))
			for (String ordering : List.of("name", "children.rank"))
				for (String first : List.of("entity", "ids", "count", "projection"))
					for (String second : List.of("entity", "ids", "count", "projection"))
						inTransaction(session -> {
							JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
							WebQuery query = new WebQuery().eq("children.flag", true).orderAsc(ordering);
							if (!fetch)
								query.dbfetch("none");
							builder.forWebQuery(query);
							assertTerminalResult(builder, first);
							assertTerminalResult(builder, second);
							return null;
						});
	}

	@Test
	public void filteredEntityQueriesFetchCompleteCollections()
	{
		ConstrainedResultSet<Parent> result = find(new WebQuery().eq("children.flag", true));
		assertEquals(3, result.getList().size());
		for (Parent parent : result.getList())
		{
			assertTrue(org.hibernate.Hibernate.isInitialized(parent.children));
			assertEquals(3, parent.children.size());
		}
	}

	@Test
	public void customFetchAfterExistingFilterJoinUsesDistinctAliases()
	{
		inTransaction(session -> {
			for (var joinType : List.of(jakarta.persistence.criteria.JoinType.LEFT, jakarta.persistence.criteria.JoinType.INNER))
			{
				JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
				builder.forWebQuery(new WebQuery().eq("children.flag", true));
				List<Parent> values = builder.selectCustom((cb, cq, root, internal) -> {
					root.fetch("children", joinType);
					cq.select(root).distinct(true);
				});
				assertEquals(3, values.size());
				for (Parent parent : values)
					assertEquals(3, parent.children.size());
				session.clear();
			}
			return null;
		});
	}

	@Test
	public void snapshotAliasNamespacePreservesSelectionAliases()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("children.flag", true));
			builder.getProperty("entity").alias("result");
			List<Parent> values = builder.selectCustom((cb, cq, root, internal) -> {
				assertEquals("result", root.getAlias());
				root.fetch("children", jakarta.persistence.criteria.JoinType.LEFT);
				cq.select(root).distinct(true);
				assertEquals("result", cq.getSelection().getAlias());
			});
			assertEquals(3, values.size());
			for (Parent value : values)
				assertEquals(3, value.children.size());
			return null;
		});
	}

	@Test
	public void previouslyCreatedQueriesRetainIndependentShapesAndBindings()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("children.flag", true).orderAsc("name"));
			var ids = builder.createSelectIDs();
			var count = builder.createSelectCount();
			var entities = builder.createSelectEntity();
			var projection = builder.createSelectCustomProjection(false, new String[]{"name"});
			assertEquals(6, projection.getResultList().size());
			assertEquals(List.of(1L, 2L, 3L), ids(entities.getResultList()));
			assertEquals(Long.valueOf(3), count.getSingleResult());
			assertEquals(List.of(1L, 2L, 3L), ids.getResultList());
			return null;
		});
	}

	@Test
	public void externalParameterObjectsRemainUsableWithIndependentQueryBindings()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().dbfetch("none"));
			var name = session.getCriteriaBuilder().parameter(String.class);
			builder.addConstraints(session.getCriteriaBuilder().equal(builder.getProperty("name"), name));
			var ids = builder.createSelectIDs();
			var count = builder.createSelectCount();
			ids.setParameter(name, "equal");
			count.setParameter(name, "other");
			assertEquals(List.of(1L, 2L), ids.getResultList());
			assertEquals(Long.valueOf(1), count.getSingleResult());
			return null;
		});
	}

	@Test
	public void customInnerFetchesAndConstraintsStayInTheirOwnQuery()
	{
		inTransaction(session -> {
			Parent empty = new Parent();
			empty.id = 4L;
			empty.name = "equal";
			session.persist(empty);
			session.flush();
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("name", "equal"));
			List<Parent> fetched = builder.selectCustom((cb, cq, root, internal) -> {
				root.fetch("children", jakarta.persistence.criteria.JoinType.INNER);
				cq.select(root).distinct(true);
			});
			assertEquals(2, fetched.size());
			assertEquals(Long.valueOf(3), builder.selectCount());
			assertEquals(List.of(1L, 2L, 4L), builder.selectIDs());
			assertEquals(3, builder.selectCustomProjection(false, "name").size());
			return null;
		});
	}

	@Test
	public void projectionDistinctSettingDoesNotLeakToLaterProjection()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("children.flag", true));
			assertEquals(2, builder.selectCustomProjection(true, "name").size());
			assertEquals(6, builder.selectCustomProjection(false, "name").size());
			return null;
		});
	}

	@Test
	public void collectionOrderingUsesMappedSqlScalarsRatherThanJavaComparable()
	{
		assertEquals(List.of(1L, 2L), ids(find(new WebQuery().orderAsc("children.timecode").limit(2)).getList()));
		assertEquals(List.of(3L, 2L), ids(find(new WebQuery().orderDesc("children.samples").limit(2)).getList()));
		assertEquals(List.of(1L, 2L), ids(find(new WebQuery().orderAsc("children.uuid").limit(2)).getList()));
		assertEquals(List.of(2L, 1L), ids(find(new WebQuery().orderAsc("children.state").limit(2)).getList()));
	}

	@Test
	public void mixedCollectionOrderingKeepsNullPagesConsistent()
	{
		inTransaction(session -> {
			Parent empty = new Parent();
			empty.id = 4L;
			empty.name = "empty";
			session.persist(empty);
			return null;
		});
		List<Long> all = ids(find(new WebQuery().orderAsc("children.rank").orderAsc("children:size")
				.orderDesc("children.flag")).getList());
		assertEquals(4, all.size());
		for (int offset = 0; offset <= 4; offset++)
		{
			ConstrainedResultSet<Parent> page = find(new WebQuery().orderAsc("children.rank").orderAsc("children:size")
					.orderDesc("children.flag").offset(offset).limit(1).computeSize(true));
			assertEquals(Long.valueOf(4), page.getTotal());
			assertEquals(all.subList(Math.min(offset, 4), Math.min(offset + 1, 4)), ids(page.getList()));
		}
	}

	private void assertTerminalResult(JPAQueryBuilder<Parent, Long> builder, String terminal)
	{
		switch (terminal)
		{
			case "entity" -> assertEquals(3, builder.selectEntity().size());
			case "ids" -> assertEquals(3, builder.selectIDs().size());
			case "count" -> assertEquals(Long.valueOf(3), builder.selectCount());
			case "projection" -> assertEquals(6, builder.selectCustomProjection(false, "name").size());
			default -> throw new IllegalArgumentException(terminal);
		}
	}

	@Test
	public void genericIdentifierListsBindAsElementsRatherThanOneSerializableValue()
	{
		inTransaction(session -> {
			for (long id = 1; id <= 2; id++)
			{
				GenericIdentifierOwner owner = new GenericIdentifierOwner();
				owner.setId(id);
				session.persist(owner);
				for (int childIndex = 0; childIndex < 2; childIndex++)
				{
					GenericIdentifierChild child = new GenericIdentifierChild();
					child.id = id * 10 + childIndex;
					child.owner = owner;
					child.flag = true;
					session.persist(child);
				}
			}
			session.flush();
			JPAQueryBuilder<GenericIdentifierOwner, Long> builder = new JPAQueryBuilder<>(session, schemas.get(GenericIdentifierOwner.class), false);
			builder.forWebQuery(new WebQuery().in("id", 1L, 2L).orderAsc("id"));
			assertEquals(List.of(1L, 2L), builder.selectIDs());
			ConstrainedResultSet<GenericIdentifierOwner> page = executor.find(schemas.get(GenericIdentifierOwner.class),
					new WebQuery().eq("children.flag", true).limit(1), JPASearchStrategy.AUTO, null, false);
			assertEquals(List.of(1L), page.getList().stream().map(GenericIdentifierOwner::getId).toList());
			return null;
		});
	}

	@Test
	public void emptyJsonFetchCollectionsSuppressDefaultEagerFetching() throws Exception
	{
		assertTrue(org.hibernate.Hibernate.isInitialized(find(new WebQuery().eq("id", 1L)).getList().getFirst().children));
		WebQuery query = JacksonFactory.newObjectMapper().readValue("{\"dbfetch\":[],\"expand\":[]}", WebQuery.class);
		assertFalse(org.hibernate.Hibernate.isInitialized(find(query).getList().getFirst().children));
	}

	@Test
	public void compositeIdentifiersAreDeduplicatedBeforePagination()
	{
		inTransaction(session -> {
			for (long value = 1; value <= 2; value++)
			{
				CompositeParent parent = new CompositeParent();
				parent.id = new CompositeId("parent", value);
				parent.name = "same";
				session.persist(parent);
				for (int c = 0; c < 2; c++)
				{
					CompositeChild child = new CompositeChild();
					child.id = value * 10 + c;
					child.parent = parent;
					child.flag = true;
					session.persist(child);
				}
			}
			session.flush();
			ConstrainedResultSet<CompositeParent> result = executor.find(schemas.get(CompositeParent.class),
					new WebQuery().eq("children.flag", true).orderAsc("name").limit(2).computeSize(true),
					JPASearchStrategy.AUTO, null, false);
			assertEquals(Long.valueOf(2), result.getTotal());
			assertEquals(List.of(new CompositeId("parent", 1), new CompositeId("parent", 2)),
					result.getList().stream().map(row -> row.id).toList());
			return null;
		});
	}

	@Test
	public void parallelCollectionFetchesDoNotDuplicateBag()
	{
		for (WebQuery query : List.of(new WebQuery().dbfetch("children,tags"),
		                             new WebQuery().dbfetch("children,tags").eq("children.flag", true),
		                             new WebQuery().dbfetch("children,tags").limit(2)))
		{
			for (Parent row : find(query).getList())
			{
				assertCompleteChildren(row);
				assertEquals(java.util.Set.of("a", "b"), row.tags);
			}
		}
	}

	@Test
	public void nestedCollectionFetchesDoNotDuplicateBag()
	{
		for (WebQuery query : List.of(new WebQuery().dbfetch("children.leaves"),
		                             new WebQuery().dbfetch("children.leaves").eq("children.flag", true),
		                             new WebQuery().dbfetch("children.leaves").limit(2)))
		{
			for (Parent row : find(query).getList())
			{
				assertCompleteChildren(row);
				for (Child child : row.children)
					assertEquals(child.id % 10 == 0 ? java.util.Set.of() : java.util.Set.of(child.id * 10 + 1, child.id * 10 + 2),
					             child.leaves.stream().map(leaf -> leaf.id).collect(java.util.stream.Collectors.toSet()));
			}
		}
	}

	@Test
	public void separateFetchesPreserveRealBagDuplicates()
	{
		for (Parent row : find(new WebQuery().dbfetch("children,labels")).getList())
		{
			assertCompleteChildren(row);
			assertEquals(List.of("other", "same", "same"), row.labels.stream().sorted().toList());
		}
	}

	@Test
	public void isolatedFetchPlanSupportsSingleResultsStreamsAndScrolls()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().eq("id", 1L).dbfetch("children,tags"));
			var query = builder.createSelectEntity();
			assertCompleteChildren(query.getSingleResult());
			session.clear();
			try (var stream = query.getResultStream())
			{
				Parent value = stream.findFirst().orElseThrow();
				assertCompleteChildren(value);
				assertEquals(java.util.Set.of("a", "b"), value.tags);
			}
			session.clear();
			try (var scroll = query.scroll())
			{
				assertTrue(scroll.next());
				assertCompleteChildren(scroll.get());
				assertEquals(java.util.Set.of("a", "b"), scroll.get().tags);
			}
			return null;
		});
	}

	@Test
	public void publicFetchPreparationIsDeferredAndCustomCallbacksApplyImmediately()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().dbfetch("children"));
			builder.applyFetches();
			builder.applyFetches();
			assertEquals(Long.valueOf(3), builder.selectCount());
			assertEquals(List.of(1L, 2L, 3L), builder.selectIDs());
			assertEquals(3, builder.selectCustomProjection(false, "name").size());
			List<Parent> values = builder.selectCustom((cb, cq, root, internal) -> {
				internal.applyFetches();
				assertFalse(root.getFetches().isEmpty());
				cq.select(root);
			});
			for (Parent value : values)
				assertCompleteChildren(value);
			return null;
		});
	}

	@Test
	public void directCriteriaCollectionJoinsProduceUniqueIdPages()
	{
		inTransaction(session -> {
			for (int offset : List.of(0, 2))
			{
				JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
				builder.forWebQuery(new WebQuery().dbfetch("none").offset(offset).limit(2));
				var children = ((jakarta.persistence.criteria.From<?, ?>) builder.getProperty("entity")).join("children");
				builder.addConstraints(session.getCriteriaBuilder().isTrue(children.get("flag")));
				assertTrue(builder.hasCollectionJoin());
				assertEquals(Long.valueOf(3), builder.selectCount());
				assertEquals(offset == 0 ? List.of(1L, 2L) : List.of(3L), builder.selectIDs());
				assertEquals(offset == 0 ? List.of(1L, 2L) : List.of(3L), ids(builder.selectEntity()));
			}
			return null;
		});
	}

	@Test
	public void directFetchJoinsRemainUsableInNonEntityTerminals()
	{
		inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().dbfetch("none"));
			var root = (jakarta.persistence.criteria.From<?, ?>) builder.getProperty("entity");
			var fetch = root.fetch("children", jakarta.persistence.criteria.JoinType.INNER);
			builder.addConstraints(session.getCriteriaBuilder().isTrue(((jakarta.persistence.criteria.Join<?, ?>) fetch).get("flag")));
			assertEquals(Long.valueOf(3), builder.selectCount());
			assertEquals(List.of(1L, 2L, 3L), builder.selectIDs());
			assertEquals(6, builder.selectCustomProjection(false, "name").size());
			assertEquals(2, builder.selectCustomProjection(true, "name").size());
			return null;
		});
	}

	@Test
	public void directFetchGraphsSurviveEntityPaginationOutsideTheSession()
	{
		for (boolean nested : List.of(false, true))
		{
			List<Parent> values = inTransaction(session -> {
				JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
				builder.forWebQuery(new WebQuery().dbfetch("none").limit(2));
				var root = (jakarta.persistence.criteria.From<?, ?>) builder.getProperty("entity");
				var children = root.fetch("children", jakarta.persistence.criteria.JoinType.LEFT);
				if (nested)
					children.fetch("leaves", jakarta.persistence.criteria.JoinType.LEFT);
				return builder.selectEntity();
			});
			assertEquals(List.of(1L, 2L), ids(values));
			for (Parent value : values)
			{
				assertCompleteChildren(value);
				if (nested)
					for (Child child : value.children)
					{
						assertTrue(org.hibernate.Hibernate.isInitialized(child.leaves));
						assertEquals(child.id % 10 == 0 ? 0 : 2, child.leaves.size());
					}
			}
		}
	}

	@Test
	public void directUnsafeFetchGraphsUseIsolatedLoaders()
	{
		List<Parent> values = inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().dbfetch("none"));
			var root = (jakarta.persistence.criteria.From<?, ?>) builder.getProperty("entity");
			root.fetch("children", jakarta.persistence.criteria.JoinType.LEFT).fetch("leaves", jakarta.persistence.criteria.JoinType.LEFT);
			root.fetch("tags", jakarta.persistence.criteria.JoinType.LEFT);
			return builder.selectEntity();
		});
		for (Parent value : values)
		{
			assertCompleteChildren(value);
			assertEquals(java.util.Set.of("a", "b"), value.tags);
			for (Child child : value.children)
				assertEquals(child.id % 10 == 0 ? 0 : 2, child.leaves.size());
		}
	}

	@Test
	public void directBagFetchAndSeparateFilterPreserveCompleteCollections()
	{
		List<Parent> values = inTransaction(session -> {
			JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
			builder.forWebQuery(new WebQuery().dbfetch("none"));
			var root = (jakarta.persistence.criteria.From<?, ?>) builder.getProperty("entity");
			root.fetch("children", jakarta.persistence.criteria.JoinType.LEFT);
			var matchingChildren = root.join("children");
			builder.addConstraints(session.getCriteriaBuilder().isTrue(matchingChildren.get("flag")));
			assertTrue(builder.hasListOrCollectionFetch());
			return builder.selectEntity();
		});
		assertEquals(3, values.size());
		for (Parent value : values)
			assertCompleteChildren(value);
	}

	@Test
	public void treatedRootFetchesPreserveSubtypeMetadataAndSkipSiblingRows()
	{
		List<List<Parent>> results = inTransaction(session -> {
			SpecialParent special = new SpecialParent();
			special.id = 4L;
			special.name = "special";
			special.extras.addAll(java.util.Set.of("first", "second"));
			session.persist(special);
			SiblingParent sibling = new SiblingParent();
			sibling.id = 5L;
			sibling.name = "sibling";
			session.persist(sibling);
			session.flush();
			session.clear();
			var cb = session.getCriteriaBuilder();
			var nativeCriteria = cb.createQuery(Parent.class);
			var nativeRoot = nativeCriteria.from(Parent.class);
			cb.treat(nativeRoot, SpecialParent.class).fetch("extras", jakarta.persistence.criteria.JoinType.LEFT);
			nativeCriteria.select(nativeRoot).orderBy(cb.asc(nativeRoot.get("id")));
			List<Long> expected = ids(session.createQuery(nativeCriteria).getResultList());
			assertEquals(List.of(1L, 2L, 3L, 4L, 5L), expected);
			session.clear();
			List<List<Parent>> values = new ArrayList<>();
			for (boolean paginated : List.of(false, true))
				for (boolean withBag : List.of(false, true))
				{
					JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
					WebQuery query = new WebQuery().dbfetch(withBag ? "children" : "none").orderAsc("id");
					if (paginated)
						query.limit(5);
					builder.forWebQuery(query);
					var root = (jakarta.persistence.criteria.Root<Parent>) builder.getProperty("entity");
					cb.treat(root, SpecialParent.class).fetch("extras", jakarta.persistence.criteria.JoinType.LEFT);
					List<Parent> rows = builder.selectEntity();
					assertEquals(expected, ids(rows));
					if (withBag)
						for (Parent row : rows)
							if (row.id <= 3)
								assertCompleteChildren(row);
					values.add(rows);
					session.clear();
				}
			return values;
		});
		for (List<Parent> rows : results)
		{
			SpecialParent special = (SpecialParent) rows.get(3);
			assertTrue(org.hibernate.Hibernate.isInitialized(special.extras));
			assertEquals(java.util.Set.of("first", "second"), special.extras);
		}
	}

	@Test
	public void treatedCollectionFetchesSurvivePaginationAndUnsafeBagLoading()
	{
		for (boolean paginated : List.of(false, true))
		{
			List<Parent> values = inTransaction(session -> {
				var cb = session.getCriteriaBuilder();
				var nativeCriteria = cb.createQuery(Parent.class);
				var nativeRoot = nativeCriteria.from(Parent.class);
				var nativeChildren = (jakarta.persistence.criteria.ListJoin<Parent, Child>) (Object)
						nativeRoot.fetch("children", jakarta.persistence.criteria.JoinType.LEFT);
				cb.treat(nativeChildren, SpecialChild.class).fetch("extras", jakarta.persistence.criteria.JoinType.LEFT);
				nativeCriteria.select(nativeRoot).where(cb.equal(nativeRoot.get("id"), 1L));
				assertEquals(List.of(1L), ids(session.createQuery(nativeCriteria).getResultList()));
				session.clear();
				JPAQueryBuilder<Parent, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Parent.class), false);
				WebQuery query = new WebQuery().eq("id", 1L).dbfetch("children");
				if (paginated)
					query.limit(1);
				builder.forWebQuery(query);
				var root = (jakarta.persistence.criteria.Root<Parent>) builder.getProperty("entity");
				var children = (jakarta.persistence.criteria.ListJoin<Parent, Child>) (Object) root.fetch("children", jakarta.persistence.criteria.JoinType.LEFT);
				session.getCriteriaBuilder().treat(children, SpecialChild.class).fetch("extras", jakarta.persistence.criteria.JoinType.LEFT);
				return builder.selectEntity();
			});
			assertEquals(List.of(1L), ids(values));
			Parent parent = values.getFirst();
			assertCompleteChildren(parent);
			SpecialChild child = (SpecialChild) parent.children.stream().filter(value -> value.id == 11L).findFirst().orElseThrow();
			assertTrue(org.hibernate.Hibernate.isInitialized(child.extras));
			assertEquals(java.util.Set.of("first", "second"), child.extras);
		}
	}

	@Test
	public void treatedAssociationFetchesRetainTheTargetSubtypeAfterPagination()
	{
		List<Child> values = inTransaction(session -> {
			SpecialParent special = new SpecialParent();
			special.id = 4L;
			special.name = "special";
			special.extras.add("extra");
			session.persist(special);
			Child child = new Child();
			child.id = 40L;
			child.parent = special;
			session.persist(child);
			session.flush();
			session.clear();
			var cb = session.getCriteriaBuilder();
			var nativeCriteria = cb.createQuery(Child.class);
			var nativeRoot = nativeCriteria.from(Child.class);
			var nativeParent = (jakarta.persistence.criteria.Join<Child, Parent>) (Object)
					nativeRoot.fetch("parent", jakarta.persistence.criteria.JoinType.LEFT);
			cb.treat(nativeParent, SpecialParent.class).fetch("extras", jakarta.persistence.criteria.JoinType.LEFT);
			nativeCriteria.select(nativeRoot).where(cb.equal(nativeRoot.get("id"), 40L));
			assertEquals(Long.valueOf(40L), session.createQuery(nativeCriteria).getSingleResult().id);
			session.clear();
			List<Child> results = new ArrayList<>();
			for (boolean paginated : List.of(false, true))
			{
				JPAQueryBuilder<Child, Long> builder = new JPAQueryBuilder<>(session, schemas.get(Child.class), false);
				WebQuery query = new WebQuery().eq("id", 40L).dbfetch("none");
				if (paginated)
					query.limit(1);
				builder.forWebQuery(query);
				var root = (jakarta.persistence.criteria.Root<Child>) builder.getProperty("entity");
				var parent = (jakarta.persistence.criteria.Join<Child, Parent>) (Object)
						root.fetch("parent", jakarta.persistence.criteria.JoinType.LEFT);
				cb.treat(parent, SpecialParent.class).fetch("extras", jakarta.persistence.criteria.JoinType.LEFT);
				results.addAll(builder.selectEntity());
				session.clear();
			}
			return results;
		});
		assertEquals(2, values.size());
		for (Child child : values)
		{
			SpecialParent parent = (SpecialParent) org.hibernate.Hibernate.unproxy(child.parent);
			assertTrue(org.hibernate.Hibernate.isInitialized(parent.extras));
			assertEquals(java.util.Set.of("extra"), parent.extras);
		}
	}

	private void assertCompleteChildren(Parent parent)
	{
		assertTrue(org.hibernate.Hibernate.isInitialized(parent.children));
		assertEquals(List.of(parent.id * 10, parent.id * 10 + 1, parent.id * 10 + 2),
		             parent.children.stream().map(child -> child.id).sorted().toList());
	}

	private ConstrainedResultSet<Parent> find(WebQuery query)
	{
		return inTransaction(session -> executor.find(schemas.get(Parent.class), query, JPASearchStrategy.AUTO, null, false));
	}

	private List<Long> findIds(WebQuery query)
	{
		return inTransaction(session -> executor.<Long>find(schemas.get(Parent.class), query, JPASearchStrategy.ID, null, false).getList());
	}

	private List<Long> ids(List<Parent> rows)
	{
		return rows.stream().map(row -> row.id).toList();
	}

	private <T> T inTransaction(Function<Session, T> action)
	{
		Session session = factory.getCurrentSession();
		var tx = session.beginTransaction();
		try
		{
			T result = action.apply(session);
			tx.commit();
			return result;
		}
		catch (RuntimeException | Error ex)
		{
			if (tx.isActive())
				tx.rollback();
			throw ex;
		}
	}

	private void addParent(Session session, long id, String name, float ratio, int... ranks)
	{
		Parent parent = new Parent();
		parent.id = id;
		parent.name = name;
		parent.ratio = ratio;
		parent.tags.addAll(List.of("a", "b"));
		parent.labels.addAll(List.of("same", "same", "other"));
		session.persist(parent);
		for (int c = 0; c < ranks.length; c++)
		{
			Child child = id == 1 && c == 1 ? new SpecialChild() : id == 1 && c == 2 ? new SiblingChild() : new Child();
			if (child instanceof SpecialChild special)
				special.extras.addAll(java.util.Set.of("first", "second"));
			child.id = id * 10 + c;
			child.parent = parent;
			child.flag = c < 2;
			child.rank = ranks[c];
			child.timecode = Timecode.getInstance("00:00:0" + id + ":00@25");
			child.samples = new SampleCount(id * 25, Timebase.HZ_25);
			child.uuid = new UUID(0, id);
			child.state = id == 2 ? State.DONE : State.READY;
			session.persist(child);
			if (c > 0)
				for (long l = 1; l <= 2; l++)
				{
					Leaf leaf = new Leaf();
					leaf.id = child.id * 10 + l;
					leaf.child = child;
					session.persist(leaf);
				}
		}
	}

	@Entity(name = "execution_parent")
	public static class Parent
	{
		@Id public Long id;
		public String name;
		public Float ratio;
		@EagerFetch @OneToMany(mappedBy = "parent") public List<Child> children = new ArrayList<>();
		@jakarta.persistence.ElementCollection public java.util.Set<String> tags = new java.util.HashSet<>();
		@jakarta.persistence.ElementCollection public List<String> labels = new ArrayList<>();
	}

	@Entity(name = "execution_special")
	@DiscriminatorValue("special")
	public static class SpecialParent extends Parent
	{
		@jakarta.persistence.ElementCollection public java.util.Set<String> extras = new java.util.HashSet<>();
	}

	@Entity(name = "execution_sibling")
	@DiscriminatorValue("sibling")
	public static class SiblingParent extends Parent {}

	@Entity(name = "execution_child")
	public static class Child
	{
		@Id public Long id;
		@OneToMany(mappedBy = "child") public java.util.Set<Leaf> leaves = new java.util.HashSet<>();
		@ManyToOne(fetch = FetchType.LAZY) public Parent parent;
		public boolean flag;
		@jakarta.persistence.Column(name = "sort_rank") public Integer rank;
		public Timecode timecode;
		public SampleCount samples;
		public UUID uuid;
		@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING) public State state;
	}

	@Entity(name = "execution_special_child")
	@DiscriminatorValue("special_child")
	public static class SpecialChild extends Child
	{
		@jakarta.persistence.ElementCollection public java.util.Set<String> extras = new java.util.HashSet<>();
	}

	@Entity(name = "execution_sibling_child")
	@DiscriminatorValue("sibling_child")
	public static class SiblingChild extends Child {}

	@Entity(name = "execution_leaf")
	public static class Leaf
	{
		@Id public Long id;
		@ManyToOne(fetch = FetchType.LAZY) public Child child;
	}

	@Embeddable
	public static class CompositeId implements Serializable
	{
		public String label;
		@jakarta.persistence.Column(name = "id_value") public long value;
		public CompositeId() {}
		public CompositeId(String label, long value) { this.label = label; this.value = value; }
		@Override public boolean equals(Object other)
		{
			return other instanceof CompositeId id && value == id.value && Objects.equals(label, id.label);
		}
		@Override public int hashCode() { return Objects.hash(label, value); }
	}

	@Entity(name = "execution_composite")
	public static class CompositeParent
	{
		@EmbeddedId public CompositeId id;
		public String name;
		@OneToMany(mappedBy = "parent") public List<CompositeChild> children = new ArrayList<>();
	}

	public enum State { READY, DONE }

	@jakarta.persistence.MappedSuperclass
	public static class GenericBase<E extends Enum<E>>
	{
		@Id public Long id;
		@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING) public E state;
	}

	@Entity(name = "execution_generic")
	public static class GenericParent extends GenericBase<State> {}

	@jakarta.persistence.MappedSuperclass
	public static class GenericIdentifier<I extends Serializable>
	{
		private I id;
		@Id public I getId() { return id; }
		public void setId(I id) { this.id = id; }
	}

	@Entity(name = "execution_generic_identifier")
	@jakarta.persistence.Access(jakarta.persistence.AccessType.FIELD)
	public static class GenericIdentifierOwner extends GenericIdentifier<Long>
	{
		@Override public Long getId() { return super.getId(); }
		@Override public void setId(Long id) { super.setId(id); }
		@OneToMany(mappedBy = "owner") public List<GenericIdentifierChild> children = new ArrayList<>();
	}

	@Entity(name = "execution_generic_identifier_child")
	public static class GenericIdentifierChild
	{
		@Id public Long id;
		@ManyToOne(fetch = FetchType.LAZY) public GenericIdentifierOwner owner;
		public boolean flag;
	}

	@Entity(name = "execution_composite_child")
	public static class CompositeChild
	{
		@Id public Long id;
		@ManyToOne(fetch = FetchType.LAZY) public CompositeParent parent;
		public boolean flag;
	}
}
