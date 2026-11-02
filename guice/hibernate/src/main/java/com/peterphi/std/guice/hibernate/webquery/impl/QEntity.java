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
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.ModelPartContainer;
import org.hibernate.metamodel.mapping.internal.ToOneAttributeMapping;

import javax.annotation.Nullable;
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
	private final Class<?> clazz;
	private final TypeToken<?> javaType;
	private String name;

	private final Map<String, QProperty> properties = new HashMap<>();
	private final Map<String, QRelation> relations = new HashMap<>();
	private final Set<String> directAttributeNames = new HashSet<>();
	private final Map<String, List<String>> flattenedAliases = new HashMap<>();
	private final Set<String> ambiguousAliases = new HashSet<>();
	private boolean declaredIdentifierPrivate;
	// Keep declared privacy independent of the queried type's polymorphic domain.
	// In particular, an ancestor's conservative policy must not taint a public sibling.
	private final Set<List<String>> privatePaths = new HashSet<>();

	private List<QEntity> descendants = Collections.emptyList();

	// Embeddables have no entity metamodel.
	private EntityType<?> metamodelEntity;

	// Populated to let us get/set the ID of an entity dynamically
	private PropertyWrapper idProperty;

	/**
	 * Relations that are marked as having an eager fetch
	 */
	private final Set<String> eagerRelations = new HashSet<>(0);

	/**
	 * A full list of the default relations (including child relations) to expand
	 */
	private volatile Set<String> defaultExpand;
	/**
	 * Entity graph for the default fetch paths.
	 */
	private volatile RootGraph defaultExpandGraph;


	/**
	 * Relations that don't have a QRelation (because they're not to an @Entity or @Embeddable)
	 */
	private record NonEntityRelation(Attribute<?, ?> attribute, List<String> path, boolean schemaPrivate) {}
	private final Map<String, NonEntityRelation> nonEntityRelations = new HashMap<>();


	public QEntity(Class<?> clazz)
	{
		this(TypeToken.of(clazz));
	}

	QEntity(TypeToken<?> javaType)
	{
		this.javaType = javaType;
		this.clazz = javaType.getRawType();
		this.name = javaType.toString();
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
		metadata.getAttributes().forEach(attribute -> directAttributeNames.add(attribute.getName()));

		for (Attribute<?, ?> attribute : metadata.getAttributes())
		{
			parseFields(entityFactory, sessionFactory, javaType, List.of(), false, attribute);
		}

		if (!metadata.hasSingleIdAttribute())
			throw new IllegalArgumentException("@IdClass Entity not supported! " + metadata.getJavaType());

		// Add links to descendants
		{
			final List<QEntity> descendants = entityFactory.getSubclasses(clazz);

			if (!descendants.isEmpty())
				this.descendants = descendants;
		}


		// Figure out the id method/field
		if (metamodelEntity != null && metamodelEntity.hasSingleIdAttribute())
		{
			final Attribute<?, ?> identifier = metamodelEntity.getId(metamodelEntity.getIdType().getJavaType());
			final String idPropertyName = identifier.getName();
			this.declaredIdentifierPrivate = new PersistentAttributeMetadata(javaType, identifier).getAnnotation(WebQueryPrivate.class) != null ||
			                         privatePaths.stream().anyMatch(path -> path.getFirst().equals(idPropertyName));
			if (!properties.containsKey(idPropertyName) && metamodelEntity.getIdType().getPersistenceType() == Type.PersistenceType.EMBEDDABLE)
			{
				// Keep whole-ID ordering/reference paths available without exporting an
				// embeddable value as though it were a primitive schema property.
				properties.put(idPropertyName, new QProperty(this, idPropertyName, identifier.getJavaType(), false,
				                                            declaredIdentifierPrivate, List.of(idPropertyName), true));
			}
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
		if (metamodelEntity == null && prefix == null)
			type.getAttributes().forEach(attribute -> directAttributeNames.add(attribute.getName()));
		parseEmbeddable(entityFactory, sessionFactory, javaType,
		                prefix == null ? List.of() : Arrays.asList(prefix.split(":")), false, type);
	}

	private void parseEmbeddable(final QEntityFactory entityFactory,
	                             final SessionFactoryImplementor sessionFactory,
	                             final TypeToken<?> context,
	                             final List<String> path,
	                             final boolean privateContainer,
	                             final EmbeddableType<?> type)
	{
		for (Attribute<?, ?> attribute : type.getAttributes())
			parseFields(entityFactory, sessionFactory, context, path, privateContainer, attribute);
	}

	protected boolean isEagerFetch(final Attribute<?, ?> attribute)
	{
		return isEagerFetch(new PersistentAttributeMetadata(javaType, attribute));
	}

	private boolean isEagerFetch(final PersistentAttributeMetadata metadata)
	{
		boolean eager = metadata.getAnnotation(EagerFetch.class) != null;
		FetchType fetchType = FetchType.LAZY;
		if (metadata.getAnnotation(OneToMany.class) != null)
			fetchType = metadata.getAnnotation(OneToMany.class).fetch();
		else if (metadata.getAnnotation(ManyToOne.class) != null)
			fetchType = metadata.getAnnotation(ManyToOne.class).fetch();
		else if (metadata.getAnnotation(OneToOne.class) != null)
			fetchType = metadata.getAnnotation(OneToOne.class).fetch();
		else if (metadata.getAnnotation(ElementCollection.class) != null)
			fetchType = metadata.getAnnotation(ElementCollection.class).fetch();
		else if (metadata.getAnnotation(ManyToMany.class) != null)
			fetchType = metadata.getAnnotation(ManyToMany.class).fetch();
		return eager || fetchType == FetchType.EAGER;
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
			QRelation relation = relations.values().stream()
			                              .filter(candidate -> !candidate.isAlias() && String.join(".", candidate.getPersistentPath()).equals(eagerRelation))
			                              .findFirst().orElse(null);
			if (relation != null)
			{
				path.push(eagerRelation);

				final QEntity entity = relation.getEntity();

				entity.getEagerFetch(visited, path, populate);

				path.pop();
			}
		}
		visited.remove(this);
	}


	private void parseFields(final QEntityFactory entityFactory,
	                         final SessionFactoryImplementor sessionFactory,
	                         final TypeToken<?> context,
	                         final List<String> parentPath,
	                         final boolean privateContainer,
	                         final Attribute<?, ?> attribute)
	{
		final PersistentAttributeMetadata metadata = new PersistentAttributeMetadata(context, attribute);
		final List<String> path = new ArrayList<>(parentPath);
		path.add(attribute.getName());
		final String name = String.join(":", path);
		final boolean privateField = privateContainer || metadata.getAnnotation(WebQueryPrivate.class) != null;
		if (privateField)
			privatePaths.add(List.copyOf(path));
		final boolean collection = attribute.isCollection();
		final boolean nullable = attribute instanceof SingularAttribute<?, ?> singular && singular.isOptional();
		final TypeToken<?> valueType = collection
		                              ? metadata.getElementType((PluralAttribute<?, ?, ?>) attribute)
		                              : metadata.getValueType();

		if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED && !collection)
		{
			EmbeddableType<?> embedded = (EmbeddableType<?>) ((SingularAttribute<?, ?>) attribute).getType();
			parseEmbeddable(entityFactory, sessionFactory, valueType, path, privateField, embedded);
		}
		else if (collection || attribute.isAssociation())
		{
			final boolean eager = isEagerFetch(metadata);
			QEntity related = null;
			if (collection && ((PluralAttribute<?, ?, ?>) attribute).getElementType().getPersistenceType() == Type.PersistenceType.EMBEDDABLE)
			{
				EmbeddableType<?> embedded = (EmbeddableType<?>) ((PluralAttribute<?, ?, ?>) attribute).getElementType();
				related = entityFactory.getEmbeddable(valueType, embedded);
			}
			else if (attribute.isAssociation())
			{
				// The persistence target may differ from the Java declaration, for
				// example Set<Interface> with targetEntity=ConcreteEntity.class.
				Type<?> targetType = collection ? ((PluralAttribute<?, ?, ?>) attribute).getElementType()
				                                : ((SingularAttribute<?, ?>) attribute).getType();
				related = entityFactory.get(targetType.getJavaType());
			}

			if (related != null)
			{
				boolean identifierShortcut = !collection && canUseIdentifierShortcut(sessionFactory, path);
				addRelation(new QRelation(this, name, related, nullable, eager, collection, privateField, path, false, identifierShortcut));
				if (path.size() > 1)
					addRelation(new QRelation(this, attribute.getName(), related, nullable, eager, collection, privateField, path, true, identifierShortcut));
			}
			else
			{
				NonEntityRelation nonEntity = new NonEntityRelation(attribute, List.copyOf(path), privateField);
				nonEntityRelations.put(name, nonEntity);
				if (path.size() > 1 && registerAlias(attribute.getName(), path))
					nonEntityRelations.put(attribute.getName(), nonEntity);
			}
			if (eager)
				eagerRelations.add(String.join(".", path));
		}
		else
		{
			addProperty(new QProperty(this, name, valueType.getRawType(), nullable, privateField, path, false));
			// Preserve unambiguous legacy flattened names; qualified paths always remain available.
			if (path.size() > 1)
				addProperty(new QProperty(this, attribute.getName(), valueType.getRawType(), nullable, privateField, path, true));
		}
	}

	private boolean canUseIdentifierShortcut(SessionFactoryImplementor factory, List<String> path)
	{
		if (metamodelEntity == null)
			return false;
		ModelPart part = factory.getMappingMetamodel().getEntityDescriptor(clazz);
		for (String name : path)
		{
			if (!(part instanceof ModelPartContainer container))
				return false;
			part = container.findSubPart(name, null);
		}
		// Inverse associations and property-ref mappings cannot read the target ID
		// from the owner's local FK. Retain LEFT navigation in those cases.
		return part instanceof ToOneAttributeMapping toOne && toOne.isFkOptimizationAllowed() && toOne.isReferenceToPrimaryKey();
	}

	private boolean registerAlias(String name, List<String> path)
	{
		if (directAttributeNames.contains(name) || ambiguousAliases.contains(name))
			return false;
		List<String> existing = flattenedAliases.putIfAbsent(name, List.copyOf(path));
		if (existing == null || existing.equals(path))
			return true;
		ambiguousAliases.add(name);
		properties.remove(name);
		properties.remove(name + ":size");
		relations.remove(name);
		nonEntityRelations.remove(name);
		return false;
	}

	private void addProperty(QProperty property)
	{
		if (!property.isAlias() || registerAlias(property.getName(), property.getPersistentPath()))
			properties.put(property.getName(), property);
	}

	private void addRelation(QRelation relation)
	{
		if (!relation.isAlias() || registerAlias(relation.getName(), relation.getPersistentPath()))
		{
			relations.put(relation.getName(), relation);
			if (relation.isCollection())
				properties.put(relation.getName() + ":size", new QSizeProperty(relation));
		}
	}

	public void assertIdentifierAccessible(boolean permitSchemaPrivate)
	{
		if (!permitSchemaPrivate && isIdentifierPrivate())
			throw new PrivatePropertyUseRejected("WebQuery prohibited from accessing Private identifier on " + clazz.getName());
	}

	private boolean isIdentifierPrivate()
	{
		return declaredIdentifierPrivate || descendants.stream().anyMatch(child -> child != this && child.declaredIdentifierPrivate);
	}

	/**
	 * A query of an entity type can return any concrete descendant. A named path
	 * is therefore private if any such descendant declares that path (or one of
	 * its containers) private. Read declarations only: following effective flags
	 * would introduce cycles and incorrectly restrict public sibling queries.
	 * Checks run after factory construction; no partial policy is cached while
	 * the factory is resolving cyclic associations.
	 */
	boolean isPathPrivate(List<String> path)
	{
		if (path.size() == 1 && path.getFirst().equals(getIdPropertyName()) && isIdentifierPrivate())
			return true;
		if (declaresPrivatePath(path))
			return true;
		return descendants.stream().anyMatch(child -> child != this && child.declaresPrivatePath(path));
	}

	private boolean declaresPrivatePath(List<String> path)
	{
		return privatePaths.stream().anyMatch(prefix -> path.size() >= prefix.size() && path.subList(0, prefix.size()).equals(prefix));
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
			return nonEntityRelations.get(name).attribute().isCollection();
	}


	/**
	 * Validates a requested fetch path and translates embedded/legacy aliases to
	 * Hibernate's physical attribute path. Private checks apply at every hop.
	 */
	public String resolveFetchPath(String requested, boolean permitSchemaPrivate)
	{
		String path = requested.replace(':', '.');
		List<QRelation> candidates = relations.values().stream()
		                                     .sorted(java.util.Comparator.comparingInt((QRelation relation) -> relation.getName().length()).reversed())
		                                     .toList();
		for (QRelation relation : candidates)
		{
			String publicPath = relation.getName().replace(':', '.');
			if (path.equals(publicPath) || path.startsWith(publicPath + "."))
			{
				getRelation(relation.getName(), permitSchemaPrivate);
				String physicalPath = String.join(".", relation.getPersistentPath());
				if (path.equals(publicPath))
					return physicalPath;
				return physicalPath + "." + relation.getEntity().resolveFetchPath(path.substring(publicPath.length() + 1), permitSchemaPrivate);
			}
		}
		for (Map.Entry<String, NonEntityRelation> entry : nonEntityRelations.entrySet())
		{
			String publicPath = entry.getKey().replace(':', '.');
			if (path.equals(publicPath) || path.startsWith(publicPath + "."))
			{
				if (!permitSchemaPrivate && (entry.getValue().schemaPrivate() || isPathPrivate(entry.getValue().path())))
					throw new PrivatePropertyUseRejected("WebQuery prohibited from fetching Private property " + requested);
				if (path.equals(publicPath))
					return String.join(".", entry.getValue().path());
				throw new InvalidPropertyException("Cannot fetch through a basic collection: " + requested);
			}
		}
		QProperty property = properties.get(requested);
		if (property == null)
			property = properties.get(path.replace('.', ':'));
		if (property != null)
		{
			getProperty(property.getName(), permitSchemaPrivate);
			return String.join(".", property.getPersistentPath());
		}
		throw new InvalidPropertyException("Unknown fetch path " + requested + " on " + clazz.getName());
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
		{
			// Colon navigation also supports to-one foreign-key shortcuts, e.g. job:id.
			// Resolve the longest relation prefix so embedded forms such as info:job:id
			// retain their meaning without introducing an unnecessary SQL join.
			for (QRelation candidate : relations.values().stream()
			                                  .sorted(java.util.Comparator.comparingInt((QRelation relation) -> relation.getName().length()).reversed())
			                                  .toList())
			{
				String prefix = candidate.getName() + ":";
				if (!name.startsWith(prefix))
					continue;
				QRelation relation = getRelation(candidate.getName(), permitSchemaPrivatePropertyAccess);
				if (relation.isCollection())
					throw new InvalidPropertyException("Colon property navigation requires a to-one relation: " + name);
				QProperty nested = relation.getEntity().getProperty(name.substring(prefix.length()), permitSchemaPrivatePropertyAccess);
				List<String> physicalPath = new ArrayList<>(relation.getPersistentPath());
				physicalPath.addAll(nested.getPersistentPath());
				if (nested instanceof QSizeProperty size)
					return new QSizeProperty(this, name, relation, size, physicalPath);
				return new QProperty(this, name, relation, nested, physicalPath);
			}
			throw new InvalidPropertyException("Unknown property " + name + " on " + clazz.getSimpleName() + ", expected one of " + properties.keySet());
		}
	}


	public QRelation getRelation(String name, boolean permitSchemaPrivate)
	{
		QRelation relation = getRelation(name);
		if (!permitSchemaPrivate && relation.isSchemaPrivate())
			throw new PrivatePropertyUseRejected("WebQuery prohibited from accessing Private relation " + name);
		return relation;
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
				                 .filter(prop -> !prop.isOmitFromSchemaDoc())
				                 .map(QProperty :: encode)
				                 .collect(Collectors.toList());

		obj.properties.addAll(relations.values().stream().filter(relation -> !relation.isAlias() && !relation.isSchemaPrivate()).map(QRelation :: encode).toList());

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
