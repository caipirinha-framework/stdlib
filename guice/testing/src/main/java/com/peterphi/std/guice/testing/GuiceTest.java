package com.peterphi.std.guice.testing;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs a Jupiter test class with instances and dependencies supplied by Guice.
 * Each test runs with a fresh injector using the class's Guice configuration.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(GuiceExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@Execution(ExecutionMode.SAME_THREAD)
@DisplayNameGeneration(GuiceExtension.DisplayNames.class)
public @interface GuiceTest
{
}
