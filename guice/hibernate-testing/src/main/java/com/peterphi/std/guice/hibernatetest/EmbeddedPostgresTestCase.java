package com.peterphi.std.guice.hibernatetest;

import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.hibernatetest.pgembedded.EmbeddedPostgresDriver;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.TestConfig;
import com.peterphi.std.io.PropertyFile;

public abstract class EmbeddedPostgresTestCase
{
	@Doc("The property name set when the database config us being overridden")
	public static final String DATABASE_OVERRIDE_CONFIG = "database.override";

	public static final String DATABASE_OVERRIDE_VAL_POSTGRES = "postgresql";


	@TestConfig
	public static PropertyFile getPostgresOverrideConfig()
	{
		PropertyFile f = new PropertyFile();

		f.set("hibernate.connection.driver_class", EmbeddedPostgresDriver.class.getName());
		f.set("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
		f.set("hibernate.connection.url", "jdbc:embedded-postgres:shared");

		// Allow
		f.set(DATABASE_OVERRIDE_CONFIG, DATABASE_OVERRIDE_VAL_POSTGRES);

		return f;
	}
}
