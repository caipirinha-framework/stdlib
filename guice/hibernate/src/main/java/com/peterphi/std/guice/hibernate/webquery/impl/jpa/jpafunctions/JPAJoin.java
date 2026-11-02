package com.peterphi.std.guice.hibernate.webquery.impl.jpa.jpafunctions;

import com.peterphi.std.guice.hibernate.webquery.impl.QEntity;
import com.peterphi.std.guice.hibernate.webquery.impl.QProperty;
import com.peterphi.std.guice.hibernate.webquery.impl.QRelation;
import com.peterphi.std.guice.hibernate.webquery.impl.QSizeProperty;
import org.apache.commons.lang.StringUtils;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;

public class JPAJoin
{
	private final CriteriaBuilder builder;
	private final QEntity entity;
	private final From<?, ?> root;
	private final boolean collection;


	public JPAJoin(final CriteriaBuilder builder, final QEntity entity, final From<?, ?> root, final boolean collection)
	{
		this.builder = builder;
		this.entity = entity;
		this.root = root;
		this.collection = collection;
	}


	public JPAJoin(final CriteriaBuilder builder, final QRelation relation, final Join<?, ?> join, final boolean collection)
	{
		this(builder, relation.getEntity(), join, collection);
	}


	public Expression<?> getJoinExpression()
	{
		return root;
	}

	public JPAJoin withRoot(final From<?, ?> replacement)
	{
		return new JPAJoin(builder, entity, replacement, collection);
	}


	public boolean isCollection()
	{
		return collection;
	}


	public JPAJoin join(final String relation)
	{
		return join(relation, false);
	}

	public JPAJoin join(final String relation, final boolean permitSchemaPrivate)
	{
		final QRelation rel = entity.getRelation(relation, permitSchemaPrivate);

		final JoinType joinType;

		// For non-nullable non-collection joins, use an INNER join instead of a LEFT join
		if (!rel.isNullable() && !rel.isCollection())
			joinType = JoinType.INNER;
		else
			joinType = JoinType.LEFT;

		From<?, ?> joined = root;
		for (String part : rel.getPersistentPath())
			joined = joined.join(part, joinType);
		return new JPAJoin(builder, rel.getEntity(), joined, collection || rel.isCollection());
	}


	public Class<?> getPropertyType(final String name, final boolean permitSchemaPrivate)
	{
		if (StringUtils.equals(name, "class"))
			return Class.class;
		if (StringUtils.equals(name, "entity"))
			return root.getJavaType();
		return entity.getProperty(name, permitSchemaPrivate).getClazz();
	}

	/** Entity ordering is identifier ordering; whole-entity projections have separate semantics. */
	public void assertOrderingAccessible(final String name, final boolean permitSchemaPrivate)
	{
		if (StringUtils.equals(name, "entity"))
			entity.assertIdentifierAccessible(permitSchemaPrivate);
		else if (!StringUtils.equals(name, "class"))
			entity.getProperty(name, permitSchemaPrivate);
	}

	public Expression<?> orderingProperty(final String name, final boolean permitSchemaPrivate)
	{
		assertOrderingAccessible(name, permitSchemaPrivate);
		if (StringUtils.equals(name, "entity") && entity.getIdPropertyName() != null)
			return root.get(entity.getIdPropertyName());
		return property(name, permitSchemaPrivate);
	}

	public Expression<?> property(final String name, final boolean permitSchemaPrivate)
	{
		if (StringUtils.equals(name, "class"))
			return root.type();
		if (StringUtils.equals(name, "entity"))
			return root;

		return propertyExpression(entity.getProperty(name, permitSchemaPrivate), root);
	}

	private Expression<?> propertyExpression(final QProperty property, final From<?, ?> owner)
	{
		QRelation navigation = property.getNavigationRelation();
		if (navigation != null)
		{
			QProperty nested = property.getNestedProperty();
			boolean identifierShortcut = navigation.canUseIdentifierShortcut() && nested.getNavigationRelation() == null && !(nested instanceof QSizeProperty) &&
			                             nested.getPersistentPath().getFirst().equals(navigation.getEntity().getIdPropertyName());
			if (identifierShortcut)
			{
				Path<?> path = owner;
				for (String part : property.getPersistentPath())
					path = path.get(part);
				return path;
			}
			// Non-ID navigation needs a LEFT join to retain rows whose to-one
			// prefix is absent. Implicit Path.get navigation uses INNER joins.
			From<?, ?> joined = owner;
			for (String part : navigation.getPersistentPath())
				joined = joined.join(part, JoinType.LEFT);
			return propertyExpression(nested, joined);
		}
		if (property instanceof QSizeProperty)
		{
			// Implicit navigation makes nullable to-one prefixes INNER joins. Match
			// ordinary dotted navigation so an absent parent still has an empty collection.
			From<?, ?> parent = owner;
			var parts = property.getPersistentPath();
			for (int i = 0; i < parts.size() - 1; i++)
				parent = parent.join(parts.get(i), JoinType.LEFT);
			return builder.size((Expression) parent.get(parts.getLast()));
		}
		Path<?> path = owner;
		for (String part : property.getPersistentPath())
			path = path.get(part);
		return path;
	}
}
