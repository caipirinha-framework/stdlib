package com.peterphi.std.io;

import org.junit.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PropertyFileTest
{
	@Test
	public void testToMap()
	{
		PropertyFile props = new PropertyFile();
		props.set("a", "b");

		assertEquals(Collections.singletonMap("a", "b"), props.toMap());
	}


	@Test
	public void testToProperties()
	{
		PropertyFile f = new PropertyFile();
		f.set("a", "b");

		Properties properties = new Properties();
		properties.put("a", "b");

		assertEquals(properties, f.toProperties());
	}


	@Test
	public void testPropertiesPreserveDecodedCharacters()
	{
		Properties properties = new Properties();
		properties.setProperty("pattern", "%-5p %d: café = #! %m%n");
		properties.setProperty("special:key=#!\\path", " leading space\ttab\nnewline\rcarriage return\\literal\\n");

		PropertyFile file = new PropertyFile(properties, true);

		assertEquals(properties, file.toProperties());
	}


	@Test
	public void testPropertiesIncludeDefaultsAndOverrides()
	{
		Properties defaults = new Properties();
		defaults.setProperty("inherited", "default: value");
		defaults.setProperty("overridden", "default");
		Properties properties = new Properties(defaults);
		properties.setProperty("overridden", "explicit");

		PropertyFile file = new PropertyFile(properties);

		assertEquals("default: value", file.get("inherited", null));
		assertEquals("explicit", file.get("overridden", null));
		assertEquals(2, file.getVariableCount());
	}


	@Test
	public void testLoadPropertiesReplacesExistingContentsAndMarksClean() throws Exception
	{
		PropertyFile file = new PropertyFile(new java.io.StringReader("# old comment\nold=value\n"));
		file.set("changed", "value");
		Properties properties = new Properties();
		properties.setProperty("new", "value: with = punctuation");

		file.load(properties);

		assertEquals(properties, file.toProperties());
		assertEquals(1, file.getLineCount());
		assertFalse(file.dirty);

		file.load(new Properties());

		assertEquals(0, file.getVariableCount());
		assertEquals(0, file.getLineCount());
		assertFalse(file.dirty);
	}


	@Test
	public void testPropertiesRespectCaseSensitivity()
	{
		Properties properties = new Properties();
		properties.setProperty("MixedCase", "value");

		assertEquals("value", new PropertyFile(properties).get("mixedcase", null));
		PropertyFile sensitive = new PropertyFile(properties, true);
		assertEquals("value", sensitive.get("MixedCase", null));
		assertFalse(sensitive.keySet().contains("mixedcase"));
	}


	@Test
	public void testLoadPropertiesOnlyCallsLoadedHook()
	{
		class ObservedPropertyFile extends PropertyFile
		{
			int loads;
			int additions;


			@Override
			protected void hook_loaded()
			{
				loads++;
				super.hook_loaded();
			}


			@Override
			protected void hook_added(String name, String value)
			{
				additions++;
				super.hook_added(name, value);
			}
		}
		ObservedPropertyFile file = new ObservedPropertyFile();
		Properties properties = new Properties();
		properties.setProperty("name", "value");

		file.load(properties);

		assertEquals(1, file.loads);
		assertEquals(0, file.additions);
		assertFalse(file.dirty);
	}
}
