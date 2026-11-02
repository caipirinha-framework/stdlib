package com.peterphi.std.guice.restclient.resteasy.impl;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.apploader.GuiceConstants;
import com.peterphi.std.guice.apploader.GuiceServiceProperties;
import com.peterphi.std.guice.common.breaker.Breaker;
import com.peterphi.std.guice.common.breaker.BreakerService;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.restclient.JAXRSProxyClientFactory;
import com.peterphi.std.guice.restclient.annotations.FastFailServiceClient;
import com.peterphi.std.guice.restclient.annotations.NoClientBreaker;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.UriBuilder;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Singleton
public class ResteasyProxyClientFactoryImpl implements JAXRSProxyClientFactory
{
	/**
	 * A Bearer Token generator that generates Delegated Tokens for an OAuth2 user session when calling other services
	 */
	public static final String OAUTH_DELEGATING_BEARER_GENERATOR = "com.peterphi.std.guice.web.rest.auth.oauth2.OAuthSessionDelegatingBearerGenerator";

	@Inject
	ResteasyClientFactoryImpl clientFactory;

	@Inject
	GuiceConfig config;

	@Inject
	BreakerService breakerService;

	@Inject
	Injector guice;

	@Inject(optional = true)
	@Named("jaxrs.cookie-store")
	@Doc("Whether default JAX-RS clients should maintain a cookie store (default false); will also default to false if oauth delegation is switched on (or if a bearer generator is configured)")
	public boolean defaultStoreCookies = false;

	/**
	 * Counter that keeps track of the number of currently-paused service calls within this service
	 */
	private final AtomicInteger pausedCallsCounter = new AtomicInteger(0);

	public ResteasyProxyClientFactoryImpl()
	{
	}


	public ResteasyProxyClientFactoryImpl(ResteasyClientFactoryImpl clientFactory, GuiceConfig config)
	{
		this.clientFactory = clientFactory;
		this.config = config;
	}

	//
	// Core Client Create Methods
	//


	ResteasyWebTarget createWebTarget(ServiceClientConfig c)
	{
		URI endpoint = c.endpoint;
		boolean h2c = c.h2c;

		// Allow the use of the "h2c://" scheme as an alias for http:// with h2c=true
		// This allows a broader set of users to set h2c without having to explicitly provide an h2c flag
		if ("h2c".equalsIgnoreCase(endpoint.getScheme()))
		{
			h2c = true;
			endpoint = UriBuilder.fromUri(endpoint).scheme("http").build();
		}

		final ResteasyClientFactoryImpl.AuthScope scope = new ResteasyClientFactoryImpl.AuthScope(endpoint.getScheme(),
		                                                                                          endpoint.getHost(),
		                                                                                          -1);

		final ResteasyClientFactoryImpl.AuthCredential credentials;
		if (c.bearerGenerator != null)
			credentials = new ResteasyClientFactoryImpl.BearerTokenCredentials(scope, c.bearerGenerator);
		else if (c.username != null)
			credentials = new ResteasyClientFactoryImpl.UsernamePasswordCredentials(scope,
			                                                                        c.username,
			                                                                        c.password,
			                                                                        c.preemptiveAuth);
		else
			credentials = null;

		return clientFactory.getOrCreateClient(credentials, c.fastFail, c.storeCookies, h2c).target(endpoint);
	}


	private <T> T getClient(final Class<T> iface, final ResteasyWebTarget target, final ServiceClientConfig config)
	{
		final T proxy = target.proxy(iface);


		final boolean fastFail = config != null ? config.fastFail : iface.isAnnotationPresent(FastFailServiceClient.class);
		final boolean ignoreBreakers = iface.isAnnotationPresent(NoClientBreaker.class);

		final String name = (config != null && config.name != null) ? config.name : null;

		// Set up a Pausable Proxy that will allow us to pause service calls by tripping a breaker
		PausableProxy handler = createPausableProxy(proxy, fastFail, name, ignoreBreakers);

		return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface}, handler);
	}

	private final Map<String, Breaker> restBreakers = new ConcurrentHashMap<>();


	private <T> PausableProxy createPausableProxy(final T proxy,
	                                              final boolean fastFail,
	                                              final String name,
	                                              final boolean ignoreBreakers)
	{
		// N.B. should not link Breaker to the PausableProxy, since it will leak PausableProxy if the caller then discards it
		final Breaker breaker;
		if (!ignoreBreakers)
		{
			final String key = (name != null) ? name : "unnamed";

			breaker = restBreakers.computeIfAbsent(key, k -> breakerService.register(null, List.of("restcall", "restcall." + k)));
		}
		else
		{
			breaker = null;
		}

		return new PausableProxy(proxy, fastFail, breaker, pausedCallsCounter);
	}


	//
	// Interface implementations
	//


	@Override
	public ResteasyWebTarget getWebTarget(final String... names)
	{
		return createWebTarget(getServiceClientConfig(false, names));
	}


	@Override
	public ResteasyWebTarget getWebTarget(final Class<?> iface, final String... names)
	{
		final boolean fastFail = iface.isAnnotationPresent(FastFailServiceClient.class);

		return createWebTarget(getServiceClientConfig(fastFail, names));
	}


	@Override
	public <T> T getClient(final Class<T> iface, final String... names)
	{
		final boolean fastFail = iface.isAnnotationPresent(FastFailServiceClient.class);

		final ServiceClientConfig config = getServiceClientConfig(fastFail, names);
		return getClient(iface, createWebTarget(config), config);
	}


	@Override
	public <T> T getClient(final Class<T> iface)
	{
		final ServiceClientConfig config = getServiceClientConfig(iface);

		return getClient(iface, config);
	}


	@Override
	public <T> T getClient(final Class<T> iface, final WebTarget target)
	{
		return getClient(iface, (ResteasyWebTarget) target, null);
	}


	private <T> T getClient(final Class<T> iface, final ServiceClientConfig config)
	{
		final ResteasyWebTarget target = createWebTarget(config);

		return getClient(iface, target, config);
	}


	@Override
	public ResteasyWebTarget createWebTarget(final URI endpoint, String username, String password)
	{
		ServiceClientConfig config = new ServiceClientConfig(null,
		                                                     endpoint,
		                                                     username,
		                                                     password,
		                                                     false,
		                                                     false,
		                                                     defaultStoreCookies,
		                                                     null,
		                                                     true);
		return createWebTarget(config);
	}


	@Override
	public <T> T createClient(final Class<T> iface, final String endpoint)
	{
		return createClient(iface, URI.create(endpoint));
	}


	@Override
	public <T> T createClient(Class<T> iface, URI endpoint)
	{
		return createClient(iface, endpoint, false);
	}


	@Override
	public <T> T createClient(final Class<T> iface, final URI endpoint, final boolean preemptiveAuth)
	{
		return createClientWithPasswordAuth(iface, endpoint, getUsername(endpoint), getPassword(endpoint), preemptiveAuth);
	}


	@Override
	@Deprecated
	public <T> T createClientWithPasswordAuth(Class<T> iface, URI endpoint, String username, String password)
	{
		return createClientWithPasswordAuth(iface, endpoint, username, password, false);
	}


	@Override
	public <T> T createClientWithBearerAuth(final Class<T> iface, final URI endpoint, final Supplier<String> token)
	{
		final boolean fastFail = iface.isAnnotationPresent(FastFailServiceClient.class);

		ServiceClientConfig config = new ServiceClientConfig(null,
		                                                     endpoint,
		                                                     null,
		                                                     null,
		                                                     fastFail,
		                                                     false,
		                                                     defaultStoreCookies,
		                                                     new SupplierBearerGenerator(token),
		                                                     false);


		return getClient(iface, config);
	}


	@Override
	public <T> T createClientWithPasswordAuth(final Class<T> iface,
	                                          final URI endpoint,
	                                          final String username,
	                                          final String password,
	                                          final boolean preemptiveAuth)
	{
		final boolean fastFail = iface.isAnnotationPresent(FastFailServiceClient.class);

		ServiceClientConfig config = new ServiceClientConfig(null,
		                                                     endpoint,
		                                                     username,
		                                                     password,
		                                                     fastFail,
		                                                     false,
		                                                     defaultStoreCookies,
		                                                     null,
		                                                     preemptiveAuth);

		return getClient(iface, config);
	}


	//
	// Helper methods
	//


	private record ServiceClientConfig(String name, URI endpoint, String username, String password, boolean fastFail, boolean h2c,
	                                   boolean storeCookies, BearerGenerator bearerGenerator, boolean preemptiveAuth)
	{
	}


	private ServiceClientConfig getServiceClientConfig(final Class<?> iface)
	{
		final boolean fastFail = iface.isAnnotationPresent(FastFailServiceClient.class);
		final String[] names = ServiceNameHelper.getServiceNames(iface);

		return getServiceClientConfig(fastFail, names);
	}


	private ServiceClientConfig getServiceClientConfig(final boolean defaultFastFail, final String... names)
	{
		final String name = ServiceNameHelper.getName(config, null, names);

		if (name == null)
			throw new IllegalArgumentException("Cannot find service in configuration by any of these names: " +
			                                   Arrays.asList(names));

		final String endpoint = config.get(GuiceServiceProperties.prop(GuiceServiceProperties.ENDPOINT, name), null);
		final URI uri = URI.create(endpoint);

		// TODO allow other per-service configuration?
		final String username = config.get(GuiceServiceProperties.prop(GuiceServiceProperties.USERNAME, name), getUsername(uri));
		final String password = config.get(GuiceServiceProperties.prop(GuiceServiceProperties.PASSWORD, name), getPassword(uri));
		final boolean fastFail = config.getBoolean(GuiceServiceProperties.prop(GuiceServiceProperties.FAST_FAIL, name),
		                                           defaultFastFail);
		final String authType = config.get(GuiceServiceProperties.prop(GuiceServiceProperties.AUTH_TYPE, name),
		                                   GuiceConstants.JAXRS_CLIENT_AUTH_DEFAULT);
		final String bearerToken = config.get(GuiceServiceProperties.prop(GuiceServiceProperties.BEARER_TOKEN, name), null);
		final boolean h2c = uri.getScheme().equalsIgnoreCase("http") &&
		                    config.getBoolean(GuiceServiceProperties.prop(GuiceServiceProperties.H2C, name),
		                                      false); // h2c with prior knowledge
		final boolean oauthDelegate = config.getBoolean(GuiceServiceProperties.prop(GuiceServiceProperties.SHOULD_DELEGATE_USER_TOKEN,
		                                                                            name), false);
		final String defaultBearerGenerator;

		if (oauthDelegate)
			defaultBearerGenerator = OAUTH_DELEGATING_BEARER_GENERATOR;
		else
			defaultBearerGenerator = null;

		final String bearerTokenClassName = config.get(GuiceServiceProperties.prop(GuiceServiceProperties.BEARER_GENERATOR, name),
		                                               defaultBearerGenerator);

		// N.B. do not store cookies by default if we're generating bearer tokens (since this may result in credentials being improperly shared across calls)
		final boolean storeCookies = config.getBoolean(GuiceServiceProperties.prop(GuiceServiceProperties.STORE_COOKIES, name),
		                                               defaultStoreCookies && (bearerTokenClassName == null));

		final BearerGenerator bearerSupplier;
		{
			if (bearerTokenClassName != null)
			{
				try
				{
					final Class<? extends BearerGenerator> bearerClass = (Class) Class.forName(bearerTokenClassName,
					                                                                           false,
					                                                                           getClass().getClassLoader());

					if (!BearerGenerator.class.isAssignableFrom(bearerClass))
						throw new IllegalArgumentException("Expected class implementing BearerGenerator, but got " +
						                                   bearerClass.getName());

					bearerSupplier = guice.getInstance(bearerClass);
				}
				catch (Throwable e)
				{
					throw new RuntimeException("Error trying to instantiate bearer-generator class " + bearerTokenClassName, e);
				}
			}
			else if (bearerToken != null)
			{
				// Static bearer token
				bearerSupplier = new StaticBearerToken(bearerToken);
			}
			else
			{
				bearerSupplier = null;
			}
		}

		final boolean preemptiveAuth;
		if (bearerSupplier != null)
			preemptiveAuth = true; // force pre-emptive auth
		else if (authType.equalsIgnoreCase(GuiceConstants.JAXRS_CLIENT_AUTH_DEFAULT))
			preemptiveAuth = false;
		else if (authType.equalsIgnoreCase(GuiceConstants.JAXRS_CLIENT_AUTH_PREEMPT))
			preemptiveAuth = true;
		else
			throw new IllegalArgumentException("Illegal auth-type for service " + name + ": " + authType);

		return new ServiceClientConfig(name,
		                               uri,
		                               username,
		                               password,
		                               fastFail,
		                               h2c,
		                               storeCookies,
		                               bearerSupplier,
		                               preemptiveAuth);
	}


	private static String getUsername(URI endpoint)
	{
		final String info = endpoint.getUserInfo();

		if (StringUtils.isEmpty(info))
			return null;
		else if (info.indexOf(':') != -1)
			return info.split(":", 2)[0];
		else
			return null;
	}


	private static String getPassword(URI endpoint)
	{
		final String info = endpoint.getUserInfo();

		if (StringUtils.isEmpty(info))
			return null;
		else if (info.indexOf(':') != -1)
			return info.split(":", 2)[1];
		else
			return null;
	}
}
