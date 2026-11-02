package com.peterphi.std.guice.hibernate.webquery.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQSchemas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class QEntityFactory
{
	private static final Logger log = LoggerFactory.getLogger(QEntityFactory.class);

	private final Map<Class<?>, QEntity> entities = new HashMap<>();
	private final SessionFactoryImplementor sessionFactory;
	private final Map<Class<?>, EmbeddableType<?>> embeddableTypeMap = new HashMap<>();


	@Inject
	public QEntityFactory(final SessionFactory sessionFactory)
	{
		this.sessionFactory = (SessionFactoryImplementor) sessionFactory;

		if (log.isDebugEnabled())
			log.debug("Known entities: {}",
			          sessionFactory.getMetamodel().getEntities().stream().map(e -> e.getName()).collect(Collectors.toList()));

		// Pre-construct the QEntity instances for all known entities
		for (EntityType<?> entityType : sessionFactory.getMetamodel().getEntities())
		{
			final Class clazz = entityType.getJavaType();

			if (clazz != null && !Modifier.isAbstract(clazz.getModifiers()))
				get(clazz);
		}
	}


	public List<QEntity> getSubclasses(Class<?> superclass)
	{
		List<QEntity> subclasses = new ArrayList<>();

		for (EntityType<?> entityType : sessionFactory.getMetamodel().getEntities())
		{
			final Class<?> clazz = entityType.getJavaType();

			if (clazz != null && !Modifier.isAbstract(clazz.getModifiers()) && superclass.isAssignableFrom(clazz))
				subclasses.add(get(clazz));
		}

		return subclasses;
	}


	public <T> QEntity get(Class<T> clazz)
	{
		if (!entities.containsKey(clazz))
		{
			if (log.isDebugEnabled())
				log.debug("Begin create QEntity {}", clazz);

			final EntityType<T> metadata = sessionFactory.getMetamodel().entity(clazz);

			if (metadata == null)
				throw new IllegalArgumentException("Hibernate has no ClassMetadata for: " +
				                                   clazz +
				                                   " (should be following Embeddable codepath?)");

			QEntity entity = new QEntity(clazz);
			entities.put(clazz, entity);

			entity.parse(this, metadata, sessionFactory);

			if (log.isDebugEnabled())
				log.debug("End create QEntity {}", clazz);
		}

		return entities.get(clazz);
	}


	public QEntity getEmbeddable(final Class clazz, final EmbeddableType ct)
	{
		if (!entities.containsKey(clazz))
		{
			if (log.isDebugEnabled())
				log.debug("Begin create QEntity {} from EmbeddableType {}", clazz, ct);

			QEntity entity = new QEntity(clazz);
			entities.put(clazz, entity);

			entity.parseEmbeddable(this, sessionFactory, null, ct);

			if (log.isDebugEnabled())
				log.debug("End create QEntity {} from EmbeddableType {}", clazz, ct);
		}

		return entities.get(clazz);
	}


	public WQSchemas encode()
	{
		WQSchemas obj = new WQSchemas();

		obj.entities = getAll().stream().map(QEntity:: encode).collect(Collectors.toList());

		return obj;
	}


	public List<QEntity> getAll()
	{
		// Pre-emptively process all entities
		sessionFactory.getMetamodel().getEntities().stream().map(EntityType:: getJavaType).forEach(this :: get);

		return new ArrayList<>(entities.values());
	}
}
