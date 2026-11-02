package com.peterphi.std.guice.hibernate.metadata;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.peterphi.std.guice.database.annotation.WebQueryPrivate;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntityFactory;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.PrivatePropertyUseRejected;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPAQueryBuilder;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPASearchExecutor;
import com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPASearchStrategy;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IdentifierPrivacyTest
{
	@Test
	public void explicitIdentifierResultsAreProtectedButInternalPaginationStillWorks()
	{
		try (SessionFactory factory = factory(PrivateIdentifierOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(PrivateIdentifierOwner.class);
			factory.inTransaction(session -> {
				PrivateIdentifierOwner owner = new PrivateIdentifierOwner();
				owner.setId(1L);
				MetadataResolutionTest.GenericInfo<MetadataResolutionTest.State> child = new MetadataResolutionTest.GenericInfo<>();
				child.state = MetadataResolutionTest.State.READY;
				owner.children.add(child);
				session.persist(owner);
			});
			JPASearchExecutor executor = Guice.createInjector(new AbstractModule() {
				@Override protected void configure() { bind(SessionFactory.class).toInstance(factory); }
			}).getInstance(JPASearchExecutor.class);
			var session = factory.getCurrentSession();
			var tx = session.beginTransaction();
			try
			{
				assertThrows(PrivatePropertyUseRejected.class, () -> executor.find(entity, new WebQuery().fetch("id"), JPASearchStrategy.AUTO, null, false));
				assertThrows(PrivatePropertyUseRejected.class, () -> executor.find(entity, new WebQuery(), JPASearchStrategy.ID, null, false));
				assertThrows(PrivatePropertyUseRejected.class, () -> executor.find(entity, new WebQuery(), JPASearchStrategy.ENTITY_WRAPPED_ID, null, false));
				assertEquals(List.of(1L), executor.find(entity, new WebQuery(), JPASearchStrategy.ID, null, true).getList());
				var page = executor.<PrivateIdentifierOwner>find(entity, new WebQuery().eq("children.state", "READY").limit(1), JPASearchStrategy.AUTO, null, false);
				assertEquals(1, page.getList().size());
				assertEquals(Long.valueOf(1L), page.getList().getFirst().getId());
			}
			finally
			{
				tx.rollback();
			}
		}
	}

	@Test
	public void privateEmbeddedIdentifierContainerProtectsWholeIdResults()
	{
		try (SessionFactory factory = factory(PrivateEmbeddedIdentifierOwner.class, PrivateComponentOwner.class))
		{
			QEntityFactory schemas = new QEntityFactory(factory);
			var entity = schemas.get(PrivateEmbeddedIdentifierOwner.class);
			assertThrows(PrivatePropertyUseRejected.class, () -> entity.assertIdentifierAccessible(false));
			assertThrows(PrivatePropertyUseRejected.class, () -> entity.getProperty("id", false));
			entity.assertIdentifierAccessible(true);
			var componentOwner = schemas.get(PrivateComponentOwner.class);
			assertThrows(PrivatePropertyUseRejected.class, () -> componentOwner.assertIdentifierAccessible(false));
			assertThrows(PrivatePropertyUseRejected.class, () -> componentOwner.getProperty("id", false));
			componentOwner.assertIdentifierAccessible(true);
			try (var session = factory.openSession())
			{
				JPAQueryBuilder<PrivateEmbeddedIdentifierOwner, Key> builder = new JPAQueryBuilder<>(session, entity, false);
				builder.forWebQuery(new WebQuery());
				assertThrows(PrivatePropertyUseRejected.class, builder::selectIDs);
			}
		}
	}

	private SessionFactory factory(Class<?>... entities)
	{
		Configuration config = new Configuration();
		config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
		config.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:" + UUID.randomUUID());
		config.setProperty("hibernate.connection.username", "sa");
		config.setProperty("hibernate.hbm2ddl.auto", "create-drop");
		config.setProperty("hibernate.current_session_context_class", "thread");
		for (Class<?> entity : entities)
			config.addAnnotatedClass(entity);
		return config.buildSessionFactory();
	}

	@MappedSuperclass
	public static class GenericIdentifier<I extends Serializable>
	{
		private I id;
		@Id public I getId() { return id; }
		public void setId(I id) { this.id = id; }
	}

	@Entity(name = "private_identifier_owner")
	public static class PrivateIdentifierOwner extends GenericIdentifier<Long>
	{
		@Override @WebQueryPrivate public Long getId() { return super.getId(); }
		@ElementCollection
		public Set<MetadataResolutionTest.GenericInfo<MetadataResolutionTest.State>> getChildren() { return children; }
		public void setChildren(Set<MetadataResolutionTest.GenericInfo<MetadataResolutionTest.State>> children) { this.children = children; }
		private Set<MetadataResolutionTest.GenericInfo<MetadataResolutionTest.State>> children = new HashSet<>();
	}

	@Embeddable
	public static class Key implements Serializable
	{
		public Long value;
		@Override public boolean equals(Object other) { return other instanceof Key key && java.util.Objects.equals(value, key.value); }
		@Override public int hashCode() { return java.util.Objects.hashCode(value); }
	}

	@Entity(name = "private_embedded_identifier_owner")
	public static class PrivateEmbeddedIdentifierOwner
	{
		@EmbeddedId @WebQueryPrivate public Key id;
	}

	@Embeddable
	public static class PrivateComponentKey implements Serializable
	{
		@WebQueryPrivate public Long part;
		@Override public boolean equals(Object other) { return other instanceof PrivateComponentKey key && java.util.Objects.equals(part, key.part); }
		@Override public int hashCode() { return java.util.Objects.hashCode(part); }
	}

	@Entity(name = "private_component_owner")
	public static class PrivateComponentOwner
	{
		@EmbeddedId public PrivateComponentKey id;
	}
}
