package com.peterphi.std.guice.web.rest.resteasy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds information about the REST classes this webapp uses
 */
public class ResteasyProviderRegistry
{
	private static Set<Class<?>> classes = new HashSet<>();
	private static Set<Object> singletons = new HashSet<>();

	private static int revision = 0;


	/**
	 * Register a provider class
	 *
	 * @param clazz
	 */
	public static synchronized void register(Class<?> clazz)
	{
		if (clazz.isAnnotationPresent(jakarta.ws.rs.ext.Provider.class))
		{
			classes.add(clazz);
			revision++;
		}
		else
		{
			throw new RuntimeException("Class " + clazz.getName() + " is not annotated with jakarta.ws.rs.ext.Provider");
		}
	}

	public static synchronized void registerSingleton(Object o) {
		if (o.getClass().isAnnotationPresent(jakarta.ws.rs.ext.Provider.class))
		{
			singletons.add(o);
			revision++;
		}
		else
		{
			throw new RuntimeException("Class " + o.getClass().getName() + " is not annotated with jakarta.ws.rs.ext.Provider");
		}

	}


	/**
	 * List the registered classes
	 *
	 * @return
	 */
	public static synchronized List<Class<?>> getClasses()
	{
		return new ArrayList<>(classes);
	}


	/**
	 * List the registered singleton classes
	 *
	 * @return
	 */
	public static synchronized List<Object> getSingletons()
	{
		return new ArrayList<>(singletons);
	}
}
