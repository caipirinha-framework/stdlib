package com.peterphi.std.guice.hibernate.webquery.impl;

import com.google.common.reflect.TypeToken;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves persistent members in their owning Java type. Hibernate can report an
 * abstract generic getter instead of its concrete override, so its member alone
 * does not describe either the effective annotations or the resolved value type.
 */
final class PersistentAttributeMetadata
{
	private final List<AnnotatedElement> declarations;
	private final TypeToken<?> valueType;

	PersistentAttributeMetadata(TypeToken<?> owner, Attribute<?, ?> attribute)
	{
		Member member = attribute.getJavaMember();
		declarations = new ArrayList<>();
		if (member instanceof Method method)
		{
			collectOverrides(owner.getRawType(), method, declarations, new HashSet<>());
			if (!declarations.contains(method))
				declarations.add(method);
			Method effective = (Method) declarations.getFirst();
			valueType = preferConcrete(owner.resolveType(effective.getGenericReturnType()), attribute.getJavaType());
		}
		else if (member instanceof Field field)
		{
			// Fields do not override one another: a shadowing field is a different property.
			declarations.add(field);
			valueType = preferConcrete(owner.resolveType(field.getGenericType()), attribute.getJavaType());
		}
		else
		{
			valueType = TypeToken.of(attribute.getJavaType());
		}
	}

	private static TypeToken<?> preferConcrete(TypeToken<?> resolved, Class<?> metamodelType)
	{
		Class<?> raw = resolved.getRawType();
		if (metamodelType != raw && raw.isAssignableFrom(metamodelType))
		{
			// targetClass can specialise an interface or generic superclass. Inferring
			// the subtype retains its type arguments and keeps concrete getter annotations.
			return subtypeContext(resolved, metamodelType).getSubtype(metamodelType);
		}
		return resolved;
	}

	private static TypeToken<?> subtypeContext(TypeToken<?> context, Class<?> subtype)
	{
		// Guava infers subtypes from parameterised declarations, but upper-bounded
		// wildcards/type variables must first expose their concrete supertype bound.
		Set<Type> visited = new HashSet<>();
		while (visited.add(context.getType()))
		{
			Type[] bounds;
			if (context.getType() instanceof TypeVariable<?> variable)
				bounds = variable.getBounds();
			else if (context.getType() instanceof WildcardType wildcard && wildcard.getLowerBounds().length == 0)
				bounds = wildcard.getUpperBounds();
			else
				break;
			TypeToken<?> best = null;
			for (Type bound : bounds)
			{
				TypeToken<?> candidate = context.resolveType(bound);
				if (candidate.getRawType().isAssignableFrom(subtype) &&
				    (best == null || (candidate.getType() instanceof ParameterizedType && !(best.getType() instanceof ParameterizedType))))
					best = candidate;
			}
			if (best == null)
				break;
			context = best;
		}
		return context;
	}

	private static void collectOverrides(Class<?> type, Method original, List<AnnotatedElement> methods, Set<Class<?>> visited)
	{
		if (type == null || !visited.add(type))
			return;
		if (original.getDeclaringClass().isAssignableFrom(type) || type.isAssignableFrom(original.getDeclaringClass()))
		{
			for (Method candidate : type.getDeclaredMethods())
			{
				if (!candidate.isBridge() && !candidate.isSynthetic() && !Modifier.isStatic(candidate.getModifiers()) &&
				    candidate.getName().equals(original.getName()) &&
				    java.util.Arrays.equals(candidate.getParameterTypes(), original.getParameterTypes()) &&
				    (candidate.equals(original) ||
				     (original.getDeclaringClass().isAssignableFrom(type) ? canOverride(original, candidate) : canOverride(candidate, original))))
					methods.add(candidate);
			}
		}
		collectOverrides(type.getSuperclass(), original, methods, visited);
		for (Class<?> iface : type.getInterfaces())
			collectOverrides(iface, original, methods, visited);
	}

	private static boolean canOverride(Method original, Method candidate)
	{
		int modifiers = original.getModifiers();
		return !Modifier.isPrivate(modifiers) &&
		       (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers) ||
		        original.getDeclaringClass().getPackageName().equals(candidate.getDeclaringClass().getPackageName()));
	}

	TypeToken<?> getValueType()
	{
		return valueType;
	}

	TypeToken<?> getElementType(PluralAttribute<?, ?, ?> attribute)
	{
		TypeToken<?> element;
		if (Map.class.isAssignableFrom(valueType.getRawType()))
			element = valueType.resolveType(Map.class.getTypeParameters()[1]);
		else if (Collection.class.isAssignableFrom(valueType.getRawType()))
			element = valueType.resolveType(Collection.class.getTypeParameters()[0]);
		else
			element = TypeToken.of(attribute.getElementType().getJavaType());
		return preferConcrete(element, attribute.getElementType().getJavaType());
	}

	<A extends Annotation> A getAnnotation(Class<A> annotationType)
	{
		for (AnnotatedElement declaration : declarations)
		{
			A annotation = declaration.getAnnotation(annotationType);
			if (annotation != null)
				return annotation;
		}
		return null;
	}
}
