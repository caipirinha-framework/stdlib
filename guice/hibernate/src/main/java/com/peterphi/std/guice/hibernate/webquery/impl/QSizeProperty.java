package com.peterphi.std.guice.hibernate.webquery.impl;

import java.util.List;

/**
 * A special type of property that refers to the size of a collection
 */
public class QSizeProperty extends QProperty
{
	protected final QRelation relation;


	public QSizeProperty(final QRelation relation)
	{
		super(relation.getOwner(), relation.getName() + ":size", Integer.class, false, relation.isSchemaPrivate(),
		      relation.getPersistentPath(), relation.isAlias());

		this.relation = relation;
	}

	QSizeProperty(final QEntity owner, final String name, final QRelation navigation,
	              final QSizeProperty nested, final List<String> path)
	{
		super(owner, name, navigation, nested, path);
		this.relation = nested.relation;
	}


	public boolean isOmitFromSchemaDoc()
	{
		return true; // Consumers must infer this field's existence from an ENTITY property with a collection (since that property has more rich type metadata)
	}

	public QRelation getRelation()
	{
		return relation;
	}


	@Override
	public String toString()
	{
		return "QSizeProperty{" +
		       "relation.name=" +
		       relation.getName() +
		       ", entity.name=" +
		       super.entity.getName() +
		       ", name='" +
		       super.name +
		       '\'' +
		       ", clazz=" +
		       super.clazz +
		       ", nullable=" +
		       super.nullable +
		       '}';
	}
}
