package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.restclient.jaxb.webquery.plugin.WebQueryDecodePlugin;
import org.apache.commons.lang.StringUtils;

import jakarta.ws.rs.core.UriInfo;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes a database query to be executed
 */
@XmlRootElement(name = "WebQueryDefinition")
@XmlType(name = "QueryDefinitionType")
@Doc(value = "Generic Web Query", href = "https://stdlib.readthedocs.io/en/latest/framework/webquery.html")
public class WebQuery implements ConstraintContainer<WebQuery>, Cloneable
{
	private static final int QUERY_STRING_DEFAULT_LIMIT = 200;

	/**
	 * Special limit value used to request the returning of 0 data rows (will still return count if requested)
	 */
	public static final int LIMIT_RETURN_ZERO = -1;

	private transient boolean wasLimitSetExplicitly = false;
	private transient int capLimit = Integer.MAX_VALUE;

	/**
	 * An optional name for the query, to allow server-side optimisation/hinting
	 */
	@XmlAttribute
	public String name;

	/**
	 * What to fetch: should be "entity" or "id".
	 */
	@XmlAttribute
	public String fetch = "entity";

	/**
	 * Comma-separated list of relations to fetch from the database as part of the query
	 */
	@XmlAttribute
	public String dbfetch;


	/**
	 * What relationships to expand (by default, all relationships are expanded)
	 */
	@XmlAttribute
	public String expand;

	@XmlAttribute
	public boolean logSQL = false;

	@XmlAttribute
	public Boolean logPerformance;

	@XmlElement
	public WQConstraints constraints = new WQConstraints();

	@XmlElementWrapper(name = "ordering")
	@XmlElement(name = "order")
	public List<WQOrder> orderings = new ArrayList<>(1);


	public WebQuery expand(String... relationships)
	{
		this.expand = String.join(",", relationships);

		return this;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Convenience getters
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	public int getOffset()
	{
		return constraints.offset;
	}


	public int getLimit()
	{
		return constraints.limit;
	}


	public String getFetch()
	{
		return this.fetch;
	}


	public Set<String> getDBFetch()
	{
		if (this.dbfetch == null)
			return null;
		else
			return new HashSet<>(Arrays.asList(this.dbfetch.split(",")));
	}


	public Set<String> getExpand()
	{
		if (this.expand == null || this.expand.isEmpty())
			return null;
		else
			return new HashSet<>(Arrays.asList(expand.split(",")));
	}


	public boolean isComputeSize()
	{
		return constraints.computeSize;
	}


	public boolean isLogSQL()
	{
		return this.logSQL;
	}


	public boolean isLogPerformance()
	{
		if (logPerformance != null)
			return logPerformance;
		else
			return false;
	}


	/**
	 * Encode the constraints of this query to a readable string representation
	 *
	 * @return
	 */
	private String constraintsToQueryFragment()
	{
		return constraints.toQueryFragment();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Builder methods
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	public WebQuery subclass(String... subclasses)
	{
		if (subclasses == null || subclasses.length == 0)
			this.constraints.subclass = null;
		else
			this.constraints.subclass = String.join(",", subclasses);

		return this;
	}


	public WebQuery fetch(final String expression)
	{
		this.fetch = expression;

		return this;
	}


	public WebQuery offset(final int offset)
	{
		constraints.offset = offset;

		return this;
	}


	public WebQuery limit(final int limit)
	{
		this.wasLimitSetExplicitly = true;

		constraints.limit = limit;

		return this;
	}


	/**
	 * Cap the current limit value such that it does not exceed the specified limit. If the limit is 0, or greater than this cap, <code>limit</code> will be set to <code>cap</code><br />
	 * A capped limit will also be stored, and apply to any <code>_limit</code> decoded from query string by the {@link #decode(UriInfo)} methods.
	 *
	 * @param cap the maximum number of rows the user may request
	 * @return self for chaining
	 */
	public WebQuery capLimit(final int cap)
	{
		this.capLimit = cap;

		if (cap != Integer.MAX_VALUE && (constraints.limit == 0 || constraints.limit > cap))
			return limit(cap);
		else
			return this;
	}


	public WebQuery computeSize(final boolean computeSize)
	{
		constraints.computeSize = computeSize;

		return this;
	}


	public WebQuery order(WQOrder order)
	{
		if (orderings.stream().anyMatch(o -> StringUtils.equalsIgnoreCase(order.field, o.field)))
			throw new IllegalArgumentException("Cannot add field to order twice! Existing orderings " +
			                                   orderings +
			                                   ", new ordering: " +
			                                   order);

		orderings.add(order);

		return this;
	}


	public WebQuery orderAsc(final String field)
	{
		order(WQOrder.asc(field));

		return this;
	}


	public WebQuery orderDesc(final String field)
	{
		order(WQOrder.desc(field));

		return this;
	}


	public WebQuery logSQL(final boolean enabled)
	{
		this.logSQL = enabled;

		return this;
	}


	public WebQuery logPerformance(final boolean enabled)
	{
		this.logPerformance = enabled;

		return this;
	}


	public WebQuery dbfetch(final String... relations)
	{
		if (relations == null || relations.length == 0)
			this.dbfetch = null;
		else
			this.dbfetch = StringUtils.join(relations, ',');

		return this;
	}


	public WebQuery name(final String name)
	{
		this.name = name;

		return this;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Constraints
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	public WebQuery add(final WQConstraintLine line)
	{
		if (line != null)
			this.constraints.add(line);

		return this;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Decoding URI Query
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Overwrite any fields in this WebQuery using the query defined in the Query String of the provided UriInfo
	 *
	 * @param qs the UriInfo to extract the QueryParameters from
	 * @return this WebQuery for chaining
	 */
	public WebQuery decode(UriInfo qs)
	{
		return decode(qs.getQueryParameters());
	}


	public WebQuery decode(Map<String, List<String>> map)
	{
		return decode(map, null);
	}


	public WebQuery decode(UriInfo qs, WebQueryDecodePlugin parserPlugin)
	{
		return decode(qs.getQueryParameters(), parserPlugin);
	}


	/**
	 * Overwrite any fields in this WebQuery using the query defined in the provided map
	 *
	 * @param map a map of fields (or control fields) to encoded constraints
	 * @return this WebQuery for chaining
	 */
	public WebQuery decode(Map<String, List<String>> map, WebQueryDecodePlugin parserPlugin)
	{
		// incoming queries from a map get a default limit set if one has not already been set pre-decode
		// Make sure this limit does not exceed any cap that has been set
		// N.B. we set constraints.limit directly to avoid setting "wasLimitSetExplicitly"
		if (!wasLimitSetExplicitly)
			constraints.limit = Math.min(capLimit, QUERY_STRING_DEFAULT_LIMIT);

		boolean hasConstraints = false;

		// First, handle text query
		// This is so that a _order expression can be used to override the ordering in the text query
		{
			final List<String> textQuery = map.get(WQUriControlField.TEXT_QUERY.getName());
			if (textQuery != null)
			{
				if (textQuery.size() != 1)
					throw new IllegalArgumentException("May only have one TEXT_QUERY element!");

				hasConstraints = true;

				if (parserPlugin != null && parserPlugin.handles(WQUriControlField.TEXT_QUERY.getName(), textQuery))
				{
					parserPlugin.process(this, WQUriControlField.TEXT_QUERY.getName(), textQuery);
				}
				else
				{
					this.decode(textQuery.get(0));
				}
			}
		}

		for (Map.Entry<String, List<String>> entry : map.entrySet())
		{
			final String key = entry.getKey();
			final boolean special = key.charAt(0) == '_';
			final boolean textQuery = (key.charAt(0) == 'q' && key.length() == 1);
			final List<String> value = entry.getValue();

			if (parserPlugin != null && parserPlugin.handles(key, value))
			{
				// If this is the first non-Special-Field, clear any default constraints and mark that we now have constraints
				// N.B. we use "special" as distinct from "textQuery", since technically a plugin could decide to implement textQuery itself
				if (!special && !hasConstraints)
				{
					constraints.constraints = new ArrayList<>();

					hasConstraints = true;
				}

				parserPlugin.process(this, key, value);
				continue;
			}

			// Text query is already handled before this for loop so no need to process it here
			if (textQuery)
				continue;
			else if (special)
			{
				final WQUriControlField specialField = WQUriControlField.getByName(key);

				switch (specialField)
				{
					case TEXT_QUERY:
						// Explicitly ignore (should never be executed anyway)
						break;
					case OFFSET:
						offset(Integer.parseInt(value.get(0)));
						break;
					case LIMIT:
						limit(Math.min(capLimit, Integer.parseInt(value.get(0))));
						break;
					case CLASS:
						subclass(value.toArray(new String[0]));
						break;
					case COMPUTE_SIZE:
						computeSize(parseBoolean(value.get(0)));
						break;
					case LOG_SQL:
						logSQL(parseBoolean(value.get(0)));
						break;
					case LOG_PERFORMANCE:
						logPerformance(parseBoolean(value.get(0)));
						break;
					case EXPAND:
						expand(value.toArray(new String[0]));
						break;
					case ORDER:
						orderings = value.stream().map(WQOrder :: parseLegacy).collect(Collectors.toList());
						break;
					case FETCH:
						// Ordinarily we'd expect a single value here, but allow for multiple values to be provided as a comma-separated list
						fetch = String.join(",", value);
						break;
					case DBFETCH:
						dbfetch = String.join(",", value);
						break;
					case NAME:
						name(value.get(0));
						break;
					default:
						throw new IllegalArgumentException("Unknown query field: " +
						                                   specialField +
						                                   " expected one of " +
						                                   WQUriControlField.getAllNames());
				}
			}
			else
			{
				// If this is the first constraint, clear any pre-defined default constraints
				if (!hasConstraints)
				{
					constraints.constraints = new ArrayList<>();

					hasConstraints = true;
				}

				if (value.size() == 1)
				{
					constraints.constraints.add(WQConstraint.decode(key, value.get(0)));
				}
				else if (value.size() > 0)
				{
					WQGroup group = new WQGroup();

					group.operator = WQGroupType.OR;

					group.constraints = value.stream().map(val -> WQConstraint.decode(key, val)).collect(Collectors.toList());

					constraints.constraints.add(group);
				}
			}
		}

		return this;
	}


	/**
	 * @param textQuery
	 */
	public WebQuery decode(final String textQuery)
	{
		this.constraints.constraints.clear();

		WebQueryParser.parse(textQuery, this);

		return this;
	}


	private static boolean parseBoolean(String value)
	{
		if (StringUtils.equalsIgnoreCase(value, "true") ||
		    StringUtils.equalsIgnoreCase(value, "yes") ||
		    StringUtils.equalsIgnoreCase(value, "on"))
			return true;
		else if (StringUtils.equalsIgnoreCase(value, "false") ||
		         StringUtils.equalsIgnoreCase(value, "no") ||
		         StringUtils.equalsIgnoreCase(value, "off"))
			return false;
		else
			throw new IllegalArgumentException("Cannot parse boolean: " + value);
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Decoding URI Query
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Encode this query to the equivalent (where possible) URI web query
	 *
	 * @return
	 */
	public Map<String, List<String>> encode()
	{
		return WebQueryToQueryStringConverter.convert(this);
	}


	/**
	 * Returns a deep clone
	 *
	 * @return
	 */
	@Override
	public WebQuery clone()
	{
		final WebQuery that = new WebQuery();

		that.name = this.name;
		that.fetch = this.fetch;
		that.dbfetch = this.dbfetch;
		that.expand = this.expand;

		that.logSQL = this.logSQL;
		that.logPerformance = this.logPerformance;

		that.wasLimitSetExplicitly = this.wasLimitSetExplicitly;
		that.capLimit = this.capLimit;

		that.orderings = new ArrayList<>(that.orderings.size());
		for (WQOrder ordering : this.orderings)
			that.orderings.add(ordering.clone());

		that.constraints = this.constraints.clone();

		return that;
	}


	@Override
	public String toString()
	{
		return "WebQuery{" +
		       "fetch='" +
		       fetch +
		       '\'' +
		       ", expand='" +
		       expand +
		       '\'' +
		       ", constraints=" +
		       constraintsToQueryFragment() +
		       ", limit=" +
		       constraints.limit +
		       (logSQL ? ", logSQL" : "") +
		       ", orderings=" +
		       orderings +
		       '}';
	}


	public String toQueryFragment()
	{
		return toQueryFragment(true);
	}


	public String toQueryFragment(final boolean includeSelectAndExpand)
	{
		StringBuilder sb = new StringBuilder();

		if (includeSelectAndExpand)
		{
			if (StringUtils.isNotEmpty(fetch) && !StringUtils.equals(fetch, "entity"))
			{
				sb.append("SELECT ").append(fetch);
			}

			if (StringUtils.isNotEmpty(expand))
			{
				// If we already had a SELECT then we need to insert a line break
				if (!sb.isEmpty())
					sb.append('\n');

				sb.append("EXPAND\n\t");
				if (!expand.contains("-"))
					sb.append(expand);
				else
					sb.append(expand.replace("-", "not:"));
			}
		}


		if (!sb.isEmpty() && !constraints.constraints.isEmpty())
			sb.append("\nWHERE\n");

		constraints.toQueryFragment(sb);

		if (!orderings.isEmpty())
		{
			if (!sb.isEmpty())
				sb.append('\n');

			sb.append("ORDER BY ");

			boolean first = true;
			for (WQOrder order : orderings)
			{
				if (!first)
					sb.append(",\n\t");
				else
					first = false;

				sb.append(order.field);

				if (!order.isAsc())
					sb.append(" DESC");
			}
		}

		return sb.toString();
	}


	public static WebQuery parse(final String str)
	{
		return new WebQuery().decode(str);
	}
}
