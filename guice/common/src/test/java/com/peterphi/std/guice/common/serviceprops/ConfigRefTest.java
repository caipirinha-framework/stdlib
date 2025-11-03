package com.peterphi.std.guice.common.serviceprops;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ConfigRefTest
{
	public enum SomeEnum
	{
		A,
		B,
		C
	}

	@Inject
	@Named("some-name")
	String name;

	@Inject
	@Named("some-enum")
	public ConfigRef enumval;


	@Test
	public void testChangingPropertyAtRuntimeAndReinjectingMembersWorks()
	{
		GuiceConfig configuration = new GuiceConfig();

		configuration.set("some-name", "initial value");
		configuration.set("some-enum", "B");

		final Injector injector = Guice.createInjector(new ServicePropertiesModule(configuration));

		injector.injectMembers(this);

		assertEquals("initial value", name);
		assertEquals("enum value", SomeEnum.B, enumval.get(SomeEnum.class));

		configuration.set("some-name", "changed value");

		// Re-inject the change
		injector.injectMembers(this);
		assertEquals("changed value", name);
	}
}
