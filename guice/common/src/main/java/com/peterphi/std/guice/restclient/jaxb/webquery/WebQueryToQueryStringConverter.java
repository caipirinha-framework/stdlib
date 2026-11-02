package com.peterphi.std.guice.restclient.jaxb.webquery;

import org.apache.commons.lang.StringUtils;

import jakarta.ws.rs.core.MultivaluedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts a WebQuery to the parameters for a query string query
 */
class WebQueryToQueryStringConverter
{
	/**
	 * Convert a WebQueryDefinition to the equivalent legacy ResultSetConstraint (if possible)
	 *
	 * @param query
	 * @return
	 * @throws IllegalArgumentException if the provided query definition cannot be represented using legacy semantics
	 */
	public static Map<String, List<String>> convert(WebQuery query)
	{
		MultivaluedHashMap<String, String> map = new MultivaluedHashMap<>();

		map.putSingle(WQUriControlField.FETCH.getName(), query.fetch);

		if (query.dbfetch != null)
			map.putSingle(WQUriControlField.DBFETCH.getName(), query.dbfetch);

		if (query.expand != null)
			map.put(WQUriControlField.EXPAND.getName(), list(query.expand));

		if (query.getOffset() > 0)
			map.putSingle(WQUriControlField.OFFSET.getName(), String.valueOf(query.getOffset()));

		map.putSingle(WQUriControlField.LIMIT.getName(), String.valueOf(query.getLimit()));

		if (query.constraints.computeSize)
			map.putSingle(WQUriControlField.COMPUTE_SIZE.getName(), "true");
		if (query.constraints.subclass != null)
			map.put(WQUriControlField.CLASS.getName(), list(query.constraints.subclass));


		if (isSimpleQuery(query.constraints))
		{
			map.put(WQUriControlField.ORDER.getName(),
			        query.orderings.stream().map(WQOrder :: toLegacyForm).collect(Collectors.toList()));

			addConstraints(map, query.constraints);
		}
		else
		{
			// Constraints and Order in text form
			map.put(WQUriControlField.TEXT_QUERY.getName(), Collections.singletonList(query.toQueryFragment()));
		}

		return map;
	}


	private static boolean isSimpleQuery(final WQConstraints constraints)
	{
		Set<String> fieldNames = new HashSet<>();

		for (WQConstraintLine item : constraints.constraints)
		{
			if (item instanceof WQConstraint)
			{
				final WQConstraint c = (WQConstraint) item;

				if (!fieldNames.add(c.field))
					return false; // field name referenced multiple times
				else if (c.valuelist != null)
					return false; // uses IN/NOT_IN constraint
			}
			else if (item instanceof WQGroup)
			{
				WQGroup g = (WQGroup) item;

				if (g.operator != WQGroupType.OR)
					return false; // Can only convert OR groups
				else if (!g.constraints.stream().allMatch(l -> l instanceof WQConstraint && ((WQConstraint) l).valuelist == null))
					return false; // Must all be regular constraints (no nested groups) and have no constraints with a valuelist (e.g. IN/NOT_IN)
				else
				{
					final Set<String> namesInGroup = g.constraints
							                                 .stream()
							                                 .map(l -> ((WQConstraint) l).field)
							                                 .collect(Collectors.toSet());

					if (namesInGroup.size() != 1)
						return false; // multiple field names referenced in group
					else if (!fieldNames.addAll(namesInGroup))
						return false; // field name already referenced
				}
			}
			else
			{
				return false; // unrecognised constraint type
			}
		}

		return true; // all checks passed
	}


	private static void addConstraints(final MultivaluedHashMap<String, String> builder, final WQConstraints constraints)
	{
		for (WQConstraintLine line : constraints.constraints)
		{
			if (line instanceof WQConstraint)
			{
				WQConstraint c = (WQConstraint) line;

				builder.add(c.field, c.encodeValue());
			}
			else if (line instanceof WQGroup)
			{
				WQGroup g = (WQGroup) line;

				g.constraints.stream().map(l -> (WQConstraint) l).forEach(c -> builder.add(c.field, c.encodeValue()));
			}
		}
	}


	private static List<String> list(String s)
	{
		if (StringUtils.isBlank(s))
			return Collections.emptyList();
		else
			return new ArrayList<>(Arrays.asList(s.split(",")));
	}
}
