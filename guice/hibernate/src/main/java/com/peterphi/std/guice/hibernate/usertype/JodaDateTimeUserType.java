package com.peterphi.std.guice.hibernate.usertype;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.hibernate.usertype.UserVersionType;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Encodes Joda DateTime values into a BIGINT column where the date is expressed in milliseconds since 1970.
 */
@Converter(autoApply = true)
public class JodaDateTimeUserType implements UserType<DateTime>, UserVersionType<DateTime>, AttributeConverter<DateTime,Long>
{
	public static JodaDateTimeUserType INSTANCE = new JodaDateTimeUserType();


	@Override
	public int getSqlType()
	{
		return Types.BIGINT;
	}


	@Override
	public Class<DateTime> returnedClass()
	{
		return DateTime.class;
	}


	@Override
	public boolean equals(DateTime x, DateTime y) throws HibernateException
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
	public int hashCode(final DateTime x) throws HibernateException
	{
		return x.hashCode();
	}


	@Override
	public DateTime nullSafeGet(final ResultSet resultSet,
	                            final int position,
	                            final SharedSessionContractImplementor session,
	                            final Object owner) throws HibernateException, SQLException
	{
		final long encoded = resultSet.getLong(position);

		if (resultSet.wasNull())
			return null;
		else
		{
			return new DateTime(encoded);
		}
	}



	@Override
	public void nullSafeSet(final PreparedStatement statement,
	                        final DateTime value,
	                        final int index,
	                        final SharedSessionContractImplementor session) throws HibernateException, SQLException
	{
		if (value == null)
		{
			statement.setNull(index, Types.BIGINT);
		}
		else
		{
			final long millis = value.getMillis();

			statement.setLong(index, millis);
		}
	}


	@Override
	public DateTime deepCopy(DateTime value) throws HibernateException
	{
		return value; // immutable type
	}


	@Override
	public boolean isMutable()
	{
		return false;
	}


	@Override
	public DateTime disassemble(final DateTime value) throws HibernateException
	{
		return value;
	}


	@Override
	public DateTime assemble(final Serializable cached, final Object owner) throws HibernateException
	{
		if (cached == null)
			return null;
		else
			return (DateTime) cached;
	}


	@Override
	public DateTime replace(final DateTime original, final DateTime target, final Object owner) throws HibernateException
	{
		return original;
	}


	@Override
	public DateTime seed(final SharedSessionContractImplementor session)
	{
		return DateTime.now();
	}


	@Override
	public DateTime next(final DateTime current, final SharedSessionContractImplementor session)
	{
		return seed(session);
	}


	@Override
	public int compare(final DateTime a, final DateTime b)
	{
		return a.compareTo(b);
	}


	@Override
	public Long convertToDatabaseColumn(final DateTime attribute)
	{
		if (attribute != null)
			return attribute.getMillis();
		else
			return null;
	}


	@Override
	public DateTime convertToEntityAttribute(final Long dbData)
	{
		if (dbData != null)
			return new DateTime(dbData.longValue());
		else
			return null;
	}
}
