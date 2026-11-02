package com.peterphi.std.guice.hibernate.usertype;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.joda.time.LocalDate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;

public class JodaLocalDateUserType implements UserType, Comparator
{
	public static JodaLocalDateUserType INSTANCE = new JodaLocalDateUserType();


	@Override
	public int getSqlType()
	{
		return Types.DATE;
	}


	@Override
	public Class returnedClass()
	{
		return LocalDate.class;
	}


	@Override
	public boolean equals(Object x, Object y) throws HibernateException
	{
		if (x == y)
		{
			return true;
		}
		else if (x == null || y == null)
		{
			return false;
		}
		else
		{
			return x.equals(y);
		}
	}


	@Override
	public int hashCode(final Object x) throws HibernateException
	{
		return x.hashCode();
	}


	@Override
	public LocalDate nullSafeGet(final ResultSet resultSet,
	                             final int position,
	                             final SharedSessionContractImplementor session,
	                             final Object owner) throws HibernateException, SQLException
	{
		final java.sql.Date encoded = resultSet.getDate(position);

		if (resultSet.wasNull())
			return null;
		else
		{
			return new LocalDate(encoded);
		}
	}


	@Override
	public void nullSafeSet(final PreparedStatement statement,
	                        final Object value,
	                        final int index,
	                        final SharedSessionContractImplementor session) throws HibernateException, SQLException
	{
		if (value == null)
		{
			statement.setNull(index, Types.DATE);
		}
		else if (value instanceof Long)
		{
			statement.setLong(index, (Long) value);
		}
		else
		{
			final long timestamp = ((LocalDate) value).toDateTimeAtStartOfDay().getMillis();
			final java.sql.Date d = new java.sql.Date(timestamp);

			statement.setDate(index, d);
		}
	}


	@Override
	public LocalDate deepCopy(Object value) throws HibernateException
	{
		return (LocalDate) value; // immutable type
	}


	@Override
	public boolean isMutable()
	{
		return false;
	}


	@Override
	public LocalDate disassemble(final Object value) throws HibernateException
	{
		if (value == null)
			return null;
		else
			return (LocalDate) value;
	}


	@Override
	public LocalDate assemble(final Serializable cached, final Object owner) throws HibernateException
	{
		if (cached == null)
			return null;
		else
			return (LocalDate) cached;
	}


	@Override
	public LocalDate replace(final Object original, final Object target, final Object owner) throws HibernateException
	{
		return (LocalDate) original;
	}


	@Override
	public int compare(final Object a, final Object b)
	{
		return ((LocalDate) a).compareTo((LocalDate) b);
	}
}
