package com.peterphi.std.guice.restclient.resteasy.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.peterphi.std.guice.common.jackson.JacksonFactory;
import com.peterphi.std.guice.common.shutdown.iface.ShutdownManager;
import com.peterphi.std.guice.common.shutdown.iface.StoppableService;
import com.peterphi.std.guice.restclient.converter.CommonTypesParamConverterProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jackson.Jackson2Provider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlRootElementProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlTypeProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset.FastInfosetXmlRootElementProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset.FastInfosetXmlTypeProvider;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds ResteasyClient objects
 */
@Singleton
public class ResteasyClientFactoryImpl implements StoppableService
{
	private static final Logger log = LoggerFactory.getLogger(ResteasyClientFactoryImpl.class);

	private final HttpClientFactory httpClientFactory;
	//private final ResteasyProviderFactory resteasyProviderFactory;

	private ResteasyClient client;
	private ResteasyClient clientH2C;

	private final List<Object> resteasyProviders = new ArrayList<>();


	@Inject
	public ResteasyClientFactoryImpl(final ShutdownManager manager,
	                                 final TracingClientRequestFilter tracingRequestFilter,
	                                 final RemoteExceptionClientResponseFilter remoteExceptionClientResponseFilter,
	                                 final JAXBSerialiserFactory serialiserFactory,
	                                 final JacksonFactory jacksonFactory,
	                                 final HttpClientFactory httpClientFactory)
	{
		this.httpClientFactory = httpClientFactory;

		// Set up the resteasy providers we will register against clients
		{
			// Register the joda param converters
			resteasyProviders.add(new CommonTypesParamConverterProvider());

			// Register the exception processor
			if (remoteExceptionClientResponseFilter != null)
				resteasyProviders.add(remoteExceptionClientResponseFilter);

			if (tracingRequestFilter != null)
				resteasyProviders.add(tracingRequestFilter);

			// Register provider that always tries to get fastinfoset rather than application/xml if the fast infoset plugin is available
			resteasyProviders.add(new FastInfosetPreferringClientRequestFilter());

			// Set up JAXB providers
			{
				resteasyProviders.add(new FastInfosetXmlRootElementProvider<>(serialiserFactory));
				resteasyProviders.add(new JAXBXmlRootElementProvider<>(serialiserFactory));

				// Set up providers for XmlType-annotated and JAXBElement entities
				// These providers will share the same underlying serialiser map
				final JAXBXmlTypeProvider<Object> xmlTypeProvider = new JAXBXmlTypeProvider<>(serialiserFactory);
				resteasyProviders.add(new FastInfosetXmlTypeProvider<>(xmlTypeProvider));
				resteasyProviders.add(xmlTypeProvider);
			}

			// Setup Jackson provider
			{
				resteasyProviders.add(new Jackson2Provider(jacksonFactory));
			}
		}

		if (manager != null)
			manager.register(this);
	}


	private void doProviderFactoryRegistration(ResteasyProviderFactory resteasyProviderFactory)
	{
		for (Object provider : resteasyProviders)
			resteasyProviderFactory.registerProviderInstance(provider);
	}


	public ResteasyClient getOrCreateClient(final AuthCredential credentials,
	                                        final boolean fastFail,
	                                        final boolean storeCookies,
	                                        final boolean h2c)
	{
		final boolean canShare = !fastFail && !storeCookies;

		// If possible, get a shared Http Client
		ClientHttpEngine shared = null;
		if (canShare)
			shared = httpClientFactory.getClient(h2c, fastFail, storeCookies);

		// We will try to share unauthenticated clients
		// This returns the existing client if present, otherwise sets up a Consumer to capture that client for future reuse
		Consumer<ResteasyClient> sharedClientSetter = null;
		if (shared != null && credentials == null)
		{
			if (h2c && httpClientFactory.willVaryWithH2C())
			{
				if (this.clientH2C != null)
					return this.clientH2C;
				else
					sharedClientSetter = (c) -> this.clientH2C = c;
			}
			else
			{
				if (this.client != null)
					return this.client;
				else
					sharedClientSetter = (c) -> this.client = c;
			}
		}

		// Now build a resteasy client
		ResteasyClientBuilder builder = (ResteasyClientBuilder) ResteasyClientBuilder.newBuilder();

		// Set up providers
		doProviderFactoryRegistration(builder.getProviderFactory());

		if (credentials != null)
		{
			builder.register(new ClientRequestFilter()
			{
				@Override
				public void filter(final ClientRequestContext ctx) throws IOException
				{
					final var uri = ctx.getUri();
					if (credentials.scope().test(uri.getScheme(), uri.getHost(), uri.getPort()))
					{
						if (credentials instanceof BearerTokenCredentials token)
						{
							ctx
									.getHeaders()
									.putIfAbsent("Authorization",
									             Collections.singletonList("Bearer " + token.token().getToken()));
						}
						else if (credentials instanceof UsernamePasswordCredentials passwd)
						{
							final String headerVal = "Basic " +
							                         Base64.encodeBase64String((passwd.username() + ":" + passwd.password()).getBytes(
									                         StandardCharsets.UTF_8));

							ctx.getHeaders().putIfAbsent("Authorization", Collections.singletonList(headerVal));
						}
					}
				}
			});
		}

		if (storeCookies)
			builder.enableCookieManagement();

		// Build and apply the HttpEngine
		if (shared != null)
			builder.httpEngine(shared); // Use the shared HTTP client
		else
			builder.httpEngine(httpClientFactory.getClient(h2c, fastFail, storeCookies)); // Get an appropriate HttpClient

		var client = builder.build();

		if (sharedClientSetter != null)
			sharedClientSetter.accept(client);

		return client;
	}


	@Override
	public void shutdown()
	{
		try
		{
			if (client != null)
				client.close();
		}
		catch (Throwable t)
		{
			log.error("Error shutting down shared ResteasyClient!", t);
		}

		try
		{
			if (clientH2C != null)
				clientH2C.close();
		}
		catch (Throwable t)
		{
			log.error("Error shutting down shared H2C ResteasyClient!", t);
		}
	}


	public interface AuthCredential
	{
		ResteasyClientFactoryImpl.AuthScope scope();
	}

	public static record AuthScope(String scheme, String host, int port)
	{
		public boolean test(String uriScheme, String uriHost, int uriPort)
		{
			if (scheme != null && !scheme.equalsIgnoreCase(uriScheme))
				return false;
			else if (port != -1 && uriPort != port)
				return false;
			else
				return host().equalsIgnoreCase(uriHost);
		}
	}


	public static record BearerTokenCredentials(AuthScope scope, BearerGenerator token) implements AuthCredential
	{

	}

	public static record UsernamePasswordCredentials(AuthScope scope, String username, String password,
	                                                 boolean preempt) implements AuthCredential
	{
	}
}
