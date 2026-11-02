package com.peterphi.std.guice.restclient;

import com.google.inject.ImplementedBy;
import com.peterphi.std.guice.restclient.resteasy.impl.ResteasyProxyClientFactoryImpl;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import jakarta.ws.rs.client.WebTarget;
import java.net.URI;
import java.util.function.Supplier;

/**
 * A factory that builds dynamic proxy clients (using JAX-RS RESTful client interfaces) for services at a known location
 */
@ImplementedBy(ResteasyProxyClientFactoryImpl.class)
public interface JAXRSProxyClientFactory
{
	/**
	 * Retrieve a WebTarget for the service, searching for the service in configuration using the ordered list of names
	 *
	 * @param names
	 *
	 * @return
	 */
	public WebTarget getWebTarget(final String... names);

	/**
	 * Retrieve a WebTarget, using the configuration for the named service interface (but not creating a dynamic proxy client)
	 *
	 * @param iface
	 * 		the service interface to read configuration (endpoint, auth policy, etc.) from
	 * @param names
	 *
	 * @return
	 */
	public WebTarget getWebTarget(final Class<?> iface, final String... names);

	/**
	 * Retrieve a Proxy client for the service, searching for the service in configuration using the ordered list of names
	 *
	 * @param iface
	 * 		the service iface
	 * @param names
	 * @param <T>
	 *
	 * @return
	 */
	public <T> T getClient(final Class<T> iface, final String... names);

	/**
	 * Get a client for the provided interface, searching for the service in configuration using the default name(s)
	 *
	 * @param iface
	 * 		the service iface
	 * @param <T>
	 *
	 * @return
	 */
	public <T> T getClient(final Class<T> iface);


	/**
	 * Get a client for the provided interface, building it from the provided WebTarget
	 *
	 * @param iface
	 * 		the service iface
	 * @param target
	 * 		the WebTarget to use to build the dynamic proxy client
	 * @param <T>
	 *
	 * @return
	 */
	public <T> T getClient(final Class<T> iface, WebTarget target);

	/**
	 * Create a WebTarget for a fixed endpoint optionally with a given username and password
	 *
	 * @param endpoint
	 * @param username
	 * @param password
	 *
	 * @return
	 */

	public ResteasyWebTarget createWebTarget(final URI endpoint, String username, String password);

	/**
	 * Create a dynamic proxy client for the desired service, setting the base endpoint
	 *
	 * @param iface
	 * 		the service interface
	 * @param endpoint
	 * 		a valid URI to use as a base point for the provided service interface
	 * @param <T>
	 * 		the type of the service
	 *
	 * @return a dynamic proxy for the service
	 */
	public <T> T createClient(final Class<T> iface, final URI endpoint);

	/**
	 * Create a dynamic proxy client for the desired service, setting the base endpoint
	 *
	 * @param iface
	 * 		the service interface
	 * @param endpoint
	 * 		a valid URI to use as a base point for the provided service interface
	 * @param preemptiveAuth
	 * 		if true, enable pre-emptive basic authentication
	 * @param <T>
	 * 		the type of the service
	 *
	 * @return a dynamic proxy for the service
	 */
	public <T> T createClient(final Class<T> iface, final URI endpoint, boolean preemptiveAuth);

	/**
	 * Create a dynamic proxy client for the desired service, setting the base endpoint
	 *
	 * @param iface
	 * 		the service interface
	 * @param endpoint
	 * 		a valid URI to use as a base point for the provided service interface
	 * @param <T>
	 * 		the type of the service
	 *
	 * @return a dynamic proxy for the service
	 *
	 * @throws IllegalArgumentException
	 * 		if <code>endpoint</code> cannot be parsed as a valid URI
	 */
	public <T> T createClient(final Class<T> iface, final String endpoint);

	/**
	 * Create a dynamic proxy client for the desired service, setting the base endpoint & setting up HTTP Basic auth
	 *
	 * @param iface
	 * 		the service interface
	 * @param endpoint
	 * 		a valid URI to use as a base point for the provided service interface
	 * @param username
	 * 		the username to use
	 * @param password
	 * 		the password to use
	 * @param <T>
	 * 		the type of the service
	 *
	 * @return a dynamic proxy for the service
	 *
	 * @throws IllegalArgumentException
	 * 		if <code>endpoint</code> cannot be parsed as a valid URI
	 */
	@Deprecated
	public <T> T createClientWithPasswordAuth(final Class<T> iface,
	                                          final URI endpoint,
	                                          final String username,
	                                          final String password);


	/**
	 * Create a dynamic proxy client for the desired service, setting the base endpoint & setting up HTTP Basic auth
	 *
	 * @param iface
	 * 		the service interface
	 * @param endpoint
	 * 		a valid URI to use as a base point for the provided service interface
	 * @param token
	 * 		a supplier that will be asked to provide a token for each HTTP call
	 * @param <T>
	 * 		the type of the service
	 *
	 * @return a dynamic proxy for the service
	 *
	 * @throws IllegalArgumentException
	 * 		if <code>endpoint</code> cannot be parsed as a valid URI
	 */
	public <T> T createClientWithBearerAuth(final Class<T> iface, final URI endpoint, final Supplier<String> token);


	/**
	 * Create a dynamic proxy client for the desired service, setting the base endpoint & setting up HTTP Basic auth
	 *
	 * @param iface
	 * 		the service interface
	 * @param endpoint
	 * 		a valid URI to use as a base point for the provided service interface
	 * @param username
	 * 		the username to use
	 * @param password
	 * 		the password to use
	 * @param preemptiveAuth
	 * 		if true then pre-emptive basic authentication will be used if credentials are also supplied
	 * @param <T>
	 * 		the type of the service
	 *
	 * @return a dynamic proxy for the service
	 *
	 * @throws IllegalArgumentException
	 * 		if <code>endpoint</code> cannot be parsed as a valid URI
	 */
	public <T> T createClientWithPasswordAuth(final Class<T> iface,
	                                          final URI endpoint,
	                                          final String username,
	                                          final String password,
	                                          boolean preemptiveAuth);
}
