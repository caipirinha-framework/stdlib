package com.peterphi.std.guice.hibernate.usertype;

import com.peterphi.std.types.Timecode;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Encodes a Timecode as a String in the form <code>SMPTE Timecode@Timebase</code> - See {@link  Timecode#getInstance(String)}
 * for
 * information on the format
 */
public class TimecodeUserType implements UserType
{
	public static TimecodeUserType INSTANCE = new TimecodeUserType();


	@Override
	public int getSqlType()
	{
		return Types.VARCHAR;
	}


	@Override
	public Class returnedClass()
	{
		return Timecode.class;
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
	public Timecode nullSafeGet(final ResultSet resultSet,
	                            final int position,
	                            final SharedSessionContractImplementor session,
	                            final Object owner) throws HibernateException, SQLException
	{
		final String encoded = resultSet.getString(position);

		if (resultSet.wasNull())
			return null;
		else
			return Timecode.getInstance(encoded);
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
			final String str = ((Timecode) value).toEncodedString();

			statement.setString(index, str);
		}
	}


	@Override
	public Timecode deepCopy(Object value) throws HibernateException
	{
		return (Timecode) value; // immutable type
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
			return ((Timecode) value).toEncodedString();
	}


	@Override
	public Object assemble(final Serializable cached, final Object owner) throws HibernateException
	{
		if (cached == null)
			return null;
		else
			return Timecode.getInstance((String) cached);
	}


	@Override
	public Timecode replace(final Object original, final Object target, final Object owner) throws HibernateException
	{
		return (Timecode) original;
	}
}
