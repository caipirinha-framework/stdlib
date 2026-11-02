package com.peterphi.std.guice.hibernate.usertype;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.hibernate.usertype.UserVersionType;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Comparator;


/**
 * Encodes Joda DateTime values into a VARCHAR column where the date format is:
 * <code>yyyyMMdd'T'HHmmss.SSSZ</code>. The time zone offset is 'Z' for zero, and of the form '±HHmm' for non-zero.<br />
 * The column size ranges between 20 and 24 characters.
 */
public class StringDateTimeUserType implements UserType, UserVersionType, Comparator
{
	private static final DateTimeFormatter FORMAT = ISODateTimeFormat.basicDateTime();
	public static StringDateTimeUserType INSTANCE = new StringDateTimeUserType();


	@Override
	public int getSqlType()
	{
		return Types.VARCHAR;
	}


	@Override
	public Class returnedClass()
	{
		return DateTime.class;
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
	public DateTime nullSafeGet(final ResultSet resultSet,
	                            final int position,
	                            final SharedSessionContractImplementor session,
	                            final Object owner) throws HibernateException, SQLException
	{
		final String encoded = resultSet.getString(position);

		if (resultSet.wasNull())
			return null;
		else
			return FORMAT.parseDateTime(encoded);
	}


	@Override
	public void nullSafeSet(final PreparedStatement statement,
	                        final Object value,
	                        final int index,
	                        final SharedSessionContractImplementor session) throws HibernateException, SQLException
	{
		if (value == null)
		{
			statement.setNull(index, Types.VARCHAR);
		}
		else
		{
			final String str = FORMAT.print((DateTime) value);

			statement.setString(index, str);
		}
	}


	@Override
	public DateTime deepCopy(Object value) throws HibernateException
	{
		return (DateTime) value; // immutable type
	}


	@Override
	public boolean isMutable()
	{
		return false;
	}


	@Override
	public DateTime disassemble(final Object value) throws HibernateException
	{
		if (value == null)
			return null;
		else
			return (DateTime) value;
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
	public DateTime replace(final Object original, final Object target, final Object owner) throws HibernateException
	{
		return (DateTime) original;
	}


	@Override
	public DateTime seed(final SharedSessionContractImplementor session)
	{
		return DateTime.now();
	}


	@Override
	public DateTime next(final Object current, final SharedSessionContractImplementor session)
	{
		return seed(session);
	}


	@Override
	public int compare(final Object a, final Object b)
	{
		return ((DateTime) a).compareTo((DateTime) b);
	}
}
