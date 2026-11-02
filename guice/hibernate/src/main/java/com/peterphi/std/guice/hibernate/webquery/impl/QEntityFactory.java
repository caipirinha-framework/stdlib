package com.peterphi.std.guice.hibernate.webquery.impl;

import com.google.common.reflect.TypeToken;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Singleton
public class QEntityFactory
{
	private static final Logger log = LoggerFactory.getLogger(QEntityFactory.class);

	private final Map<Class<?>, QEntity> entities = new HashMap<>();
	private final SessionFactoryImplementor sessionFactory;
	private final Map<TypeToken<?>, QEntity> embeddables = new HashMap<>();
	private int constructionDepth;


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


	public synchronized List<QEntity> getSubclasses(Class<?> superclass)
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


	public synchronized <T> QEntity get(Class<T> clazz)
	{
		QEntity existing = entities.get(clazz);
		if (existing != null)
			return existing;
		return construct(() -> {
			EntityType<T> metadata = sessionFactory.getMetamodel().entity(clazz);
			QEntity entity = new QEntity(clazz);
			// Register before traversing relations to allow cyclic entity graphs.
			entities.put(clazz, entity);
			entity.parse(this, metadata, sessionFactory);
			return entity;
		});
	}

	public synchronized QEntity getEmbeddable(final Class clazz, final EmbeddableType type)
	{
		return getEmbeddable(TypeToken.of(clazz), type);
	}

	public synchronized QEntity getEmbeddable(final TypeToken<?> context, final EmbeddableType<?> type)
	{
		QEntity existing = embeddables.get(context);
		if (existing != null)
			return existing;
		return construct(() -> {
			QEntity entity = new QEntity(context);
			embeddables.put(context, entity);
			entity.parseEmbeddable(this, sessionFactory, null, type);
			return entity;
		});
	}

	private QEntity construct(Supplier<QEntity> builder)
	{
		boolean outermost = constructionDepth++ == 0;
		Set<Class<?>> previousEntities = outermost ? new HashSet<>(entities.keySet()) : null;
		Set<TypeToken<?>> previousEmbeddables = outermost ? new HashSet<>(embeddables.keySet()) : null;
		try
		{
			return builder.get();
		}
		catch (RuntimeException | Error failure)
		{
			// A cyclic relation may reference another newly constructed, incomplete entity.
			// Roll back the entire traversal, not just the entry whose parsing failed.
			if (outermost)
			{
				entities.keySet().retainAll(previousEntities);
				embeddables.keySet().retainAll(previousEmbeddables);
			}
			throw failure;
		}
		finally
		{
			constructionDepth--;
		}
	}


	public WQSchemas encode()
	{
		WQSchemas obj = new WQSchemas();

		obj.entities = getAll().stream().map(QEntity:: encode).collect(Collectors.toList());

		return obj;
	}


	public synchronized List<QEntity> getAll()
	{
		// Pre-emptively process all entities
		sessionFactory.getMetamodel().getEntities().stream().map(EntityType:: getJavaType).forEach(this :: get);

		List<QEntity> all = new ArrayList<>(entities.values());
		all.addAll(embeddables.values());
		return all;
	}
}
