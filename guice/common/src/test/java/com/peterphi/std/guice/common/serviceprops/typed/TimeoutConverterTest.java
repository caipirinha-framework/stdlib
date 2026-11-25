package com.peterphi.std.guice.common.serviceprops.typed;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.peterphi.std.guice.common.serviceprops.ServicePropertiesModule;
import com.peterphi.std.io.PropertyFile;
import com.peterphi.std.threading.Timeout;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TimeoutConverterTest
{
	@Inject
	@Named("timeout1msNoUnit")
	Timeout timeout1msNoUnit;

	@Inject
	@Named("timeout1ms")
	Timeout timeout1ms;

	@Inject
	@Named("timeout1s")
	Timeout timeout1s;

	@Inject
	@Named("timeout60m")
	Timeout timeout60m;

	@Inject
	@Named("timeout1h")
	Timeout timeout1h;

	@Inject
	@Named("timeout50h")
	Timeout timeout50h;


	@Inject
	@Named("timeout1000d")
	Timeout timeout1000d;


	@Test
	public void test()
	{
		PropertyFile props = new PropertyFile();
		props.set("timeout1msNoUnit", "1");
		props.set("timeout1ms", "1ms");
		props.set("timeout1s", "1s");
		props.set("timeout1h", "1h");
		props.set("timeout60m", "60m");
		props.set("timeout50h", "50h");
		props.set("timeout1000d", "P10000D");

		final Injector injector = Guice.createInjector(new ServicePropertiesModule(props));

		injector.injectMembers(this);

		// extract values
		assertEquals(new Timeout(1, TimeUnit.MILLISECONDS), timeout1msNoUnit);
		assertEquals(new Timeout(1, TimeUnit.MILLISECONDS), timeout1ms);
		assertEquals(new Timeout(1, TimeUnit.SECONDS), timeout1s);
		assertEquals(new Timeout(60, TimeUnit.MINUTES), timeout60m);
		assertEquals(new Timeout(1, TimeUnit.HOURS), timeout1h);
		assertEquals(new Timeout(10000, TimeUnit.DAYS), timeout1000d);

		// compare against one another
		assertEquals(timeout60m, timeout1h);
		assertEquals(timeout1msNoUnit, timeout1ms);
		assertFalse(timeout50h.equals(timeout1h));
	}
}
