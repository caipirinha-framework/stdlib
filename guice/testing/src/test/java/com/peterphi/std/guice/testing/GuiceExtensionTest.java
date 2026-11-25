package com.peterphi.std.guice.testing;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.common.shutdown.iface.ShutdownManager;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.TestConfig;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.TestModule;
import com.peterphi.std.io.PropertyFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Execution(ExecutionMode.SAME_THREAD)
class GuiceExtensionTest
{
	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void injectsLifecycleMethodsAndStopsEachInjector(boolean closeAutoCloseableValues)
	{
		FixtureConfig.configLoads = 0;
		Fixture.injectors.clear();
		Fixture.stopped = 0;
		Fixture.afterEachCalls = 0;

		final var listener = new SummaryGeneratingListener();
		LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request()
		                                                .selectors(selectClass(Fixture.class))
		                                                .configurationParameter(
		                                                        "junit.jupiter.extensions.store.close.autocloseable.enabled",
		                                                        Boolean.toString(closeAutoCloseableValues))
		                                                .build(), listener);

		final var summary = listener.getSummary();
		assertEquals(1, summary.getTestsSucceededCount());
		assertEquals(List.of("failed in setup"), summary.getFailures().stream()
		                                              .map(failure -> failure.getException().getMessage())
		                                              .toList());
		assertEquals(1, FixtureConfig.configLoads);
		assertEquals(2, Fixture.injectors.size());
		assertEquals(2, Fixture.stopped);
		assertEquals(2, Fixture.afterEachCalls);
	}


	@Test
	void validatesAllConfigProvidersBeforeInvokingThem()
	{
		final var failure = assertThrows(IllegalArgumentException.class,
		                                 () -> GuiceRegistryBuilder.createRegistry(ConfigProvidingFixture.class));
		assertTrue(failure.getMessage().contains("invalidConfig"));
	}


	@Test
	void rejectsNullModule()
	{
		final var failure = assertThrows(IllegalArgumentException.class,
		                                 () -> GuiceRegistryBuilder.createRegistry(NullModuleProvider.class));
		assertTrue(failure.getMessage().contains("missingModule"));
	}


	static class InvalidConfigProvider
	{
		@TestConfig
		PropertyFile invalidConfig()
		{
			return new PropertyFile();
		}
	}


	static class ConfigProvidingFixture extends InvalidConfigProvider
	{
		@TestConfig
		static PropertyFile properties()
		{
			throw new AssertionError("config providers must be validated before they are called");
		}
	}


	static class NullModuleProvider
	{
		@TestModule
		static Module missingModule()
		{
			return null;
		}
	}


	static class FixtureConfig
	{
		private static int configLoads;

		@TestConfig
		private static PropertyFile properties()
		{
			configLoads++;
			final var file = new PropertyFile();
			file.set("test.fixture", "inherited");
			return file;
		}

		@TestModule
		static Module messages()
		{
			throw new AssertionError("the subclass provider replaces this one");
		}
	}


	@GuiceTest
	static class Fixture extends FixtureConfig
	{
		private static final Set<ShutdownManager> injectors = new HashSet<>();
		private static int stopped;
		private static int afterEachCalls;
		private final ShutdownManager shutdown;

		@Inject
		Fixture(ShutdownManager shutdown)
		{
			this.shutdown = shutdown;
			injectors.add(shutdown);
			shutdown.register(() -> stopped++);
		}

		@TestModule
		static Module messages()
		{
			return binder -> binder.bind(Key.get(new TypeLiteral<List<String>>() {}, Names.named("messages")))
			                       .toInstance(List.of("hello"));
		}

		@BeforeEach
		void setup(@Named("messages") List<String> messages, GuiceConfig config, TestInfo info)
		{
			assertEquals(List.of("hello"), messages);
			assertEquals("inherited", config.get("test.fixture"));
			assertEquals("true", config.get(GuiceProperties.UNIT_TEST));
			if (info.getTestMethod().orElseThrow().getName().equals("failsInSetup"))
				fail("failed in setup");
		}

		@Test
		@Doc("Guice supplies the fixture")
		void runsWithGuice(@Named("messages") List<String> messages, TestInfo info)
		{
			assertEquals(List.of("hello"), messages);
			assertEquals("Guice supplies the fixture", info.getDisplayName());
		}

		@Test
		void failsInSetup()
		{
			fail("the test body should not run");
		}

		@AfterEach
		void teardown(ShutdownManager injectedShutdown)
		{
			assertEquals(shutdown, injectedShutdown);
			afterEachCalls++;
		}
	}
}
