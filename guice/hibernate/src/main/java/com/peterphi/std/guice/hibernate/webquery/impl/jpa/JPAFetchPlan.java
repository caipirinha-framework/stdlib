package com.peterphi.std.guice.hibernate.webquery.impl.jpa;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.hibernate.Hibernate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Avoids multiplying bag elements by fetching another collection in the same SQL result. */
final class JPAFetchPlan
{
	record Step(ManagedType<?> ownerType, Attribute<?, ?> attribute) {}
	record Path(List<Step> steps) {}

	private final List<Path> paths;
	private final boolean separateLoading;
	private final boolean possibleBag;

	JPAFetchPlan(ManagedType<?> rootType, Set<String> fetches, List<Path> directPaths)
	{
		this.paths = new ArrayList<>(directPaths);
		if (fetches != null)
			for (String fetch : fetches.stream().sorted().toList())
			{
				final List<Step> steps = new ArrayList<>();
				Type<?> type = rootType;
				for (String part : fetch.split("\\."))
				{
					final ManagedType<?> managed = (ManagedType<?>) type;
					final Attribute<?, ?> attribute = managed.getAttribute(part);
					steps.add(new Step(managed, attribute));
					type = attribute instanceof PluralAttribute<?, ?, ?> plural ? plural.getElementType() :
					       ((SingularAttribute<?, ?>) attribute).getType();
				}
				paths.add(new Path(List.copyOf(steps)));
			}
		final Set<String> collections = new HashSet<>();
		boolean bag = false;
		for (Path path : paths)
		{
			String prefix = "";
			for (Step step : path.steps())
			{
				prefix += "." + step.ownerType().getJavaType().getName() + ":" + step.attribute().getName();
				if (step.attribute() instanceof PluralAttribute<?, ?, ?> plural)
				{
					collections.add(prefix);
					bag |= plural.getCollectionType() == PluralAttribute.CollectionType.LIST ||
					       plural.getCollectionType() == PluralAttribute.CollectionType.COLLECTION;
				}
			}
		}
		possibleBag = bag;
		separateLoading = bag && collections.size() > 1;
	}

	boolean hasListOrCollectionFetch()
	{
		return possibleBag;
	}

	boolean requiresSeparateLoading()
	{
		return separateLoading;
	}

	/**
	 * Initialise each requested path using Hibernate's collection loaders. These
	 * secondary selects preserve real bag duplicates; SQL DISTINCT cannot do that.
	 * They may execute once per owner when batching is not configured.
	 */
	void initialise(Object root)
	{
		for (Path path : paths)
			initialise(root, path.steps(), 0);
	}

	private void initialise(Object owner, List<Step> path, int index)
	{
		if (owner == null)
			return;
		owner = Hibernate.unproxy(owner);
		final Step step = path.get(index);
		// A treated fetch applies only to that subtype, including within collections.
		if (!step.ownerType().getJavaType().isInstance(owner))
			return;
		final var attribute = step.attribute();
		final Object value;
		try
		{
			if (attribute.getJavaMember() instanceof Field field)
			{
				if (!field.canAccess(owner))
					field.setAccessible(true);
				value = field.get(owner);
			}
			else if (attribute.getJavaMember() instanceof Method method)
			{
				if (!method.canAccess(owner))
					method.setAccessible(true);
				value = method.invoke(owner);
			}
			else
				throw new IllegalStateException("Persistent attribute has no readable Java member: " + attribute);
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException("Cannot read persistent attribute " + attribute, e);
		}
		Hibernate.initialize(value);
		if (index + 1 == path.size() || value == null)
			return;
		if (attribute instanceof PluralAttribute<?, ?, ?> plural)
		{
			final Collection<?> elements = value instanceof Map<?, ?> map ? map.values() : (Collection<?>) value;
			for (Object element : elements)
				initialise(element, path, index + 1);
		}
		else
			initialise(value, path, index + 1);
	}
}
