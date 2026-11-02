package com.peterphi.std.guice.hibernate.dialect;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

public class PostgresByteaTest
{
	@Test
	public void lobByteArrayRoundTripsThroughExistingByteaColumn() throws Exception
	{
		try (EmbeddedPostgres database = EmbeddedPostgres.builder().start())
		{
			Configuration config = new Configuration();
			config.getProperties().put("hibernate.connection.datasource", database.getPostgresDatabase());
			config.setProperty("hibernate.dialect", PostgresDialect.class.getName());
			config.addAnnotatedClass(BinaryEntity.class);

			try (SessionFactory factory = config.buildSessionFactory())
			{
				factory.inTransaction(session -> session.createNativeMutationQuery(
						"create table binary_entity (id bigint primary key, content bytea)").executeUpdate());
				byte[] content = {0, 1, -1, 42};
				factory.inTransaction(session -> {
					BinaryEntity entity = new BinaryEntity();
					entity.id = 1L;
					entity.content = content;
					session.persist(entity);
				});
				factory.inTransaction(session -> {
					BinaryEntity entity = session.find(BinaryEntity.class, 1L);
					assertArrayEquals(content, entity.content);
					entity.content = null;
				});
				factory.inTransaction(session -> assertNull(session.find(BinaryEntity.class, 1L).content));
			}
		}
	}

	@Entity(name = "binary_entity")
	public static class BinaryEntity
	{
		@Id
		public Long id;

		@Lob
		public byte[] content;
	}
}
