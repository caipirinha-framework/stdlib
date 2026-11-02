package com.peterphi.std.guice.hibernate.mapping;

import com.codahale.metrics.MetricRegistry;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.hibernate.module.HibernateModule;
import com.peterphi.std.guice.hibernate.module.logging.HibernateObservingInterceptor;
import com.peterphi.std.types.SampleCount;
import com.peterphi.std.types.Timebase;
import com.peterphi.std.types.Timecode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.Version;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HibernateMappingCompatibilityTest
{
	private Configuration configuration(String database, java.util.function.Consumer<Configuration> mapping)
	{
		GuiceConfig properties = new GuiceConfig();
		properties.set("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
		properties.set("hibernate.connection.url", "jdbc:hsqldb:mem:" + database);
		properties.set("hibernate.connection.username", "sa");
		properties.set("hibernate.hbm2ddl.auto", "create-drop");
		properties.set("hibernate.allow-hbm2ddl-create", "true");
		properties.set("hibernate.xml_mapping_enabled", "true");

		HibernateModule module = new HibernateModule(new MetricRegistry(), properties)
		{
			@Override
			protected void configure(Configuration config)
			{
				mapping.accept(config);
			}
		};
		return module.getHibernateConfiguration(properties, "embedded", new HibernateObservingInterceptor());
	}

	@Test
	public void implicitTypesPreserveTheirSqlStorage()
	{
		try (SessionFactory factory = configuration("mapping_types", c -> c.addAnnotatedClass(StoredTypes.class)).buildSessionFactory())
		{
			factory.inTransaction(s -> s.persist(new StoredTypes()));
			factory.inTransaction(s -> {
				StoredTypes loaded = s.find(StoredTypes.class, 1L);
				assertEquals(new Date(1234567), loaded.date);
				assertEquals(new DateTime(2345678), loaded.dateTime);
				assertEquals(new LocalDate(2020, 1, 2), loaded.localDate);
				assertEquals(new SampleCount(124, Timebase.HZ_25), loaded.samples);
				assertEquals(Timecode.getInstance("00:00:01:00@25"), loaded.timecode);
				s.doWork(connection -> {
					Map<String, Integer> columns = new HashMap<>();
					try (var result = connection.getMetaData().getColumns(null, null, "MAPPING_TYPES", null))
					{
						while (result.next())
							columns.put(result.getString("COLUMN_NAME"), result.getInt("DATA_TYPE"));
					}
					assertEquals(Integer.valueOf(Types.BIGINT), columns.get("DATE_VAL"));
					assertEquals(Integer.valueOf(Types.BIGINT), columns.get("DATETIME_VAL"));
					assertEquals(Integer.valueOf(Types.DATE), columns.get("LOCALDATE"));
					assertEquals(Integer.valueOf(Types.VARCHAR), columns.get("SAMPLES"));
					assertEquals(Integer.valueOf(Types.VARCHAR), columns.get("TIMECODE"));
				});
			});
		}
	}

	@Test
	public void existingBigintDateColumnSupportsValuesAndNulls()
	{
		Configuration config = configuration("mapping_existing_date", c -> c.addAnnotatedClass(StoredDate.class));
		config.setProperty("hibernate.hbm2ddl.auto", "none");
		try (SessionFactory factory = config.buildSessionFactory())
		{
			factory.inTransaction(s -> s.createNativeMutationQuery(
					"create table mapping_date (id bigint primary key, created bigint)").executeUpdate());
			factory.inTransaction(s -> s.persist(new StoredDate()));
			factory.inTransaction(s -> {
				StoredDate loaded = s.find(StoredDate.class, 1L);
				assertEquals(new Date(1234567), loaded.created);
				loaded.created = null;
			});
			factory.inTransaction(s -> assertNull(s.find(StoredDate.class, 1L).created));
		}
	}

	@Test
	public void implicitDateVersionRetainsBigintAndRejectsStaleWrites()
	{
		try (SessionFactory factory = configuration("mapping_date_version", c -> c.addAnnotatedClass(VersionedDate.class)).buildSessionFactory())
		{
			factory.inTransaction(s -> s.persist(new VersionedDate()));
			VersionedDate first = factory.fromTransaction(s -> s.find(VersionedDate.class, 1L));
			VersionedDate stale = factory.fromTransaction(s -> s.find(VersionedDate.class, 1L));
			first.label = "updated";
			factory.inTransaction(s -> s.merge(first));
			factory.inTransaction(s -> {
				VersionedDate loaded = s.find(VersionedDate.class, 1L);
				assertTrue(loaded.version.after(new Date(0)));
				s.doWork(connection -> {
					try (var result = connection.getMetaData().getColumns(null, null, "MAPPING_DATE_VERSION", "VERSION"))
					{
						assertTrue(result.next());
						assertEquals(Types.BIGINT, result.getInt("DATA_TYPE"));
					}
				});
			});
			stale.label = "stale";
			assertThrows(OptimisticLockException.class, () -> factory.inTransaction(s -> s.merge(stale)));
		}
	}

	@Test
	public void explicitXmlMappingSettingIsHonoured()
	{
		Configuration config = configuration("mapping_xml", c -> {
			String xml = """
					<?xml version="1.0" encoding="UTF-8"?>
					<entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.2">
					  <entity class="com.peterphi.std.guice.hibernate.mapping.HibernateMappingCompatibilityTest$XmlOnly" access="FIELD">
					    <table name="mapping_xml"/>
					    <attributes><id name="id"/><basic name="label"/></attributes>
					  </entity>
					</entity-mappings>
					""";
			c.addInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		});
		assertEquals("true", config.getProperty("hibernate.xml_mapping_enabled"));
		try (SessionFactory factory = config.buildSessionFactory())
		{
			factory.inTransaction(s -> {
				XmlOnly row = new XmlOnly();
				row.id = 1L;
				row.label = "XML";
				s.persist(row);
			});
			factory.inTransaction(s -> assertEquals("XML", s.find(XmlOnly.class, 1L).label));
		}
	}

	@Entity(name = "mapping_types")
	public static class StoredTypes
	{
		@Id public Long id = 1L;
		@Column(name = "date_val") public Date date = new Date(1234567);
		@Column(name = "datetime_val") public DateTime dateTime = new DateTime(2345678);
		public LocalDate localDate = new LocalDate(2020, 1, 2);
		public SampleCount samples = new SampleCount(124, Timebase.HZ_25);
		public Timecode timecode = Timecode.getInstance("00:00:01:00@25");
	}

	@Entity(name = "mapping_date")
	public static class StoredDate
	{
		@Id public Long id = 1L;
		public Date created = new Date(1234567);
	}

	@Entity(name = "mapping_date_version")
	public static class VersionedDate
	{
		@Id public Long id = 1L;
		@Version public Date version = new Date(0);
		public String label = "initial";
	}

	public static class XmlOnly
	{
		public Long id;
		public String label;
	}
}
