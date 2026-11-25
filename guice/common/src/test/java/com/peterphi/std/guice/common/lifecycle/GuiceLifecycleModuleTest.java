package com.peterphi.std.guice.common.lifecycle;


import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuiceLifecycleModuleTest
{
	private static boolean POST_CONSTRUCT_WAS_CALLED = false;

	static class ClassWithStringBinding implements GuiceLifecycleListener
	{
		@Inject
		@Named("xyz")
		String xyz;


		@Override
		public void postConstruct()
		{
			POST_CONSTRUCT_WAS_CALLED = true;
		}
	}

	static class ClassWithBrokenDependency implements GuiceLifecycleListener
	{
		@Inject
		DodgyDependency dodgy;


		@Override
		public void postConstruct()
		{
			POST_CONSTRUCT_WAS_CALLED = true;
		}
	}

	/**
	 * A class whose constructor throws an exception
	 */
	static class DodgyDependency
	{
		@Inject
		DodgyDependency()
		{
			throw new RuntimeException();
		}
	}


	@Test
	public void testPostConstructIsCalled()
	{
		POST_CONSTRUCT_WAS_CALLED = false;

		// Bind the xyz property so that construction will succeed
		Injector injector = Guice.createInjector(new GuiceLifecycleModule(), new AbstractModule()
		{
			@Override
			protected void configure()
			{
				Properties properties = new Properties();
				properties.put("xyz", "some value");

				Names.bindProperties(binder(), properties);
			}
		});

		final ClassWithStringBinding instance = injector.getInstance(ClassWithStringBinding.class);

		assertNotNull(instance, "Creation of object should succeed");
		assertEquals("some value", instance.xyz);
		assertTrue(POST_CONSTRUCT_WAS_CALLED, "postConstruct should have been called");
	}


	@Test
	public void testPostConstructNotCalledOnMissingBinding()
	{
		POST_CONSTRUCT_WAS_CALLED = false;

		Injector injector = Guice.createInjector(new GuiceLifecycleModule());
		try
		{
			assertThrows(ConfigurationException.class, () -> injector.getInstance(ClassWithStringBinding.class));
		}
		finally
		{
			assertFalse(POST_CONSTRUCT_WAS_CALLED, "postConstruct should not have been called");
		}
	}


	@Test
	public void testPostConstructNotCalledOnFailureInDependencyConstructor()
	{
		POST_CONSTRUCT_WAS_CALLED = false;

		Injector injector = Guice.createInjector(new GuiceLifecycleModule());
		try
		{
			assertThrows(ProvisionException.class, () -> injector.getInstance(ClassWithBrokenDependency.class));
		}
		finally
		{
			assertFalse(POST_CONSTRUCT_WAS_CALLED, "postConstruct should not have been called");
		}
	}
}
