package com.peterphi.std.guice.common.serviceprops.jaxbref;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import com.peterphi.std.util.jaxb.exception.JAXBRuntimeException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that {@link JAXBResourceProvider} only hands back a new object when the underlying config has actually changed (so that
 * users can detect a config reload by comparing object references)
 */
public class JAXBResourceProviderTest
{
	private static final String PROPERTY = "some.xml";

	JAXBSerialiserFactory SERIALISER_FACTORY = new JAXBSerialiserFactory(false);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final HealthCheckRegistry healthChecks = new HealthCheckRegistry();

	/**
	 * Used to make sure file timestamps always move forwards (filesystem timestamp granularity can be coarse)
	 */
	private long timestamp = System.currentTimeMillis();


	private JAXBResourceProvider<MyType> createProvider(final GuiceConfig config)
	{
		final JAXBResourceFactory factory = new JAXBResourceFactory(config, SERIALISER_FACTORY);

		final JAXBResourceProvider<MyType> provider = new JAXBResourceProvider<>(() -> factory,
		                                                                         () -> healthChecks,
		                                                                         PROPERTY,
		                                                                         MyType.class,
		                                                                         null);

		// Disable the provider-level cache so that every get() consults the underlying config
		provider.setCacheValidity(null);

		return provider;
	}


	private File writeConfig(final File file, final String contents) throws Exception
	{
		Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));

		return touch(file);
	}


	private File touch(final File file)
	{
		file.setLastModified(this.timestamp += 5000);

		return file;
	}


	private HealthCheck.Result runHealthCheck()
	{
		final Map<String, HealthCheck.Result> results = healthChecks.runHealthChecks();

		assertEquals("expected exactly one registered health check", 1, results.size());

		return results.values().iterator().next();
	}


	@Test
	public void testUnchangedFileReturnsSameInstance() throws Exception
	{
		final File file = writeConfig(folder.newFile("mytype.xml"), "<MyType name=\"test\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		// Touch the file without changing its contents
		touch(file);

		final MyType b = provider.get();

		assertEquals(new MyType("test", true), a);
		assertSame("unchanged config file should keep returning the same object", a, b);
	}


	@Test
	public void testChangedFileReturnsNewInstance() throws Exception
	{
		final File file = writeConfig(folder.newFile("mytype.xml"), "<MyType name=\"test1\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		writeConfig(file, "<MyType name=\"test2\"/>");

		final MyType b = provider.get();

		assertEquals(new MyType("test1", true), a);
		assertEquals(new MyType("test2", true), b);
		assertNotSame("changed config file should return a new object", a, b);
	}


	@Test
	public void testUnchangedLiteralReturnsSameInstance() throws Exception
	{
		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, "<MyType name=\"test\"/>");

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		assertSame("unchanged literal config should keep returning the same object", provider.get(), provider.get());
	}


	@Test
	public void testChangedLiteralReturnsNewInstance() throws Exception
	{
		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, "<MyType name=\"test1\"/>");

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		config.set(PROPERTY, "<MyType name=\"test2\"/>");

		final MyType b = provider.get();

		assertEquals(new MyType("test2", true), b);
		assertNotSame("changed literal config should return a new object", a, b);
	}


	@Test
	public void testHealthCheckDoesNotCreateNewInstances() throws Exception
	{
		final File file = writeConfig(folder.newFile("mytype.xml"), "<MyType name=\"test\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		assertTrue("health check should be healthy for a valid config", runHealthCheck().isHealthy());

		assertSame("health check should not cause a new object to be created", a, provider.get());
	}


	@Test
	public void testUnparseableFileFailsHealthCheck() throws Exception
	{
		final File file = writeConfig(folder.newFile("mytype.xml"), "<MyType name=\"test\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		provider.get();

		writeConfig(file, "this is not xml");

		try
		{
			provider.get();

			fail("expected an error deserialising an unparseable config file");
		}
		catch (JAXBRuntimeException e)
		{
			// Expected
		}

		assertFalse("health check should be unhealthy for an unparseable config", runHealthCheck().isHealthy());
	}
}
