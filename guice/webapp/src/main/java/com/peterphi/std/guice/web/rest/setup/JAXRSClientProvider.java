package com.peterphi.std.guice.web.rest.setup;

import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.peterphi.std.guice.restclient.JAXRSProxyClientFactory;

import jakarta.inject.Provider;

/**
 * Builds JAX-RS Proxy Clients for interfaces using {@link com.peterphi.std.guice.restclient.JAXRSProxyClientFactory#getClient(Class)}
 *
 * @param <T>
 */
class JAXRSClientProvider<T> implements Provider<T>
{
	private final Class<? extends T> iface;
	private final JAXRSProxyClientFactory clientFactory;


	@Inject
	public JAXRSClientProvider(final TypeLiteral<T> iface, final JAXRSProxyClientFactory clientFactory)
	{
		this.iface = (Class<T>) iface.getRawType();
		this.clientFactory = clientFactory;
	}


	@Override
	public T get()
	{
		return clientFactory.getClient(iface);
	}
}
