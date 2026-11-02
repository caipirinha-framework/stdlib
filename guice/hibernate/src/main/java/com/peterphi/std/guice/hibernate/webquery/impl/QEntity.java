package com.peterphi.std.guice.hibernate.webquery.impl;

import com.google.common.reflect.TypeToken;
import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.guice.database.annotation.WebQueryPrivate;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.InvalidPropertyException;
import com.peterphi.std.guice.hibernate.webquery.impl.exception.PrivatePropertyUseRejected;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQEntitySchema;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Subgraph;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.graph.RootGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

public class QEntity
{
	private static final Logger log = LoggerFactory.getLogger(QEntity.class);

	private final Class<?> clazz;
	private String name;

	private Map<String, QProperty> properties = new HashMap<>();
	private Map<String, QRelation> relations = new HashMap<>();

	private List<QEntity> descendants = Collections.emptyList();

	// Only one of the following should be populated
	private EntityType<?> metamodelEntity;
	private EmbeddableType<?> metamodelEmbeddable;

	// Populated to let us get/set the ID of an entity dynamically
	private PropertyWrapper idProperty;

	/**
	 * Relations that are marked as having an eager fetch
	 */
	private Set<String> eagerRelations = new HashSet<>(0);

	/**
	 * A full list of the default relations (including child relations) to expand
	 */
	private Set<String> defaultExpand;
	/**
	 * A JPA2.1 EntityGraph version of defaultExpand
	 */
	private RootGraph defaultExpandGraph;


	/**
	 * Relations that don't have a QRelation (because they're not to an @Entity or @Embeddable)
	 */
	private Map<String, Attribute> nonEntityRelations = new HashMap<>(0);


	public QEntity(Class<?> clazz)
	{
		this.clazz = clazz;
	}


	public Class<?> getEntityClass()
	{
		return clazz;
	}


	public EntityType<?> getMetamodelEntity()
	{
		return this.metamodelEntity;
	}


	/**
	 * Parse an @Entity
	 *
	 * @param entityFactory
	 * @param metadata
	 * @param sessionFactory
	 */
	void parse(QEntityFactory entityFactory, EntityType<?> metadata, SessionFactoryImplementor sessionFactory)
	{
		this.metamodelEntity = metadata;
		this.name = metadata.getName();

		for (Attribute<?, ?> attribute : metadata.getAttributes())
		{
			parseFields(entityFactory, sessionFactory, null, attribute);
		}

		// Parse top-level properties

		// Add identifier property
		{
			if (!metadata.hasSingleIdAttribute())
				throw new IllegalArgumentException("@IdClass Entity not supported! " + metadata.getJavaType());

			Type idType = metadata.getIdType();

			switch (idType.getPersistenceType())
			{
				case BASIC:
					break; // No action necessary, will be processed like a normal field
				case EMBEDDABLE:
				{
					EmbeddableType<?> emb = (EmbeddableType<?>) idType;

					parseEmbeddable(entityFactory, sessionFactory, "id", emb);
					break;
				}
				default:
					throw new IllegalArgumentException("Cannot handle id type: " + idType.getPersistenceType() + ": " + idType);
			}
		}

		// Add links to descendants
		{
			final List<QEntity> descendants = entityFactory.getSubclasses(clazz);

			if (!descendants.isEmpty())
				this.descendants = descendants;
		}


		// Figure out the id method/field
		if (metamodelEntity != null && metamodelEntity.hasSingleIdAttribute())
		{
			final String idPropertyName = metamodelEntity.getId(metamodelEntity.getIdType().getJavaType()).getName();

			this.idProperty = new PropertyWrapper(clazz, idPropertyName);
		}
	}


	/**
	 * Parse an @Embeddable
	 *
	 * @param entityFactory
	 * @param sessionFactory
	 * @param prefix
	 * @param type
	 */
	public void parseEmbeddable(final QEntityFactory entityFactory,
	                            final SessionFactoryImplementor sessionFactory,
	                            final String prefix,
	                            final EmbeddableType<?> type)
	{
		this.metamodelEmbeddable = type;
		// Make sure the entity factory sees this embeddable
		entityFactory.getEmbeddable(type.getJavaType(), type);

		for (Attribute<?, ?> attribute : type.getAttributes())
		{
			parseFields(entityFactory, sessionFactory, prefix, attribute);
		}
	}


	private List<Field> getAllFields(Class clazz)
	{
		List<Field> fields = new ArrayList<>();

		while (clazz != null)
		{
			fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
			clazz = clazz.getSuperclass();
		}

		return fields;
	}


	protected boolean isEagerFetch(final Attribute<?, ?> attribute)
	{
		if (attribute.isCollection() || attribute.isAssociation())
		{
			final Member member = attribute.getJavaMember();

			if (member instanceof AnnotatedElement)
			{
				final AnnotatedElement el = (AnnotatedElement) member;

				final boolean hasEagerFetch = el.isAnnotationPresent(EagerFetch.class);

				final FetchType fetchType;
				if (el.isAnnotationPresent(OneToMany.class))
					fetchType = el.getAnnotation(OneToMany.class).fetch();
				else if (el.isAnnotationPresent(ManyToOne.class))
					fetchType = el.getAnnotation(ManyToOne.class).fetch();
				else if (el.isAnnotationPresent(OneToOne.class))
					fetchType = el.getAnnotation(OneToOne.class).fetch();
				else if (el.isAnnotationPresent(ElementCollection.class))
					fetchType = el.getAnnotation(ElementCollection.class).fetch();
				else if (el.isAnnotationPresent(ManyToMany.class))
					fetchType = el.getAnnotation(ManyToMany.class).fetch();
				else
					return false;

				if (hasEagerFetch && fetchType == FetchType.EAGER)
					log.warn(
							"ENTITY HAS @EagerFetch but also JPA fetch=EAGER annotation - will not be able to instruct Hibernate to suppress fetching this relation: {} {}",
							clazz,
							member.toString());

				return (hasEagerFetch || fetchType == FetchType.EAGER);
			}
		}

		return false;
	}


	public Set<String> getEagerFetch()
	{
		if (this.defaultExpand == null)
		{
			Set<String> populate = new HashSet<>();

			getEagerFetch(new HashSet<>(), new Stack<>(), populate);

			this.defaultExpand = Collections.unmodifiableSet(populate);
		}

		return this.defaultExpand;
	}


	protected void getEagerFetch(Set<QEntity> visited, Stack<String> path, Set<String> populate)
	{
		if (visited.contains(this))
			return;
		else
			visited.add(this);

		for (String eagerRelation : eagerRelations)
		{
			// Always add the relation, even if it's not a part of the QEntity parse structure (e.g. may be a Map or some other ElementCollection)
			if (path.isEmpty())
				populate.add(eagerRelation);
			else
				populate.add(StringUtils.join(path, '.') + "." + eagerRelation);

			// If this is a known relation then expand it further
			if (hasRelation(eagerRelation))
			{
				path.push(eagerRelation);

				final QEntity entity = getRelation(eagerRelation).getEntity();

				entity.getEagerFetch(visited, path, populate);

				path.pop();
			}
		}
	}


	private void parseFields(final QEntityFactory entityFactory,
	                         final SessionFactoryImplementor sessionFactory,
	                         final String prefix,
	                         final Attribute<?, ?> attribute)
	{
		final String name = attribute.getName();
		//final jakarta.persistence.metamodel.Type<?> type;
		final Class<?> clazz;
		final boolean isCollection;
		final boolean nullable;
		final boolean privateField;

		if (attribute.getJavaMember() instanceof Method m)
		{
			privateField = m.isAnnotationPresent(WebQueryPrivate.class);
		}
		else if (attribute.getJavaMember() instanceof Field f)
		{
			privateField = f.isAnnotationPresent(WebQueryPrivate.class);
		}
		else
		{
			privateField = false;
		}


		// Transparently unwrap collections
		if (attribute.isCollection())
		{
			isCollection = true;
			nullable = false;

			//type = ((PluralAttribute<?, ?, ?>) attribute).getElementType();
			clazz = ((PluralAttribute<?, ?, ?>) attribute).getElementType().getJavaType();
		}
		else
		{
			isCollection = false;
			//type = attribute.getDeclaringType();
			nullable = attribute instanceof SingularAttribute<?,?> sa && sa.isOptional();

			final Class<?> baseJavaType = attribute.getJavaType();

			if (baseJavaType == Enum.class) {
				// Hibernate has unhelpfully told us this is an enum without giving us the underlying type
				// We need to inspect the Member to figure out the actual Java type
				if (attribute.getJavaMember() instanceof Method m)
				{
					clazz = TypeToken.of(this.clazz).resolveType(m.getGenericReturnType()).getRawType();
				}
				else if (attribute.getJavaMember() instanceof Field f)
				{
					clazz = TypeToken.of(this.clazz).resolveType(f.getGenericType()).getRawType();
				}
				else {
					throw new IllegalArgumentException("Unsupported member type: " + attribute.getJavaMember().getClass());
				}

				// A raw Enum without a concrete type cannot be represented in a WebQuery schema.
				if (clazz == Enum.class)
					throw new IllegalArgumentException("Encountered Enum type without further type information, cannot proceed. " + attribute.getJavaMember());
			}
			else {
				clazz = baseJavaType;
			}
		}

		// TODO is it also meaningful to add the parent composite type as a field too?
		// TODO if not we should have this as a separate if condition
		if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED && !isCollection)
		{
			SingularAttribute<?, ?> attr = (SingularAttribute<?, ?>) attribute;
			EmbeddableType<?> ct = (EmbeddableType<?>) attr.getType();

			parseEmbeddable(entityFactory, sessionFactory, prefix, ct);
		}
		else if (isCollection || attribute.isAssociation())
		{
			final boolean isEagerFetch = isEagerFetch(attribute);

			final QRelation relation;
			if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.EMBEDDED &&
			    attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.BASIC)
			{
				if (attribute.getPersistentAttributeType() != Attribute.PersistentAttributeType.ELEMENT_COLLECTION)
				{
					relation = new QRelation(this, prefix, name, entityFactory.get(clazz), nullable, isEagerFetch, isCollection);
				}
				else
				{
					PluralAttribute<?, ?, ?> plural = (PluralAttribute<?, ?, ?>) attribute;

					if (plural.getElementType().getPersistenceType() == Type.PersistenceType.EMBEDDABLE)
					{
						final EmbeddableType<?> ct = (EmbeddableType<?>) plural.getElementType();

						relation = new QRelation(this,
						                         prefix,
						                         name,
						                         entityFactory.getEmbeddable(ct.getJavaType(), ct),
						                         nullable,
						                         isEagerFetch,
						                         isCollection);
					}
					else if (plural.getElementType().getPersistenceType() == Type.PersistenceType.BASIC)
					{
						// Ignore this altogether. We should probably come up with a way of querying this relationship in the future
						relation = null;

						// Record the relation so we can use it in the future to determine if a relation is a collection for query optimisation
						nonEntityRelations.put(name, attribute);

						if (log.isDebugEnabled())
							log.debug("Ignoring BASIC ElementCollection {} of {} {}",
							          plural.getCollectionType(),
							          plural.getElementType().getJavaType(),
							          plural.getName());
					}
					else
					{
						throw new IllegalArgumentException("Cannot handle ElementCollection of " +
						                                   plural.getElementType().getJavaType() +
						                                   " - type " +
						                                   plural.getElementType().getPersistenceType());
					}
				}
			}
			else if (isCollection && attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED)
			{
				SingularAttribute<?, ?> attr = (SingularAttribute<?, ?>) attribute;
				EmbeddableType<?> ct = (EmbeddableType<?>) attr.getType();

				relation = new QRelation(this,
				                         prefix,
				                         name,
				                         entityFactory.getEmbeddable(clazz, ct),
				                         nullable,
				                         isEagerFetch,
				                         isCollection);
			}
			else
			{
				// N.B. this fallback catches collections we don't natively support but that we have some support for (i.e. for fetch joining against)
				// As result, we will not be able to record them as a QRelation, but we can record them as a non-entity relation
				relation = null;

				// Record the relation so we can use it in the future to determine if a relation is a collection for query optimisation
				nonEntityRelations.put(name, attribute);

				if (log.isDebugEnabled())
					log.debug("Unknown Collection type: {} {} with name {} within {} - ignoring",
					          attribute.getPersistentAttributeType(),
					          attribute,
					          name,
					          clazz);
			}

			if (relation != null)
			{
				relations.put(name, relation);

				// Set up a special property to allow constraining the collection size
				if (isCollection)
					properties.put(name + ":size", new QSizeProperty(relations.get(name)));
			}

			if (isEagerFetch)
			{
				this.eagerRelations.add(attribute.getName());
			}
		}
		else
		{
			final String newPrefix;

			if (prefix != null)
				newPrefix = prefix + ":" + name;
			else
				newPrefix = name;

			properties.put(newPrefix, new QProperty(this, prefix, name, clazz, nullable, privateField));
		}
	}


	public String getName()
	{
		final Table table = this.clazz.getAnnotation(Table.class);
		final Entity entity = this.clazz.getAnnotation(Entity.class);

		if (table != null && !StringUtils.isBlank(table.name()))
			return table.name();
		else if (entity != null && !StringUtils.isBlank(entity.name()))
			return entity.name();
		else
			return this.name;
	}


	public String getDiscriminatorValue()
	{
		final DiscriminatorValue annotation = clazz.getAnnotation(DiscriminatorValue.class);

		if (annotation != null)
			return annotation.value();
		else
			return clazz.getName();
	}


	public Set<String> getPropertyNames()
	{
		return Collections.unmodifiableSet(this.properties.keySet());
	}


	public Set<String> getRelationNames()
	{
		return Collections.unmodifiableSet(this.relations.keySet());
	}


	public boolean hasProperty(String name)
	{
		return properties.containsKey(name);
	}


	public boolean hasRelation(String name)
	{
		return relations.containsKey(name);
	}


	public boolean hasNonEntityRelation(final String name)
	{
		return nonEntityRelations.containsKey(name);
	}


	public boolean isNonEntityRelationCollection(final String name)
	{
		if (!hasNonEntityRelation(name))
			throw new IllegalArgumentException("No such non-entity relation: " + name + " on " + this);
		else
			return nonEntityRelations.get(name).isCollection();
	}


	@Nullable
	public String getIdPropertyName()
	{
		if (this.idProperty != null)
			return this.idProperty.name;
		else
			return null;
	}


	/**
	 * Create a new instance of this entity, setting only the ID field
	 *
	 * @param id
	 *
	 * @return
	 */
	public Object newInstanceWithId(final Object id)
	{
		try
		{
			final Object o = clazz.newInstance();

			idProperty.set(o, id);

			return o;
		}
		catch (Throwable e)
		{
			throw new RuntimeException("Cannot create new instance of " +
			                           clazz +
			                           " with ID " +
			                           id +
			                           " (of type " +
			                           id.getClass() +
			                           ") populated!", e);
		}
	}


	public QProperty getProperty(final String name, final boolean permitSchemaPrivatePropertyAccess)
	{
		final QProperty property = properties.get(name);

		if (property != null)
		{
			if (permitSchemaPrivatePropertyAccess || !property.isSchemaPrivate())
				return property;
			else
				throw new PrivatePropertyUseRejected("WebQuery prohibited from accessing Private property " + name);
		}
		else
			throw new InvalidPropertyException("Unknown property " +
			                                   name +
			                                   " on " +
			                                   this.clazz.getSimpleName() +
			                                   ", expected one of " +
			                                   properties.keySet());
	}


	public QRelation getRelation(String name)
	{
		final QRelation relation = relations.get(name);

		if (relation != null)
			return relation;
		else
			throw new IllegalArgumentException("Unknown relation " +
			                                   name +
			                                   " on " +
			                                   this.clazz.getSimpleName() +
			                                   ", expected one of " +
			                                   relations.keySet());
	}


	public List<QEntity> getSubEntities()
	{
		return descendants;
	}


	public WQEntitySchema encode()
	{
		WQEntitySchema obj = new WQEntitySchema();

		obj.name = getName();
		obj.discriminator = getDiscriminatorValue();

		if (!this.descendants.isEmpty())
			obj.childEntityNames = descendants.stream().map(QEntity:: getName).toList();

		obj.properties = properties
				                 .values()
				                 .stream()
				                 .filter(prop -> !(prop instanceof QSizeProperty))
				                 .map(QProperty :: encode)
				                 .collect(Collectors.toList());

		obj.properties.addAll(relations.values().stream().map(QRelation :: encode).toList());

		return obj;
	}


	/**
	 * Return true if the underlying entity class is abstract
	 *
	 * @return
	 */
	public boolean isEntityClassAbstract()
	{
		return Modifier.isAbstract(clazz.getModifiers());
	}


	@Override
	public String toString()
	{
		return "QEntity{" +
		       "clazz=" +
		       clazz +
		       ", name='" +
		       name +
		       '\'' +
		       ", properties=" +
		       properties.keySet() +
		       ", relations=" +
		       relations.keySet() +
		       ", defaultFetch=" +
		       defaultExpand +
		       '}';
	}


	public QEntity getSubEntity(final String discriminator)
	{
		if (StringUtils.equals(getDiscriminatorValue(), discriminator))
			return this;
		else
			for (QEntity entity : getSubEntities())
				if (StringUtils.equals(entity.getDiscriminatorValue(), discriminator))
					return entity;

		throw new IllegalArgumentException("Unknown subclass with discriminator: " + discriminator);
	}


	public QEntity getCommonSubclass(final List<String> discriminators)
	{
		Map<Class, QEntity> entities = discriminators
				                               .stream()
				                               .map(this :: getSubEntity)
				                               .collect(Collectors.toMap(e -> e.clazz, e -> e));

		Collection<Class> classes = entities.keySet();

		for (Class potentialSuperclass : classes)
		{
			boolean suitableForAll = true;
			for (Class test : classes)
			{
				suitableForAll = suitableForAll && test.isAssignableFrom(potentialSuperclass);
			}

			// If this is a suitable superclass for all classes we have tested then we have a winner!
			if (suitableForAll)
				return entities.get(potentialSuperclass);
		}

		// Found no more specific subclass
		return this;
	}


	/**
	 * Build or return the default Entity Graph that represents defaultExpand
	 *
	 * @param session
	 *
	 * @return
	 */
	public RootGraph getDefaultGraph(final Session session)
	{
		if (this.defaultExpandGraph == null)
		{
			final RootGraph<?> graph = session.createEntityGraph(clazz);

			populateGraph(graph, getEagerFetch());

			this.defaultExpandGraph = graph;

			return graph;
		}
		else
		{
			return this.defaultExpandGraph;
		}
	}


	/**
	 * Creates an EntityGraph representing the
	 *
	 * @param graph
	 * @param fetches
	 */
	private void populateGraph(final RootGraph<?> graph, final Set<String> fetches)
	{
		Map<String, Subgraph<?>> created = new HashMap<>();

		for (String fetch : fetches)
		{
			final String[] parts = StringUtils.split(fetch, '.');

			// Starts of null (meaning parent is the root graph), updated as we go through path segments to refer to the last path segment
			Subgraph<?> parent = null;

			// Iterate over the path segments, adding attributes subgraphs
			for (int i = 0; i < parts.length; i++)
			{
				final String path = StringUtils.join(parts, '.', 0, i + 1);
				final boolean isLeaf = (i == parts.length - 1);

				final Subgraph<?> existing = created.get(path);

				if (existing == null)
				{
					if (parent == null)
					{
						try
						{
							// Relation under root
							graph.addAttributeNodes(parts[i]);

							// Only set up a subgraph if this is not a leaf node
							// This prevents us trying to add a Subgraph for a List of Embeddable
							if (!isLeaf)
								parent = graph.addSubgraph(parts[i]);
						}
						catch (IllegalArgumentException e)
						{
							throw new IllegalArgumentException("Error adding graph member " +
							                                   parts[i] +
							                                   " with isLeaf=" +
							                                   isLeaf +
							                                   " to root as part of " +
							                                   fetch, e);
						}
					}
					else
					{
						try
						{
							// Relation under parent
							parent.addAttributeNodes(parts[i]);

							// Only set up a subgraph if this is not a leaf node
							// This prevents us trying to add a Subgraph for a List of Embeddable
							if (!isLeaf)
								parent = parent.addSubgraph(parts[i]);
						}
						catch (IllegalArgumentException e)
						{
							throw new IllegalArgumentException("Error adding graph member " +
							                                   parts[i] +
							                                   " with isLeaf=" +
							                                   isLeaf +
							                                   " as part of " +
							                                   fetch +
							                                   " with parent.class=" +
							                                   parent.getClassType(), e);
						}
					}

					if (!isLeaf)
						created.put(path, parent);
				}
				else
				{
					// Already seen this graph node before
					parent = existing;
				}
			}
		}
	}
}
