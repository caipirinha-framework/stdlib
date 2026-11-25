package com.peterphi.std.guice.testing.com.peterphi.std.guice.testing.annotations;

import com.peterphi.std.guice.apploader.GuiceRole;
import com.peterphi.std.guice.apploader.GuiceSetup;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that describes how to customise the test environment for a test run with {@link
 * com.peterphi.std.guice.testing.GuiceTest}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GuiceConfig
{
	/**
	 * The configuration resources to search for in the classpath
	 *
	 * @return
	 */
	String[] config() default {};

	/**
	 * Recursively scan the listed packages
	 *
	 * @return
	 */
	String[] packages() default {};

	/**
	 * Recursively scan the package containing the named classes
	 *
	 * @return
	 */
	Class[] classPackages() default {};

	/**
	 * The (at most one) {@link com.peterphi.std.guice.apploader.GuiceSetup} instance to use. If not specified then an empty
	 * {@link com.peterphi.std.guice.apploader.BasicSetup} will be used. The class named here <strong>must</strong> have a
	 * no-argument constructor.
	 *
	 * @return
	 */
	Class<? extends GuiceSetup>[] setup() default {};

	/**
	 * The {@link com.peterphi.std.guice.apploader.GuiceRole} classes to instantiate and register when building the guice
	 * environment. The classes named here <strong>must</strong> have a no-argument constructor.
	 *
	 * @return
	 */
	Class<? extends GuiceRole>[] role() default {};

	/**
	 * If set to false then {@link com.peterphi.std.guice.apploader.GuiceRole}s will not be automatically loaded using the
	 * Service
	 * Provider Interface. Any desired roles must then be manually managed through {@link #role()}
	 *
	 * @return
	 */
	boolean autoLoadRoles() default true;
}
