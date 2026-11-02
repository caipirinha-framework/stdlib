package com.peterphi.std.guice.web.rest.setup;

import com.google.inject.AbstractModule;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;

public class ServletContextModule extends AbstractModule
{
	private final ServletContext context;
	private final FilterConfig filterConfig;
	private final ServletConfig servletConfig;


	public ServletContextModule(final FilterConfig config)
	{
		this.context = config.getServletContext();
		this.filterConfig = config;
		this.servletConfig = null;
	}


	public ServletContextModule(final ServletConfig config)
	{
		this.context = config.getServletContext();
		this.filterConfig = null;
		this.servletConfig = config;
	}


	@Override
	protected void configure()
	{
		bind(ServletContext.class).toInstance(context);

		if (filterConfig != null)
			bind(FilterConfig.class).toInstance(filterConfig);

		if (servletConfig != null)
			bind(ServletConfig.class).toInstance(servletConfig);
	}
}
