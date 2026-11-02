package com.peterphi.std.guice.hibernate.webquery.impl;

import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQDataType;
import com.peterphi.std.guice.restclient.jaxb.webqueryschema.WQEntityProperty;

import java.util.ArrayList;

public class QProperty
{
	protected final QEntity entity;
	protected final String name;
	protected final Class<?> clazz;
	protected final boolean nullable;
	protected final boolean schemaPrivate;

	public QProperty(final QEntity entity, final String prefix, final String name, final Class<?> clazz, final boolean nullable, final boolean schemaPrivate)
	{
		this.entity = entity;

		if (prefix != null)
			this.name = prefix + ":" + name;
		else
			this.name = name;

		this.clazz = clazz;
		this.nullable = nullable;
		this.schemaPrivate= schemaPrivate;
	}


	/**
	 * Returns whether this property should be considered part of a private schema. The intention here is to prohibit non-local (or at least non-privileged) queries from using this property.<br />
	 *
	 * @return
	 */
	public boolean isSchemaPrivate()
	{
		return this.schemaPrivate;
	}


	/**
	 * Returns whether this property should be omitted from generated search schemas. This is different from {@link #isSchemaPrivate()} in that it is not intended for use as a way to access control particular fields
	 *
	 * @return
	 */
	public boolean isOmitFromSchemaDoc()
	{
		return this.isSchemaPrivate();
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
