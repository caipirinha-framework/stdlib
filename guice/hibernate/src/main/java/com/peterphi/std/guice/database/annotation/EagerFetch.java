package com.peterphi.std.guice.database.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on a member within a {@link jakarta.persistence.Entity}-annotated class to indicate that this should be
 * eager-fetched when querying. This is a replacement for fetch=EAGER on the JPA annotations, because of <a href="https://hibernate.atlassian.net/browse/HHH-8776"></a>limitations in Hibernate
 * since 2013</a> that prevent a FetchGraph from overriding fetch=EAGER annotated relations (which means that a user who only wants
 * particular relations expanded must pay a time penalty for the EAGER-annotated relations to be retrieved too)
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EagerFetch
{
}
