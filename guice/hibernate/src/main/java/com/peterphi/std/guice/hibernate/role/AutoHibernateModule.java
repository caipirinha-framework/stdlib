package com.peterphi.std.guice.hibernate.role;

import com.codahale.metrics.MetricRegistry;
import com.peterphi.std.guice.common.ClassScanner;
import com.peterphi.std.guice.common.ClassScannerFactory;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.hibernate.module.HibernateModule;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AutoHibernateModule extends HibernateModule
{
	private static final Logger log = LoggerFactory.getLogger(AutoHibernateModule.class);

	private final ClassScannerFactory scannerFactory;


	public AutoHibernateModule(final ClassScannerFactory scannerFactory, final MetricRegistry metrics, final GuiceConfig config)
	{
		super(metrics, config);

		this.scannerFactory = scannerFactory;
	}


	@Override
	protected void configure(final Configuration config)
	{
		final ClassScanner scanner = scannerFactory.getInstance();

		if (scanner == null)
			throw new IllegalArgumentException("No classpath scanner available, missing scan.packages?");

		for (Class<?> clazz : scanner.getAnnotatedClasses(Entity.class))
		{
			if (log.isTraceEnabled())
				log.trace("Registering @Entity class with hibernate: {}", clazz.getName());
			
			config.addAnnotatedClass(clazz);
		}
	}
}
