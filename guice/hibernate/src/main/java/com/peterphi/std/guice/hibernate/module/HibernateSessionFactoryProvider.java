package com.peterphi.std.guice.hibernate.module;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.common.shutdown.iface.ShutdownManager;
import com.peterphi.std.guice.common.shutdown.iface.StoppableService;
import com.peterphi.std.guice.database.annotation.Transactional;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;

import jakarta.inject.Named;

/**
 * A SessionFactory Provider for Guice
 */
class HibernateSessionFactoryProvider implements Provider<SessionFactory>, StoppableService
{
	private static final Logger log = LoggerFactory.getLogger(HibernateSessionFactoryProvider.class);

	private final Provider<ServiceRegistry> serviceRegistryProvider;
	private final Configuration config;

	private SessionFactory sessionFactory;

	@Inject(optional = true)
	@Named("hibernate.shutdown.sql")
	@Doc("The SQL to run on the database before a shutdown occurs")
	private String shutdownSql;

	@Inject
	TransactionHelper txutils;


	@Inject
	public HibernateSessionFactoryProvider(ShutdownManager coordinator,
	                                       Provider<ServiceRegistry> serviceRegistryProvider,
	                                       Configuration config)
	{
		if (config == null)
			throw new IllegalArgumentException("Must provide non-null hibernate Configuration!");

		this.serviceRegistryProvider = serviceRegistryProvider;
		this.config = config;

		coordinator.register(this);
	}


	public synchronized SessionFactory get()
	{
		if (this.sessionFactory == null)
		{
			final ServiceRegistry serviceRegistry = serviceRegistryProvider.get();

			try
			{
				this.sessionFactory = config.buildSessionFactory(serviceRegistry);
			}
			catch (Throwable t)
			{
				log.warn("Error setting up hibernate session factory", t);

				throw t;
			}
			log.trace("Hibernate Setup Complete.");
		}

		return sessionFactory;
	}


	@Override
	public void shutdown()
	{
		log.info("Shut down Hibernate SessionFactory...");

		if (sessionFactory != null)
		{

			runShutDownSQL();
			sessionFactory.close();
			sessionFactory = null;
		}
		else
		{
			log.info("SessionFactory already null");
		}
	}


	void runShutDownSQL()
	{
		if (StringUtils.isNotEmpty(shutdownSql))
		{
			try (final HibernateTransaction tx = txutils.start())
			{
				final Session session = sessionFactory.getCurrentSession();
				session.createNativeMutationQuery(shutdownSql).executeUpdate();
				tx.commit();
			}
		}
	}
}
