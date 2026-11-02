package com.peterphi.std.guice.hibernate.webquery.impl;

import com.google.common.base.MoreObjects;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQDataType;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQEntityProperty;

import java.util.Arrays;
import java.util.List;

public class QRelation
{
	private final QEntity owner;
	private final String name;
	private final QEntity entity;
	private final boolean nullable;
	private final boolean eager;
	private final boolean collection;
	private final boolean schemaPrivate;
	private final List<String> persistentPath;
	private final boolean alias;
	private final boolean identifierShortcut;


	public QRelation(final QEntity owner,
	                 final String prefix,
	                 final String name,
	                 final QEntity entity,
	                 final boolean nullable,
	                 final boolean eager,
	                 final boolean collection)
	{
		this(owner, prefix == null ? name : prefix + "." + name, entity, nullable, eager, collection, false,
		     Arrays.asList((prefix == null ? name : prefix + "." + name).split("[.:]")), false, false);
	}

	QRelation(final QEntity owner, final String name, final QEntity entity, final boolean nullable,
	          final boolean eager, final boolean collection, final boolean schemaPrivate,
	          final List<String> persistentPath, final boolean alias, final boolean identifierShortcut)
	{
		this.owner = owner;
		this.name = name;
		this.entity = entity;
		this.nullable = nullable;
		this.eager = eager;
		this.collection = collection;
		this.schemaPrivate = schemaPrivate;
		this.persistentPath = List.copyOf(persistentPath);
		this.alias = alias;
		this.identifierShortcut = identifierShortcut;
	}

	public boolean canUseIdentifierShortcut()
	{
		return identifierShortcut;
	}

	public List<String> getPersistentPath()
	{
		return persistentPath;
	}

	public boolean isSchemaPrivate()
	{
		return schemaPrivate || owner.isPathPrivate(persistentPath);
	}

	boolean isAlias()
	{
		return alias;
	}


	/**
	 * Get the owning entity of the relation
	 *
	 * @return
	 */
	public QEntity getOwner()
	{
		return owner;
	}


	/**
	 * Get the relation name on the owner
	 *
	 * @return
	 */
	public String getName()
	{
		return name;
	}


	/**
	 * Get the entity for the relation destination
	 *
	 * @return
	 */
	public QEntity getEntity()
	{
		return entity;
	}


	/**
	 * Get the nullability of this relationship
	 *
	 * @return
	 */
	public boolean isNullable()
	{
		return nullable;
	}


	public boolean isEager()
	{
		return eager;
	}


	public boolean isCollection()
	{
		return collection;
	}


	@Override
	public String toString()
	{
		return MoreObjects
				       .toStringHelper(this)
				       .add("owner", owner)
				       .add("name", name)
				       .add("entity", entity)
				       .add("nullable", nullable)
				       .add("eager", eager)
				       .add("collection", collection)
				       .toString();
	}


	public WQEntityProperty encode()
	{
		WQEntityProperty obj = new WQEntityProperty();

		obj.name = this.getName();
		obj.nullable = this.isNullable();
		obj.type = WQDataType.ENTITY;
		obj.relation = getEntity().getName();
		obj.relationCollection = isCollection();

		return obj;
	}
}
