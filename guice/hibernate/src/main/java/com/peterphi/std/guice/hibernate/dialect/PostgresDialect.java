package com.peterphi.std.guice.hibernate.dialect;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType;

import static org.hibernate.type.SqlTypes.BLOB;

/**
 * A custom PostgreSQL Dialect for Hibernate that uses bytea instead of oid for byte[] Lobs
 */
public class PostgresDialect extends PostgreSQLDialect
{
	public PostgresDialect()
	{
		super();
	}

	public PostgresDialect(DialectResolutionInfo info)
	{
		super(info);
	}

	@Override
	public void contributeTypes(TypeContributions contributions, ServiceRegistry registry)
	{
		super.contributeTypes(contributions, registry);
		// PostgreSQL's default BLOB binder writes an OID, which cannot be stored in bytea.
		contributions.getTypeConfiguration().getJdbcTypeRegistry().addDescriptor(BLOB, VarbinaryJdbcType.INSTANCE);
	}

	@Override
	protected String columnType(int sqlTypeCode)
	{
		switch (sqlTypeCode)
		{
			case BLOB:
				return "bytea";
			default:
				return super.columnType(sqlTypeCode);
		}
	}
}
