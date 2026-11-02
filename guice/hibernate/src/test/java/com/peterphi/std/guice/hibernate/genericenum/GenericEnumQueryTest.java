package com.peterphi.std.guice.hibernate.genericenum;

import com.google.inject.Inject;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntityFactory;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.testing.GuiceUnit;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(GuiceUnit.class)
@GuiceConfig(config = "hibernate-tests-in-memory-hsqldb.properties", classPackages = GenericEnumQueryTest.class)
public class GenericEnumQueryTest
{
	@Inject
	HibernateDao<ConcreteEntity, Long> dao;

	@Inject
	QEntityFactory schemas;

	@Test
	@Transactional
	public void inheritedGenericFieldSupportsSchemaAndFiltering()
	{
		ConcreteEntity entity = new ConcreteEntity();
		entity.state = State.READY;
		dao.persist(entity);

		assertEquals(State.class, schemas.get(ConcreteEntity.class).getProperty("state", false).getClazz());
		assertEquals(1, dao.find(new WebQuery().eq("state", "READY")).getList().size());
		assertEquals(0, dao.find(new WebQuery().eq("state", "DONE")).getList().size());
		assertEquals(1, dao.find(new WebQuery().in("state", "READY", "DONE")).getList().size());
	}

	public enum State { READY, DONE }

	@MappedSuperclass
	public static abstract class GenericBase<E extends Enum<E>>
	{
		@Id
		@GeneratedValue
		public Long id;

		@Enumerated(EnumType.STRING)
		E state;
	}

	@MappedSuperclass
	public static abstract class Intermediate<E extends Enum<E>> extends GenericBase<E>
	{
	}

	@Entity(name = "generic_enum_entity")
	public static class ConcreteEntity extends Intermediate<State>
	{
	}
}
