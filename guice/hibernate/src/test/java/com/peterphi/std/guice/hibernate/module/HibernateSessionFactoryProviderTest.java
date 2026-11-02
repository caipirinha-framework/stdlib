package com.peterphi.std.guice.hibernate.module;

import com.peterphi.std.guice.common.shutdown.iface.ShutdownManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class HibernateSessionFactoryProviderTest
{
	@Test
	public void shutdownMutationCommitsBeforeFactoryCloses() throws Exception
	{
		Configuration config = new Configuration();
		config.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbcDriver");
		config.setProperty("hibernate.connection.url", "jdbc:hsqldb:mem:shutdown_commit");
		config.setProperty("hibernate.connection.username", "sa");
		config.setProperty("hibernate.current_session_context_class", "thread");
		ServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(config.getProperties()).build();
		HibernateSessionFactoryProvider provider = new HibernateSessionFactoryProvider(mock(ShutdownManager.class), () -> registry, config);
		try (SessionFactory factory = provider.get())
		{
			factory.inTransaction(session -> {
				session.createNativeMutationQuery("create table shutdown_marker (value integer)").executeUpdate();
				session.createNativeMutationQuery("insert into shutdown_marker values (1)").executeUpdate();
			});

			TransactionHelper helper = new TransactionHelper();
			helper.sessionProvider = factory::getCurrentSession;
			provider.txutils = helper;
			Field sql = HibernateSessionFactoryProvider.class.getDeclaredField("shutdownSql");
			sql.setAccessible(true);
			sql.set(provider, "update shutdown_marker set value = 2");
			provider.runShutDownSQL();

			factory.inTransaction(session -> assertEquals(Integer.valueOf(2), session.createNativeQuery("select value from shutdown_marker", Integer.class).getSingleResult()));
		}
		finally
		{
			StandardServiceRegistryBuilder.destroy(registry);
		}
	}
}
