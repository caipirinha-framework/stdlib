package com.peterphi.std.guice.common.serviceprops.jaxbref;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.Provider;
import com.peterphi.std.threading.Timeout;

import java.util.function.Consumer;

public class JAXBResourceProvider<T> implements Provider<T>
{
	private final Provider<JAXBResourceFactory> resourceFactoryProvider;
	private final Provider<HealthCheckRegistry> healthCheckRegistryProvider;
	private final String propertyName;
	private final Class<T> clazz;
	private final Consumer<T> onLoadMethod;

	private Timeout cacheValidity = Timeout.THIRTY_SECONDS;

	private volatile T value;
	private volatile long cacheExpires = Integer.MIN_VALUE;

	private boolean hasRegisteredHealthCheck = false;

	/**
	 * N.B. retained (rather than using {@link JAXBResourceFactory#getOnce(Class, String)}) so its change-detection cache can
	 * suppress re-deserialisation when the config content has not changed
	 */
	private JAXBNamedResourceFactory<T> namedFactory;


	public JAXBResourceProvider(final Provider<JAXBResourceFactory> resourceFactoryProvider,
	                            final Provider<HealthCheckRegistry> healthCheckRegistryProvider,
	                            final String propertyName,
	                            final Class<T> clazz,
	                            final Consumer<T> onLoadMethod)
	{
		this.resourceFactoryProvider = resourceFactoryProvider;
		this.healthCheckRegistryProvider = healthCheckRegistryProvider;
		this.propertyName = propertyName;
		this.clazz = clazz;
		this.onLoadMethod = onLoadMethod;
	}


	/**
	 * Change the period that serialised results will be cached for
	 *
	 * @param cacheValidity
	 */
	public void setCacheValidity(final Timeout cacheValidity)
	{
		this.cacheValidity = cacheValidity;
		this.cacheExpires = Integer.MIN_VALUE;
	}


	@Override
	public synchronized T get()
	{
		final long now = System.currentTimeMillis();

		if (cacheExpires > now)
		{
			final T obj = this.value;

			if (obj != null)
				return obj;
		}

		final T obj = load(now);

		return obj;
	}


	private synchronized T load(final long now)
	{
		// Cannot use cache, must recompute
		final T obj = deserialise();

		// If caching is enabled, cache the value
		if (cacheValidity != null)
		{
			this.value = obj;
			this.cacheExpires = now + cacheValidity.getMilliseconds();
		}
		return obj;
	}


	private synchronized T deserialise()
	{
		if (namedFactory == null)
		{
			namedFactory = resourceFactoryProvider.get().createDedicatedFactory(clazz, propertyName);

			// Reloads are rate-limited by cacheValidity, so don't apply the factory's own rate limiting on top of that
			namedFactory.setMaxReloadRate(0);
		}

		if (!hasRegisteredHealthCheck)
		{
			// Make sure we only register (or try to register) once
			hasRegisteredHealthCheck = true;

			// N.B. manually constructing a name of the form used by AbstractHealthCheck (which is not available in this module) so errors appear with the appropriate severity
			healthCheckRegistryProvider.get().register("COMPROMISED:ConfigFile." + propertyName, new HealthCheck()
			{
				@Override
				protected Result check() throws Exception
				{
					try
					{
						// Try to reload the live value on disk
						// We call this because we want the actual value, not the last successfully loaded value
						// N.B. this will also populate the cache on successful load
						load(System.currentTimeMillis());

						return Result.healthy();
					}
					catch (Throwable t)
					{
						return Result.unhealthy(t);
					}
				}
			});
		}

		final T obj = namedFactory.get();

		// N.B. only fire onLoad when the underlying config has actually (re)loaded, not on every cache refresh
		if (onLoadMethod != null && obj != this.value)
			onLoadMethod.accept(obj);

		return obj;
	}
}
