package com.peterphi.std.guice.testing;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.apploader.impl.GuiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstanceFactory;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstantiationException;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.store.Namespace;
import org.junit.platform.commons.support.AnnotationSupport;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

final class GuiceExtension implements BeforeAllCallback, TestInstanceFactory, ParameterResolver
{
	private static final Namespace NAMESPACE = Namespace.create(GuiceExtension.class);


	@Override
	public void beforeAll(final ExtensionContext context)
	{
		context.getStore(NAMESPACE).put(GuiceRegistry.class, GuiceRegistryBuilder.createRegistry(context.getRequiredTestClass()));
	}


	@Override
	public ExtensionContextScope getTestInstantiationExtensionContextScope(final ExtensionContext context)
	{
		return ExtensionContextScope.TEST_METHOD;
	}


	@Override
	public Object createTestInstance(final TestInstanceFactoryContext factoryContext, final ExtensionContext context)
	{
		if (context.getTestMethod().isEmpty())
			throw new TestInstantiationException("@GuiceTest requires the PER_METHOD test instance lifecycle");
		if (context.getExecutionMode() == ExecutionMode.CONCURRENT)
			throw new TestInstantiationException("@GuiceTest requires SAME_THREAD execution");

		final var store = context.getStore(NAMESPACE);
		final GuiceRegistry registry = store.get(GuiceRegistry.class, GuiceRegistry.class);
		// The store closes resources in reverse order, including when provisioning fails.
		store.put(Mockito.class, (Cleanup) Mockito::validateMockitoUsage);
		store.put(GuiceExtension.class, (Cleanup) registry::stop);
		return registry.getInjector().getInstance(factoryContext.getTestClass());
	}


	// Keep closing these resources even when Jupiter disables automatic closing of AutoCloseable values.
	@FunctionalInterface
	@SuppressWarnings("deprecation")
	private interface Cleanup extends AutoCloseable, CloseableResource
	{
		@Override
		void close();
	}


	@Override
	public boolean supportsParameter(final ParameterContext parameter, final ExtensionContext context)
	{
		if (!(parameter.getDeclaringExecutable() instanceof Method method) || Modifier.isStatic(method.getModifiers()))
			return false;

		final Class<?> type = parameter.getParameter().getType();
		if (type == TestInfo.class || type == TestReporter.class || parameter.isAnnotated(TempDir.class))
			return false;

		return AnnotationSupport.isAnnotated(method, Test.class) ||
		       AnnotationSupport.isAnnotated(method, BeforeEach.class) ||
		       AnnotationSupport.isAnnotated(method, AfterEach.class);
	}


	@Override
	public Object resolveParameter(final ParameterContext parameter, final ExtensionContext context)
	{
		final var executable = parameter.getDeclaringExecutable();
		final TypeLiteral<?> type = TypeLiteral.get(executable.getGenericParameterTypes()[parameter.getIndex()]);
		try
		{
			final Key<?> key = Annotations.getKey(type, executable, parameter.getParameter().getAnnotations(), new Errors(executable));
			final GuiceRegistry registry = context.getStore(NAMESPACE).get(GuiceRegistry.class, GuiceRegistry.class);
			return registry.getInjector().getInstance(key);
		}
		catch (ErrorsException e)
		{
			throw new ParameterResolutionException("Invalid Guice binding for " + parameter.getParameter(), e);
		}
	}


	public static final class DisplayNames extends DisplayNameGenerator.Standard
	{
		@Override
		public String generateDisplayNameForMethod(final List<Class<?>> enclosingClasses, final Class<?> testClass,
		                                           final Method method)
		{
			final Doc doc = method.getAnnotation(Doc.class);
			final String label = doc == null ? "" : String.join("\n", doc.value());
			return label.isBlank() ? super.generateDisplayNameForMethod(enclosingClasses, testClass, method) : label;
		}
	}
}
