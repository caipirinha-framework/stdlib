package com.peterphi.std.guice.hibernate.usertype;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.hibernate.usertype.UserVersionType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;
import java.util.Date;

/**
 * Encodes a date as a BIGINT in the database.<br />
 * <strong>NOTE: Dates being processed MUST NOT be modified in ANY way - they MUST be treated as immutable datatypes</strong>
 */
public class DateUserType implements UserType, UserVersionType, Comparator
{
	public static DateUserType INSTANCE = new DateUserType();


	@Override
	public int getSqlType()
	{
		return Types.BIGINT;
	}


	@Override
	public Class returnedClass()
	{
		return Date.class;
	}


	@Override
	public boolean equals(Object x, Object y) throws HibernateException
	{
		if (x == y)
			return true;
		else if (x == null || y == null)
			return false;
		else
			return x.equals(y);
	}


	@Override
	public int hashCode(final Object x) throws HibernateException
	{
		return x.hashCode();
	}


	@Override
	public Object nullSafeGet(final ResultSet rs,
	                          final int position,
	                          final SharedSessionContractImplementor session,
	                          final Object owner) throws SQLException
	{
		final long timestamp = rs.getLong(position);

		if (rs.wasNull())
			return null;
		else
			return new Date(timestamp);
	}


	@Override
	public void nullSafeSet(final PreparedStatement statement,
	                        final Object value,
	                        final int index,
	                        final SharedSessionContractImplementor session) throws HibernateException, SQLException
	{
		if (value == null)
		{
			statement.setNull(index, Types.BIGINT);
		}
		else if (value instanceof Long)
		{
			statement.setLong(index, (Long) value);
		}
		else
		{
			final long timestamp = ((Date) value).getTime();

			statement.setLong(index, timestamp);
		}
	}


	@Override
	public Date deepCopy(Object value) throws HibernateException
	{
		return (Date) value; // immutable type
	}


	@Override
	public boolean isMutable()
	{
		return false;
	}


	@Override
	public Serializable disassemble(final Object value) throws HibernateException
	{
		if (value == null)
			return null;
		else
			return deepCopy(value);
	}


	@Override
	public Object assemble(final Serializable cached, final Object owner) throws HibernateException
	{
		return deepCopy(cached);
	}


	@Override
	public Object replace(final Object original, final Object target, final Object owner) throws HibernateException
	{
		return deepCopy(original);
	}


	@Override
	public Date seed(final SharedSessionContractImplementor session)
	{
		return new Date();
	}


	@Override
	public Date next(final Object current, final SharedSessionContractImplementor session)
	{
		return seed(session);
	}


	@Override
	public int compare(final Object a, final Object b)
	{
		return ((Date) a).compareTo((Date) b);
	}
}
