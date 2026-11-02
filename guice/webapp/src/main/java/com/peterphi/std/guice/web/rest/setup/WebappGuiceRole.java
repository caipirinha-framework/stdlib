package com.peterphi.std.guice.web.rest.setup;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.apploader.GuiceRole;
import com.peterphi.std.guice.apploader.GuiceSetup;
import com.peterphi.std.guice.common.ClassScannerFactory;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.web.rest.CoreRestServicesModule;
import com.peterphi.std.guice.web.rest.jaxrs.converter.JAXRSJodaConverterModule;
import com.peterphi.std.guice.web.rest.scoping.ServletScopingModule;
import com.peterphi.std.io.PropertyFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class WebappGuiceRole implements GuiceRole
{
	private static final Logger log = LoggerFactory.getLogger(WebappGuiceRole.class);

	private final ServletConfig servlet;
	private final FilterConfig filter;
	private final ServletContext context;

	private final ServletContextModule contextModule;


	public WebappGuiceRole(final ServletConfig config)
	{
		this.servlet = config;
		this.filter = null;
		this.context = config.getServletContext();

		this.contextModule = new ServletContextModule(config);
	}


	public WebappGuiceRole(final FilterConfig config)
	{
		this.servlet = null;
		this.filter = config;
		this.context = filter.getServletContext();

		this.contextModule = new ServletContextModule(config);
	}


	private static PropertyFile getConfig(ServletConfig servlet, FilterConfig filter, ServletContext context)
	{
		PropertyFile props = new PropertyFile();

		if (context != null)
		{
			final Enumeration<String> names = context.getInitParameterNames();

			while (names.hasMoreElements())
			{
				final String name = names.nextElement();
				props.set(name, context.getInitParameter(name));
			}
		}

		if (servlet != null)
		{
			final Enumeration<String> names = servlet.getInitParameterNames();

			while (names.hasMoreElements())
			{
				final String name = names.nextElement();
				props.set(name, servlet.getInitParameter(name));
			}
		}

		if (filter != null)
		{
			final Enumeration<String> names = filter.getInitParameterNames();

			while (names.hasMoreElements())
			{
				final String name = names.nextElement();
				props.set(name, filter.getInitParameter(name));
			}
		}

		return props;
	}


	@Override
	public void adjustConfigurations(final List<PropertyFile> configs)
	{
		// First, add the context and servlet/filter config properties
		configs.add(0, getConfig(servlet, filter, context));

		// Finally, add simple properties for the context name
		{
			PropertyFile props = new PropertyFile();
			props.set(GuiceProperties.SERVLET_CONTEXT_NAME, context.getContextPath());
			props.set(GuiceProperties.CONTEXT_NAME, context.getContextPath().replaceAll("/", ""));

			configs.add(0, props);
		}
	}


	@Override
	public void register(final Stage stage,
	                     final ClassScannerFactory scanner,
	                     final GuiceConfig config,
	                     final GuiceSetup setup,
	                     final List<Module> modules,
	                     final AtomicReference<Injector> injectorRef,
	                     final MetricRegistry metrics)
	{
		// Expose ServletContext and Filter/Servlet Config instances
		modules.add(contextModule);

		modules.add(new ServletScopingModule());
		modules.add(new JAXRSJodaConverterModule());

		if (config.getBoolean(GuiceProperties.UNIT_TEST, false) == false)
		{
			if (!config.getBoolean(GuiceProperties.DISABLE_CORE_SERVICES, false))
				modules.add(new CoreRestServicesModule());
			else
				log.info("REST Core Services disabled by config parameter");
		}
	}


	@Override
	public void injectorCreated(final Stage stage,
	                            final ClassScannerFactory scanner,
	                            final GuiceConfig config,
	                            final GuiceSetup setup,
	                            final List<Module> modules,
	                            final AtomicReference<Injector> injectorRef,
	                            final MetricRegistry metrics)
	{

	}
}
