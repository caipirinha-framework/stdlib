package com.peterphi.std.guice.web.rest.jaxrs;

import com.peterphi.std.guice.apploader.impl.GuiceRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

class GuiceDynamicProxyProvider implements InvocationHandler
{
	private final GuiceRegistry registry;
	private final Class<?> clazz;

	private Object proxyInstance;

	public GuiceDynamicProxyProvider(final GuiceRegistry registry, Class<?> interfaceType)
	{
		this.registry = registry;
		this.clazz = interfaceType;
	}

	public synchronized Object getProxy()
	{
		if (proxyInstance == null)
			proxyInstance = createProxy();

		return proxyInstance;
	}

	private Object createProxy()
	{
		try
		{
			return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Error creating Proxy for " + clazz + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Proxies each method invocation through to a Guice-acquired instance.
	 */
	@Override
	public Object invoke(Object self, Method thisMethod, Object[] args) throws Throwable
	{
		// Get an instance of the implementing class via Guice
		final Object instance = registry.getInjector().getInstance(clazz);

		try
		{
			return thisMethod.invoke(instance, args);
		}
		catch (InvocationTargetException e)
		{
			throw e.getCause();
		}
	}
}
