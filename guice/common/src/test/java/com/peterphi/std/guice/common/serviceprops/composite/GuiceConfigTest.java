package com.peterphi.std.guice.common.serviceprops.composite;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GuiceConfigTest
{
	GuiceConfig props = new GuiceConfig();


	@Test
	public void testOverwriteValue()
	{
		props.set("name", "value1");
		assertEquals("value1", props.getRaw("name", null), "getRaw");
		assertEquals("value1", props.get("name", null), "get");

		props.set("name", "value2");
		assertEquals("value2", props.getRaw("name", null), "getRaw");
		assertEquals("value2", props.get("name", null), "get");
	}


	@Test
	public void testSimpleSetAndGet()
	{
		props.set("name", "value1");
		assertEquals("value1", props.getRaw("name", null), "getRaw");
		assertEquals("value1", props.get("name", null), "get");

		props.setOverride("name", "value2");
		assertEquals("value2", props.getRaw("name", null), "getRaw");
		assertEquals("value2", props.get("name", null), "get");

		props.set("name", "valueX"); // Change an underlying value - should be ignored
		assertEquals("value2", props.getRaw("name", null), "getRaw");
		assertEquals("value2", props.get("name", null), "get");

		assertEquals(1, props.getOverrides().size(), "override count");
	}


	@Test
	public void testVariableRef()
	{
		props.set("var", "123");
		props.set("name", "a-${var}-b");
		assertEquals("a-${var}-b", props.getRaw("name", null), "getRaw");
		assertEquals("a-123-b", props.get("name", null), "get");
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
