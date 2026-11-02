package com.peterphi.std.guice.hibernate.webquery.impl;

import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQDataType;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQEntityProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QProperty
{
	protected final QEntity entity;
	protected final String name;
	protected final Class<?> clazz;
	protected final boolean nullable;
	protected final boolean schemaPrivate;
	private final List<String> persistentPath;
	private final boolean alias;
	private final QRelation navigationRelation;
	private final QProperty nestedProperty;

	public QProperty(final QEntity entity, final String prefix, final String name, final Class<?> clazz, final boolean nullable, final boolean schemaPrivate)
	{
		this(entity, prefix == null ? name : prefix + ":" + name, clazz, nullable, schemaPrivate,
		     Arrays.asList((prefix == null ? name : prefix + ":" + name).split(":")), false);
	}

	QProperty(final QEntity entity, final String name, final Class<?> clazz, final boolean nullable,
	          final boolean schemaPrivate, final List<String> persistentPath, final boolean alias)
	{
		this(entity, name, clazz, nullable, schemaPrivate, persistentPath, alias, null, null);
	}

	QProperty(final QEntity entity, final String name, final QRelation navigation, final QProperty nested,
	          final List<String> persistentPath)
	{
		this(entity, name, nested.getClazz(), navigation.isNullable() || nested.isNullable(),
		     navigation.isSchemaPrivate() || nested.isSchemaPrivate(), persistentPath, true, navigation, nested);
	}

	private QProperty(final QEntity entity, final String name, final Class<?> clazz, final boolean nullable,
	                  final boolean schemaPrivate, final List<String> persistentPath, final boolean alias,
	                  final QRelation navigation, final QProperty nested)
	{
		this.entity = entity;
		this.name = name;
		this.clazz = clazz;
		this.nullable = nullable;
		this.schemaPrivate = schemaPrivate;
		this.persistentPath = List.copyOf(persistentPath);
		this.alias = alias;
		this.navigationRelation = navigation;
		this.nestedProperty = nested;
	}

	public QRelation getNavigationRelation()
	{
		return navigationRelation;
	}

	public QProperty getNestedProperty()
	{
		return nestedProperty;
	}

	public List<String> getPersistentPath()
	{
		return persistentPath;
	}

	boolean isAlias()
	{
		return alias;
	}


	/**
	 * Returns whether this property should be considered part of a private schema. The intention here is to prohibit non-local (or at least non-privileged) queries from using this property.<br />
	 *
	 * @return
	 */
	public boolean isSchemaPrivate()
	{
		return this.schemaPrivate || entity.isPathPrivate(persistentPath);
	}


	/**
	 * Returns whether this property should be omitted from generated search schemas. This is different from {@link #isSchemaPrivate()} in that it is not intended for use as a way to access control particular fields
	 *
	 * @return
	 */
	public boolean isOmitFromSchemaDoc()
	{
		return this.isSchemaPrivate() || alias;
	}

	public QEntity getEntity()
	{
		return entity;
	}


	public String getName()
	{
		return name;
	}


	public Class<?> getClazz()
	{
		return clazz;
	}


	public boolean isNullable()
	{
		return nullable;
	}


	public WQEntityProperty encode()
	{
		WQEntityProperty obj = new WQEntityProperty();

		obj.name = this.getName();
		obj.nullable = this.isNullable();
		obj.relation = null;
		obj.type = WQTypeHelper.translate(getClazz());

		if (obj.type == WQDataType.ENUM)
		{
			obj.enumType = clazz.getSimpleName();

			final Object[] consts = clazz.getEnumConstants();
			obj.enumValues = new ArrayList<>(consts.length);
			for (Object val : consts)
			{
				obj.enumValues.add(((Enum) val).name());
			}
		}

		return obj;
	}


	@Override
	public String toString()
	{
		return "QProperty{" +
		       "entity.name=" + entity.getName() +
		       ", name='" + name + '\'' +
		       ", clazz=" + clazz +
		       ", nullable=" + nullable +
		       '}';
	}
}
