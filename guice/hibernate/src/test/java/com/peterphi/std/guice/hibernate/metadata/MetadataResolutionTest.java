package com.peterphi.std.guice.hibernate.metadata;

import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.guice.database.annotation.WebQueryPrivate;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntityFactory;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.PrivatePropertyUseRejected;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPAQueryBuilder;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Inheritance;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MetadataResolutionTest
{
	private SessionFactory factory(Class<?>... entities)
	{
		Configuration config = new Configuration();
		config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
		config.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:" + UUID.randomUUID());
		config.setProperty("hibernate.connection.username", "sa");
		config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
		for (Class<?> entity : entities)
			config.addAnnotatedClass(entity);
		return config.buildSessionFactory();
	}

	@Test
	public void concreteGenericOverrideKeepsPrivacyAcrossAllQueryRoutes()
	{
		try (SessionFactory factory = factory(PrivateEntity.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(PrivateEntity.class);
			assertTrue(entity.getProperty("state", true).isSchemaPrivate());
			assertFalse(entity.encode().properties.stream().anyMatch(property -> property.name.equals("state")));
			try (Session session = factory.openSession())
			{
				var tx = session.beginTransaction();
				PrivateEntity row = new PrivateEntity();
				row.setId(1L);
				row.setState(State.READY);
				session.persist(row);
				assertThrows(PrivatePropertyUseRejected.class, () -> query(session, schemas, PrivateEntity.class, new WebQuery().eq("state", "READY")));
				assertThrows(PrivatePropertyUseRejected.class, () -> query(session, schemas, PrivateEntity.class, new WebQuery().orderAsc("state")));
				assertThrows(PrivatePropertyUseRejected.class, () -> builder(session, schemas, PrivateEntity.class).selectCustomProjection(false, "state"));
				JPAQueryBuilder<PrivateEntity, Long> permitted = new JPAQueryBuilder<>(session, entity, true);
				permitted.forWebQuery(new WebQuery().eq("state", "READY"));
				assertEquals(List.of(1L), permitted.selectIDs());
				tx.rollback();
			}
		}
	}

	@Test
	public void genericEmbeddableBindingsStayDistinctAndSupportFiltering()
	{
		try (SessionFactory factory = factory(EmbeddedOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(EmbeddedOwner.class);
			assertEquals(State.class, entity.getProperty("first:state", false).getClazz());
			assertEquals(Mode.class, entity.getProperty("second:state", false).getClazz());
			assertFalse("Two embedded states must not silently overwrite a flattened alias", entity.hasProperty("state"));
			assertEquals(2, entity.encode().properties.stream().filter(property -> property.name.endsWith(":state")).count());
			try (Session session = factory.openSession())
			{
				var tx = session.beginTransaction();
				EmbeddedOwner row = new EmbeddedOwner();
				row.id = 1L;
				row.label = "test";
				row.first.state = State.READY;
				row.second.state = Mode.ACTIVE;
				session.persist(row);
				session.flush();
				session.clear();
				assertEquals(List.of(1L), query(session, schemas, EmbeddedOwner.class, new WebQuery().eq("label", "test")));
				assertEquals(List.of(1L), query(session, schemas, EmbeddedOwner.class, new WebQuery().eq("first:state", "READY")));
				assertEquals(List.of(1L), query(session, schemas, EmbeddedOwner.class, new WebQuery().eq("second:state", "ACTIVE")));
				assertEquals(State.READY, session.find(EmbeddedOwner.class, 1L).first.state);
				tx.rollback();
			}
		}
	}

	@Test
	public void privateEmbeddedContainerProtectsQualifiedAndLegacyPaths()
	{
		try (SessionFactory factory = factory(PrivateEmbeddedOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			try (Session session = factory.openSession())
			{
				assertThrows(PrivatePropertyUseRejected.class, () -> query(session, schemas, PrivateEmbeddedOwner.class, new WebQuery().eq("secret:state", "READY")));
				assertThrows(PrivatePropertyUseRejected.class, () -> query(session, schemas, PrivateEmbeddedOwner.class, new WebQuery().eq("state", "READY")));
				assertTrue(schemas.get(PrivateEmbeddedOwner.class).encode().properties.stream().noneMatch(property -> property.name.contains("state")));
			}
		}
	}

	@Test
	public void concreteGenericAssociationOverrideKeepsEagerFetching()
	{
		try (SessionFactory factory = factory(Target.class, EagerOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			factory.inTransaction(session -> {
				Target target = new Target();
				target.id = 10L;
				session.persist(target);
				EagerOwner owner = new EagerOwner();
				owner.setId(1L);
				owner.setTarget(target);
				session.persist(owner);
			});
			EagerOwner loaded;
			try (Session session = factory.openSession())
			{
				loaded = session.find(EagerOwner.class, 1L, Map.of("jakarta.persistence.fetchgraph", schemas.get(EagerOwner.class).getDefaultGraph(session)));
			}
			assertTrue(Hibernate.isInitialized(loaded.getTarget()));
		}
	}

	@Test
	public void genericElementCollectionsHaveSeparateSchemaBindings()
	{
		try (SessionFactory factory = factory(CollectionOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(CollectionOwner.class);
			var states = entity.getRelation("states").getEntity();
			var modes = entity.getRelation("modes").getEntity();
			assertEquals(State.class, states.getProperty("state", false).getClazz());
			assertEquals(Mode.class, modes.getProperty("state", false).getClazz());
			assertFalse(states.getName().equals(modes.getName()));
			try (Session session = factory.openSession())
			{
				var tx = session.beginTransaction();
				CollectionOwner row = new CollectionOwner();
				row.id = 1L;
				GenericInfo<State> state = new GenericInfo<>();
				state.state = State.READY;
				row.states.add(state);
				GenericInfo<Mode> mode = new GenericInfo<>();
				mode.state = Mode.ACTIVE;
				row.modes.add(mode);
				session.persist(row);
				assertEquals(List.of(1L), query(session, schemas, CollectionOwner.class, new WebQuery().eq("states.state", "READY")));
				assertEquals(List.of(1L), query(session, schemas, CollectionOwner.class, new WebQuery().eq("modes.state", "ACTIVE")));
				tx.rollback();
			}
		}
	}

	@Test
	public void privateRelationsAndBasicCollectionsValidateEveryNamedPath()
	{
		try (SessionFactory factory = factory(Target.class, PrivateRelations.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(PrivateRelations.class);
			assertThrows(PrivatePropertyUseRejected.class, () -> entity.resolveFetchPath("target", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> entity.resolveFetchPath("labels", false));
			assertThrows(PrivatePropertyUseRejected.class, () -> entity.resolveFetchPath("attributes", false));
			assertEquals("labels", entity.resolveFetchPath("labels", true));
			try (Session session = factory.openSession())
			{
				assertThrows(PrivatePropertyUseRejected.class, () -> query(session, schemas, PrivateRelations.class, new WebQuery().eq("target.id", "10")));
				assertThrows(PrivatePropertyUseRejected.class, () -> query(session, schemas, PrivateRelations.class, new WebQuery().eq("infos:size", "0")));
			}
		}
	}

	@Test
	public void embeddedEagerAssociationUsesPhysicalPathAndKeepsLegacyAlias()
	{
		try (SessionFactory factory = factory(Target.class, EmbeddedAssociationOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(EmbeddedAssociationOwner.class);
			assertEquals("info.target", entity.resolveFetchPath("info:target", false));
			assertEquals("info.target", entity.resolveFetchPath("info.target", false));
			assertEquals("info.target", entity.resolveFetchPath("target", false));
			factory.inTransaction(session -> {
				Target target = new Target();
				target.id = 10L;
				session.persist(target);
				EmbeddedAssociationOwner owner = new EmbeddedAssociationOwner();
				owner.id = 1L;
				owner.info = new EmbeddedAssociation();
				owner.info.target = target;
				session.persist(owner);
			});
			EmbeddedAssociationOwner loaded;
			try (Session session = factory.openSession())
			{
				loaded = session.find(EmbeddedAssociationOwner.class, 1L, Map.of("jakarta.persistence.fetchgraph", entity.getDefaultGraph(session)));
				assertEquals(List.of(1L), query(session, schemas, EmbeddedAssociationOwner.class, new WebQuery().eq("info:target.id", "10")));
			}
			assertTrue(Hibernate.isInitialized(loaded.info.target));
		}
	}

	@Test
	public void failedLazyMetadataConstructionDoesNotLeaveAnIncompleteCacheEntry()
	{
		try (SessionFactory factory = factory(Target.class, CompositeEntity.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			assertThrows(IllegalArgumentException.class, () -> schemas.get(CompositeEntity.class));
			assertThrows(IllegalArgumentException.class, () -> schemas.get(CompositeEntity.class));
			assertEquals(Long.class, schemas.get(Target.class).getProperty("id", false).getClazz());
		}
	}

	private <T> JPAQueryBuilder<T, Long> builder(Session session, QEntityFactory schemas, Class<T> type)
	{
		JPAQueryBuilder<T, Long> builder = new JPAQueryBuilder<>(session, schemas.get(type), false);
		builder.forWebQuery(new WebQuery());
		return builder;
	}

	private <T> List<Long> query(Session session, QEntityFactory schemas, Class<T> type, WebQuery query)
	{
		JPAQueryBuilder<T, Long> builder = new JPAQueryBuilder<>(session, schemas.get(type), false);
		builder.forWebQuery(query);
		return builder.selectIDs();
	}

	public enum State { READY, DONE }
	public enum Mode { ACTIVE, PAUSED }

	@MappedSuperclass
	public abstract static class GenericBase<E extends Enum<E>>
	{
		private Long id;
		@Id public Long getId() { return id; }
		public void setId(Long id) { this.id = id; }
		@Enumerated(EnumType.STRING) public abstract E getState();
		public abstract void setState(E state);
	}

	@Entity(name = "private_generic_entity")
	public static class PrivateEntity extends GenericBase<State>
	{
		private State state;
		@Override @WebQueryPrivate public State getState() { return state; }
		@Override public void setState(State state) { this.state = state; }
	}

	@Embeddable
	@Access(AccessType.FIELD)
	public static class GenericInfo<E extends Enum<E>>
	{
		@Enumerated(EnumType.STRING) public E state;
	}

	@Entity(name = "embedded_generic_owner")
	public static class EmbeddedOwner
	{
		@Id public Long id;
		public String label;
		@Embedded @AttributeOverride(name = "state", column = @Column(name = "first_state"))
		public GenericInfo<State> first = new GenericInfo<>();
		@Embedded @AttributeOverride(name = "state", column = @Column(name = "second_state"))
		public GenericInfo<Mode> second = new GenericInfo<>();
	}

	@Entity(name = "private_embedded_owner")
	public static class PrivateEmbeddedOwner
	{
		@Id public Long id;
		@Embedded @WebQueryPrivate public GenericInfo<State> secret;
	}

	@Entity(name = "eager_target")
	public static class Target
	{
		@Id public Long id;
	}

	@MappedSuperclass
	public abstract static class EagerBase<T extends Target>
	{
		private Long id;
		@Id public Long getId() { return id; }
		public void setId(Long id) { this.id = id; }
		@ManyToOne(fetch = FetchType.LAZY, targetEntity = Target.class) public abstract T getTarget();
		public abstract void setTarget(T target);
	}

	@Entity(name = "eager_generic_owner")
	public static class EagerOwner extends EagerBase<Target>
	{
		private Target target;
		@Override @EagerFetch @ManyToOne(fetch = FetchType.LAZY, targetEntity = Target.class)
		public Target getTarget() { return target; }
		@Override public void setTarget(Target target) { this.target = target; }
	}

	@Entity(name = "generic_collection_owner")
	public static class CollectionOwner
	{
		@Id public Long id;
		@ElementCollection public Set<GenericInfo<State>> states = new HashSet<>();
		@ElementCollection public Set<GenericInfo<Mode>> modes = new HashSet<>();
	}

	@Entity(name = "private_relations_owner")
	public static class PrivateRelations
	{
		@Id public Long id;
		@WebQueryPrivate @ManyToOne public Target target;
		@WebQueryPrivate @ElementCollection public Set<String> labels;
		@WebQueryPrivate @ElementCollection public Map<String, String> attributes;
		@WebQueryPrivate @ElementCollection public Set<GenericInfo<State>> infos;
	}

	@Embeddable
	public static class EmbeddedAssociation
	{
		@EagerFetch @ManyToOne(fetch = FetchType.LAZY) public Target target;
	}

	@Entity(name = "embedded_association_owner")
	public static class EmbeddedAssociationOwner
	{
		@Id public Long id;
		@Embedded public EmbeddedAssociation info;
	}

	public static class CompositeKey implements java.io.Serializable
	{
		public Long first;
		public Long second;
		@Override public boolean equals(Object other) { return other instanceof CompositeKey key && java.util.Objects.equals(first, key.first) && java.util.Objects.equals(second, key.second); }
		@Override public int hashCode() { return java.util.Objects.hash(first, second); }
	}

	@Entity(name = "unsupported_composite_entity")
	@IdClass(CompositeKey.class)
	@Inheritance
	public abstract static class CompositeEntity
	{
		@Id public Long first;
		@Id public Long second;
	}
}
