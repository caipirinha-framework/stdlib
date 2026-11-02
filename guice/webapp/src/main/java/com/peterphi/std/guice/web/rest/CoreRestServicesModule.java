package com.peterphi.std.guice.web.rest;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.metrics.daemon.HealthCheckWorker;
import com.peterphi.std.guice.metrics.rest.api.HealthRestService;
import com.peterphi.std.guice.metrics.rest.impl.HealthRestServiceImpl;
import com.peterphi.std.guice.serviceregistry.LocalEndpointDiscovery;
import com.peterphi.std.guice.serviceregistry.rest.RestResourceRegistry;
import com.peterphi.std.guice.web.rest.service.GuiceCoreServicesRegistry;
import com.peterphi.std.guice.web.rest.service.breaker.GuiceRestBreakerService;
import com.peterphi.std.guice.web.rest.service.caches.GuiceCacheManagerRestService;
import com.peterphi.std.guice.web.rest.service.daemons.GuiceRestDaemonsService;
import com.peterphi.std.guice.web.rest.service.logging.GuiceRestLoggingService;
import com.peterphi.std.guice.web.rest.service.restcore.GuiceCommonRestResources;
import com.peterphi.std.guice.web.rest.service.restcore.GuiceRestCoreService;
import com.peterphi.std.guice.web.rest.service.restcore.GuiceRestCoreServiceImpl;
import com.peterphi.std.guice.web.rest.service.servicedescription.RestConfigList;
import com.peterphi.std.guice.web.rest.service.servicedescription.RestServiceList;
import com.peterphi.std.guice.web.rest.templating.thymeleaf.ThymeleafModule;

import jakarta.servlet.ServletContext;
import java.net.URI;

public class CoreRestServicesModule extends AbstractModule
{
	/**
	 * Servlet context value to read for the resteasy prefix
	 */
	public static final String RESTEASY_MAPPING_PREFIX = "resteasy.servlet.mapping.prefix";


	@Override
	protected void configure()
	{
		install(new ThymeleafModule());

		bind(GuiceCoreServicesRegistry.class).asEagerSingleton();
		bind(LocalEndpointDiscovery.class).to(ServletEndpointDiscoveryImpl.class);
		bind(HealthCheckWorker.class).asEagerSingleton();

		bind(GuiceRestCoreService.class).to(GuiceRestCoreServiceImpl.class).asEagerSingleton();
		bind(HealthRestService.class).to(HealthRestServiceImpl.class);

		RestResourceRegistry.register(HealthRestService.class);
		RestResourceRegistry.register(GuiceRestCoreService.class);
		RestResourceRegistry.register(GuiceCommonRestResources.class);
		RestResourceRegistry.register(RestServiceList.class);
		RestResourceRegistry.register(RestConfigList.class);
		RestResourceRegistry.register(GuiceRestDaemonsService.class);
		RestResourceRegistry.register(GuiceRestBreakerService.class);
		RestResourceRegistry.register(GuiceRestLoggingService.class);
		RestResourceRegistry.register(GuiceCacheManagerRestService.class);
	}


	/**
	 * Retrieves the RESTeasy mapping prefix - this is the path under the webapp root where RESTeasy services are mapped.
	 *
	 * @param context
	 *
	 * @return
	 */
	@Provides
	@Singleton
	@Named(GuiceProperties.REST_SERVICES_PREFIX)
	public String getRestServicesPrefix(ServletContext context)
	{
		String restPath = context.getInitParameter(RESTEASY_MAPPING_PREFIX);

		if (restPath == null || restPath.isEmpty() || restPath.equals("/"))
		{
			return "";
		}
		else
		{
			return restPath;
		}
	}


	/**
	 * Return the base path for all REST services in this webapp
	 *
	 * @param webappUri
	 * @param restPrefix
	 * 		the prefix for rest services (added after the webapp endpoint to form the base path for the JAX-RS container)
	 * @param context
	 *
	 * @return
	 */
	@Provides
	@Singleton
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	public URI getRestServicesEndpoint(@Named(GuiceProperties.STATIC_ENDPOINT_CONFIG_NAME) URI webappUri,
	                                   @Named(GuiceProperties.REST_SERVICES_PREFIX) String restPrefix,
	                                   ServletContext context)
	{
		if (restPrefix.equals(""))
		{
			// resteasy mapped to /
			return webappUri;
		}
		else
		{
			// Strip the leading / from the restpath
			while (restPrefix.startsWith("/"))
				restPrefix = restPrefix.substring(1);

			final String webappPath = webappUri.toString();

			if (webappPath.endsWith("/"))
				return URI.create(webappPath + restPrefix);
			else
				return URI.create(webappPath + "/" + restPrefix);
		}
	}


	/**
	 * Return the base path for this webapp
	 *
	 * @param localEndpointDiscovery
	 *
	 * @return
	 */
	@Provides
	@Singleton
	@Named(GuiceProperties.STATIC_ENDPOINT_CONFIG_NAME)
	public URI getRestServicesEndpoint(LocalEndpointDiscovery localEndpointDiscovery)
	{
		final URI base = localEndpointDiscovery.getLocalEndpoint();

		return base;
	}
}
