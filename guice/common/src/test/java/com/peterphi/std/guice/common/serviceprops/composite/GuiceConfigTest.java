package com.peterphi.std.guice.common.serviceprops.composite;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class GuiceConfigTest
{
	GuiceConfig props = new GuiceConfig();


	@Test
	public void testOverwriteValue()
	{
		props.set("name", "value1");
		assertEquals("getRaw", "value1", props.getRaw("name", null));
		assertEquals("get", "value1", props.get("name", null));

		props.set("name", "value2");
		assertEquals("getRaw", "value2", props.getRaw("name", null));
		assertEquals("get", "value2", props.get("name", null));
	}


	@Test
	public void testSimpleSetAndGet()
	{
		props.set("name", "value1");
		assertEquals("getRaw", "value1", props.getRaw("name", null));
		assertEquals("get", "value1", props.get("name", null));

		props.setOverride("name", "value2");
		assertEquals("getRaw", "value2", props.getRaw("name", null));
		assertEquals("get", "value2", props.get("name", null));

		props.set("name", "valueX"); // Change an underlying value - should be ignored
		assertEquals("getRaw", "value2", props.getRaw("name", null));
		assertEquals("get", "value2", props.get("name", null));

		assertEquals("override count", 1, props.getOverrides().size());
	}


	@Test
	public void testVariableRef()
	{
		props.set("var", "123");
		props.set("name", "a-${var}-b");
		assertEquals("getRaw", "a-${var}-b", props.getRaw("name", null));
		assertEquals("get", "a-123-b", props.get("name", null));
	}


	@Test
	public void testSetAllPropertiesPreservesLoggingPattern()
	{
		Properties properties = new Properties();
		String name = "log4j.appender.logconsole.layout.ConversionPattern";
		String pattern = "%-5p %d: café = #! %m%n";
		properties.setProperty(name, pattern);

		props.setAll(properties);

		assertEquals(pattern, props.get(name, null));
	}
}
