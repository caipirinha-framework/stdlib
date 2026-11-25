package com.peterphi.std.guice.testing;

import com.google.inject.Module;
import com.peterphi.std.guice.apploader.BasicSetup;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.apploader.GuiceRole;
import com.peterphi.std.guice.apploader.impl.GuiceBuilder;
import com.peterphi.std.guice.apploader.impl.GuiceRegistry;
import com.peterphi.std.guice.common.ClassScannerFactory;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.Automock;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.GuiceConfig;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.TestConfig;
import com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations.TestModule;
import com.peterphi.std.io.PropertyFile;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ReflectionSupport;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.platform.commons.support.HierarchyTraversalMode.BOTTOM_UP;

class GuiceRegistryBuilder
{
	public static GuiceRegistry createRegistry(Class<?> clazz)
	{
		final GuiceConfig config = clazz.getAnnotation(GuiceConfig.class);
		if (config != null && config.setup().length > 1)
			throw new IllegalArgumentException("May only have a maximum of 1 setup class for @GuiceConfig annotation!");

		GuiceRegistry registry = new GuiceRegistry();

		ClassScannerFactory scanner = null;
		GuiceRole[] roles = null;
		if (config != null)
		{
			if (config.packages().length > 0 || config.classPackages().length > 0)
			{
				Set<String> packages = new HashSet<>();

				packages.addAll(Arrays.asList(config.packages()));

				for (Class<?> c : config.classPackages())
					packages.add(c.getPackage().getName());

				scanner = new ClassScannerFactory(packages.toArray(new String[0]));
			}

			if (config.role().length > 0)
			{
				List<GuiceRole> instances = new ArrayList<>();

				for (Class<? extends GuiceRole> role : config.role())
				{
					try
					{
						instances.add(role.getDeclaredConstructor().newInstance());
					}
					catch (Exception e)
					{
						throw new IllegalArgumentException("Error instantiating GuiceRole " + role, e);
					}
				}

				roles = instances.toArray(new GuiceRole[0]);
			}
		}

		GuiceBuilder builder = registry.getBuilder();

		if (scanner != null)
			builder.withScannerFactory(scanner);
		else
			builder.withNoScannerFactory();

		if (config != null && config.config().length > 0)
			builder.withConfig(config.config());

		if (config != null)
			builder.withAutoLoadRoles(config.autoLoadRoles());

		if (roles != null)
			builder.withRole(roles);

		for (Method method : findProviders(clazz, TestConfig.class, Properties.class, PropertyFile.class))
		{
			final Object source = ReflectionSupport.invokeMethod(method, null);
			if (source instanceof Properties properties)
				builder.withConfig(properties);
			else if (source instanceof PropertyFile file)
				builder.withConfig(file);
		}

		final List<Module> modules = new ArrayList<>();
		for (Method method : findProviders(clazz, TestModule.class, Module.class))
		{
			final Module module = (Module) ReflectionSupport.invokeMethod(method, null);
			if (module == null)
				throw new IllegalArgumentException("Test module provider returned null: " + method);

			modules.add(module);
		}
		builder.withRole(new ModuleAddingGuiceRole(modules));

		final Set<String> fieldNames = new HashSet<>();
		final List<Field> fields = AnnotationSupport.findAnnotatedFields(clazz, Automock.class,
		                                                                field -> !field.getDeclaringClass().isInterface(),
		                                                                BOTTOM_UP).stream()
		                                           .filter(field -> fieldNames.add(field.getName()))
		                                           .toList();
		if (!fields.isEmpty())
			builder.withRole(new ModuleAddingGuiceRole(new AutomockAnnotatedMockModule(clazz, fields)));

		// Make sure we set the unit test property so roles are aware they're running in a unit test (e.g. so they don't auto-register REST services)
		{
			PropertyFile props = new PropertyFile();
			props.set(GuiceProperties.UNIT_TEST, "true");
			builder.withConfig(props);
		}

		// Add the Setup class, or if none is specified then add local modules:
		if (config != null && config.setup().length > 0)
		{
			builder.withSetup(config.setup()[0]);
		}
		else
		{
			builder.withSetup(new BasicSetup());
		}

		return registry;
	}


	private static List<Method> findProviders(final Class<?> clazz, final Class<? extends Annotation> annotation,
	                                          final Class<?>... returnTypes)
	{
		// The nearest annotated declaration wins when a superclass provider is hidden.
		final Set<String> names = new HashSet<>();
		final List<Method> methods = AnnotationSupport.findAnnotatedMethods(clazz, annotation, BOTTOM_UP).stream()
		                                             .filter(method -> !method.getDeclaringClass().isInterface())
		                                             .filter(method -> method.getParameterCount() != 0 || names.add(method.getName()))
		                                             .toList();

		final List<Class<?>> allowedReturnTypes = Arrays.asList(returnTypes);
		for (Method method : methods)
		{
			if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0)
				throw new IllegalArgumentException("Test provider must be a static no-argument method: " + method);

			if (!allowedReturnTypes.contains(method.getReturnType()))
				throw new IllegalArgumentException("Test provider " + method + " must return one of " + allowedReturnTypes);
		}

		return methods;
	}
}
