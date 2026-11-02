package com.peterphi.std.guice.web.rest.setup;

import com.google.inject.AbstractModule;
import com.google.inject.ImplementedBy;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Message;
import com.google.inject.util.Types;
import com.peterphi.std.guice.common.ClassScannerFactory;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.restclient.resteasy.impl.ServiceNameHelper;
import com.peterphi.std.guice.serviceregistry.rest.RestResourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.Path;
import java.util.List;

/**
 * Discovers all classes/interfaces annotated with {@link jakarta.ws.rs.Path} and registers them with the {@link
 * com.peterphi.std.guice.serviceregistry.rest.RestResource}.<br />
 * <p>
 * In addition, searches for implementations (unless the interface is annotated with {@link com.google.inject.ImplementedBy}) and
 * auto-binds a REST interface to an implementation. If there is more than one implementation then auto-binding will fail and
 * startup will not be able to proceed.
 * <p>
 * If there are no implementations found then it is assumed the interface is a client interface in this context and it is
 * either bound to a service (if an endpoint is found of the form <code>service.<em>name</em>.endpoint</code> (see {@link
 * com.peterphi.std.guice.restclient.resteasy.impl.ServiceNameHelper#getServiceNames(Class)}  for how the
 * <em>name</em> is computed)
 */
class JAXRSAutoRegisterServicesModule extends AbstractModule
{
	private static final Logger log = LoggerFactory.getLogger(JAXRSAutoRegisterServicesModule.class);

	private final GuiceConfig config;
	private final ClassScannerFactory scannerFactory;


	public JAXRSAutoRegisterServicesModule(final GuiceConfig config, final ClassScannerFactory scannerFactory)
	{
		this.config = config;
		this.scannerFactory = scannerFactory;
	}


	@Override
	protected void configure()
	{
		// scan for @Path annotated classes
		for (Class<?> iface : scannerFactory.getInstance().getAnnotatedClasses(Path.class))
		{
			try
			{
				autobind(iface);
			}
			catch (Exception e)
			{
				addError(new Message("Error auto-binding discovered REST interface " + iface, e));
			}
		}
	}


	private <T> void autobind(Class<T> clazz)
	{
		// If the class is a regular class (or an interface annotated with @ImplementedBy)...
		if (!clazz.isInterface() || clazz.isAnnotationPresent(ImplementedBy.class))
		{
			// Guice knows how to bind this already so no need to search for an implementation, just expose it via JAX-RS
			RestResourceRegistry.register(clazz);
		}
		else
		{
			// Search for implementations
			final List<Class<? extends T>> implementations = scannerFactory.getInstance().getImplementations(clazz);

			if (implementations.size() == 1)
			{
				RestResourceRegistry.register(clazz);

				bind(clazz).to(implementations.get(0));
			}
			else if (implementations.size() == 0)
			{
				if (ServiceNameHelper.getName(config, clazz) != null)
				{
					log.debug(
							"Found JAX-RS interface with no implementation but a service.{name}.endpoint config. Auto-binding a client: {}",
							clazz);

					TypeLiteral typeLiteral = TypeLiteral.<JAXRSClientProvider<T>>get(Types.newParameterizedType(
							JAXRSClientProvider.class,
							clazz));
					bind(clazz).toProvider(typeLiteral);
				}
				else
				{
					log.debug("Found JAX-RS interface with no implementation and no service.{name}.endpoint config. Ignoring: {}",
					          clazz);
				}
			}
			else
			{
				// Too many interfaces
				throw new IllegalArgumentException("Expected 0 or 1 implementation for auto-discovered REST interface " +
				                                   clazz +
				                                   " but found " +
				                                   implementations +
				                                   ". Auto-binding REST interfaces is not possible.");
			}
		}
	}
}
