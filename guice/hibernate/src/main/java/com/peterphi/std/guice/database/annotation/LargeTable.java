package com.peterphi.std.guice.database.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on an {@link jakarta.persistence.Entity}-annotated class to indicate that special measures should be
 * taken when querying against this entity because the backing table will be large.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LargeTable
{
}
