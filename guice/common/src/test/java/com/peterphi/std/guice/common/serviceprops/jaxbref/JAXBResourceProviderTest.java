package com.peterphi.std.guice.common.serviceprops.jaxbref;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import com.peterphi.std.util.jaxb.exception.JAXBRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests that {@link JAXBResourceProvider} only hands back a new object when the underlying config has actually changed (so that
 * users can detect a config reload by comparing object references)
 */
public class JAXBResourceProviderTest
{
	private static final String PROPERTY = "some.xml";

	JAXBSerialiserFactory SERIALISER_FACTORY = new JAXBSerialiserFactory(false);

	@TempDir
	File folder;

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

		assertEquals(1, results.size(), "expected exactly one registered health check");

		return results.values().iterator().next();
	}


	@Test
	public void testUnchangedFileReturnsSameInstance() throws Exception
	{
		final File file = writeConfig(new File(folder, "mytype.xml"), "<MyType name=\"test\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		// Touch the file without changing its contents
		touch(file);

		final MyType b = provider.get();

		assertEquals(new MyType("test", true), a);
		assertSame(a, b, "unchanged config file should keep returning the same object");
	}


	@Test
	public void testChangedFileReturnsNewInstance() throws Exception
	{
		final File file = writeConfig(new File(folder, "mytype.xml"), "<MyType name=\"test1\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		writeConfig(file, "<MyType name=\"test2\"/>");

		final MyType b = provider.get();

		assertEquals(new MyType("test1", true), a);
		assertEquals(new MyType("test2", true), b);
		assertNotSame(a, b, "changed config file should return a new object");
	}


	@Test
	public void testUnchangedLiteralReturnsSameInstance() throws Exception
	{
		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, "<MyType name=\"test\"/>");

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		assertSame(provider.get(), provider.get(), "unchanged literal config should keep returning the same object");
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
		assertNotSame(a, b, "changed literal config should return a new object");
	}


	@Test
	public void testHealthCheckDoesNotCreateNewInstances() throws Exception
	{
		final File file = writeConfig(new File(folder, "mytype.xml"), "<MyType name=\"test\"/>");

		final GuiceConfig config = new GuiceConfig();
		config.set(PROPERTY, file.getAbsolutePath());

		final JAXBResourceProvider<MyType> provider = createProvider(config);

		final MyType a = provider.get();

		assertTrue(runHealthCheck().isHealthy(), "health check should be healthy for a valid config");

		assertSame(a, provider.get(), "health check should not cause a new object to be created");
	}


	@Test
	public void testUnparseableFileFailsHealthCheck() throws Exception
	{
		final File file = writeConfig(new File(folder, "mytype.xml"), "<MyType name=\"test\"/>");

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

		assertFalse(runHealthCheck().isHealthy(), "health check should be unhealthy for an unparseable config");
	}
}
