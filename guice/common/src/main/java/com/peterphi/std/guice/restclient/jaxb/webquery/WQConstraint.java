package com.peterphi.std.guice.restclient.jaxb.webquery;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines an individual field constraint to apply
 */
@XmlRootElement(name = "Constraint")
@XmlType(name = "ConstraintType")
public class WQConstraint extends WQConstraintLine
{
	@XmlAttribute(name = "field", required = true)
	public String field;

	@XmlAttribute(name = "function", required = false)
	public WQFunctionType function;

	@XmlAttribute(name = "value")
	public String value;

	@XmlElement(name = "v")
	public List<String> valuelist;

	/**
	 * The second value (for binary functions).<br /> Should only be supplied if {@link #function} is supplied and if it refers to
	 * a binary function such as {@link WQFunctionType#RANGE}
	 */
	@XmlAttribute(name = "value2", required = false)
	public String value2;


	public WQConstraint()
	{
	}


	public WQConstraint(final String field, final WQFunctionType function, final String value)
	{
		this(field, function, value, null);
	}


	public WQConstraint(final String field, final WQFunctionType function, final String left, final String right)
	{
		this.field = field;
		this.function = function;
		this.value = left;
		this.value2 = right;
	}


	public WQConstraint(final String field, WQFunctionType function, final List<String> values)
	{
		if (function == WQFunctionType.EQ)
			function = WQFunctionType.IN;
		else if (function != WQFunctionType.IN && function != WQFunctionType.NOT_IN)
			throw new IllegalArgumentException("Cannot call WQConstraint with valuelist on function " + field);

		this.field = field;
		this.function = function;
		this.valuelist = values;
	}


	@Override
	public WQConstraintLine not()
	{
		final WQFunctionType inverted = function.invert();

		if (inverted != null)
		{
			this.function = inverted;
			return this;
		}
		else
		{
			return super.not();
		}
	}

	@Override
	public WQConstraint clone()
	{
		final WQConstraint that = new WQConstraint();

		that.field = this.field;
		that.function = this.function;
		that.value = this.value;
		that.value2 = this.value2;

		if (this.valuelist != null)
			that.valuelist = new ArrayList<>(this.valuelist);

		return that;
	}


	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder(64);

		sb.append("QConstraint{");
		toQueryFragment(sb);
		sb.append('}');
		return sb.toString();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Encode to query string format
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Encode this constraint in the query string value format
	 *
	 * @return
	 */
	public String encodeValue()
	{
		switch (function)
		{
			case EQ:
				if (value != null && value.startsWith("_"))
					return function.getPrefix() + value;
				else
					return value;
			case IN:
				throw new RuntimeException("Values for IN query cannot be expressed in query string format!");
			default:
				if (function.hasBinaryParam())
					return function.getPrefix() + value + ".." + value2;
				else if (function.hasParam())
					return function.getPrefix() + value;
				else
					return function.getPrefix();
		}
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper constructors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	public static WQConstraint eq(final String field, final Object value)
	{
		if (value == null)
			return isNull(field);
		else
			return new WQConstraint(field, WQFunctionType.EQ, toString(value));
	}


	public static WQConstraint in(final String field, final Collection<?> values)
	{
		if (values.size() == 0)
			throw new IllegalArgumentException("Must provide at least one value for IN!");
		else if (values.size() == 1)
			return new WQConstraint(field,
			                        WQFunctionType.IN,
			                        Collections.singletonList(values
					                                                  .stream()
					                                                  .findFirst()
					                                                  .filter(Objects :: nonNull)
					                                                  .map(WQConstraint :: toString)
					                                                  .get()));
		else if (values instanceof List && values.stream().allMatch(o -> o != null && o instanceof String))
			return new WQConstraint(field, WQFunctionType.IN, (List<String>) values);
		else
			return new WQConstraint(field,
			                        WQFunctionType.IN,
			                        values.stream().map(WQConstraint :: toString).collect(Collectors.toList()));
	}


	public static WQConstraint notIn(final String field, final Collection<?> values)
	{
		if (values.size() == 0)
			throw new IllegalArgumentException("Must provide at least one value for IN!");
		else if (values.size() == 1)
			return new WQConstraint(field,
			                        WQFunctionType.NOT_IN,
			                        Collections.singletonList(values
					                                                  .stream()
					                                                  .findFirst()
					                                                  .filter(Objects :: nonNull)
					                                                  .map(WQConstraint :: toString)
					                                                  .get()));
		else if (values instanceof List && values.stream().allMatch(o -> o != null && o instanceof String))
			return new WQConstraint(field, WQFunctionType.NOT_IN, (List<String>) values);
		else
			return new WQConstraint(field,
			                        WQFunctionType.NOT_IN,
			                        values.stream().map(WQConstraint :: toString).collect(Collectors.toList()));
	}


	public static WQConstraint neq(final String field, final Object value)
	{
		if (value == null)
			return isNotNull(field);
		else
			return new WQConstraint(field, WQFunctionType.NEQ, toString(value));
	}


	public static WQConstraint isNull(final String field)
	{
		return new WQConstraint(field, WQFunctionType.IS_NULL, (String) null);
	}


	public static WQConstraint isNotNull(final String field)
	{
		return new WQConstraint(field, WQFunctionType.NOT_NULL, (String)null);
	}


	public static WQConstraint lt(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.LT, toString(value));
	}


	public static WQConstraint le(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.LE, toString(value));
	}


	public static WQConstraint gt(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.GT, toString(value));
	}


	public static WQConstraint ge(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.GE, toString(value));
	}


	public static WQConstraint contains(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.CONTAINS, toString(value));
	}

	public static WQConstraint notContains(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.NOT_CONTAINS, toString(value));
	}


	public static WQConstraint startsWith(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.STARTS_WITH, toString(value));
	}


	public static WQConstraint notStartsWith(final String field, final Object value)
	{
		return new WQConstraint(field, WQFunctionType.NOT_STARTS_WITH, toString(value));
	}


	public static WQConstraint range(final String field, final Object from, final Object to)
	{
		return new WQConstraint(field, WQFunctionType.RANGE, toString(from), toString(to));
	}


	public static WQConstraint eqRef(final String field, final String field2)
	{
		return new WQConstraint(field, WQFunctionType.EQ_REF, field2);
	}


	public static WQConstraint neqRef(final String field, final String field2)
	{
		return new WQConstraint(field, WQFunctionType.NEQ_REF, field2);
	}


	public static WQConstraint leRef(final String field, final String field2)
	{
		return new WQConstraint(field, WQFunctionType.LE_REF, field2);
	}


	public static WQConstraint ltRef(final String field, final String field2)
	{
		return new WQConstraint(field, WQFunctionType.LT_REF, field2);
	}


	public static WQConstraint geRef(final String field, final String field2)
	{
		return new WQConstraint(field, WQFunctionType.GE_REF, field2);
	}


	public static WQConstraint gtRef(final String field, final String field2)
	{
		return new WQConstraint(field, WQFunctionType.GT_REF, field2);
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Decode query string value encoding
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Produce a WebQueryConstraint from a Query String format parameter
	 *
	 * @param field
	 * @param rawValue
	 * @return
	 */
	public static WQConstraint decode(final String field, final String rawValue)
	{
		final WQFunctionType function;
		final String value;

		if (StringUtils.equalsIgnoreCase(rawValue, WQFunctionType.IS_NULL.getPrefix()))
			return new WQConstraint(field, WQFunctionType.IS_NULL, (String) null);
		else if (StringUtils.equalsIgnoreCase(rawValue, WQFunctionType.NOT_NULL.getPrefix()))
			return new WQConstraint(field, WQFunctionType.NOT_NULL, (String) null);
		else if (rawValue.startsWith("_f_"))
		{
			function = WQFunctionType.getByPrefix(rawValue);

			if (function == WQFunctionType.IN)
				throw new IllegalArgumentException("IN constraint cannot be encoded in a query string value!");

			if (function.hasParam())
			{
				// Strip the function name from the value
				value = rawValue.substring(function.getPrefix().length());

				if (function.hasBinaryParam())
				{
					final int separator = value.indexOf("..");

					if (separator <= 0 || separator + 2 >= value.length())
						throw new IllegalArgumentException("Range constraint for " + field + " requires two endpoints separated by '..'");

					final String left = value.substring(0, separator);
					final String right = value.substring(separator + 2);

					return new WQConstraint(field, function, left, right);
				}
			}
			else
			{
				value = null;
			}
		}
		else
		{
			function = WQFunctionType.EQ;
			value = rawValue;
		}

		return new WQConstraint(field, function, value);
	}


	@Override
	public void toQueryFragment(StringBuilder sb)
	{
		sb.append(field).append(' ').append(function.getQueryFragmentForm());

		if (function == WQFunctionType.IN || function == WQFunctionType.NOT_IN)
		{
			sb.append('(');

			boolean first = true;
			for (String s : valuelist)
			{
				if (!first)
					sb.append(", ");
				else
					first = false;

				appendEscaped(sb, s);
			}
			sb.append(')');
		}
		else if (function.hasParam())
		{
			sb.append(' ');
			appendEscaped(sb, value);

			if (function.hasBinaryParam())
			{
				sb.append(" AND ");
				appendEscaped(sb, value2);
			}
		}
	}


	private static void appendEscaped(final StringBuilder sb, final String val)
	{
		if (val == null)
			sb.append("(NIL)"); // avoid using null
		else if (!val.isEmpty() && StringUtils.isNumeric(val))
			sb.append(val); // Don't quote numbers
		else if (isBareWord(val))
			sb.append(val); // no spaces, no quotes so can be a bare value
		else if (val.indexOf('\'') == -1)
			sb.append('\'').append(val).append('\''); // no single quotes in string, so use single quotes
		else if (val.indexOf('"') == -1)
			sb.append('"').append(val).append('"'); // no double quotes
		else if (val.indexOf('`') == -1)
			sb.append('`').append(val).append('`'); // no backticks
		else
			throw new IllegalArgumentException("Cannot escape string value: contains all 3 quote types!");
	}


	private static boolean isBareWord(final String val)
	{
		if (val.length() == 0)
			return false;

		if (!Character.isJavaIdentifierPart(val.charAt(0)))
			return false;

		for (int i = 0; i < val.length(); i++)
			if (!WebQueryParser.isBareWordPart(val.charAt(i)))
				return false;

		return true;
	}


	public static String toString(final Object value)
	{
		if (value == null)
			throw new IllegalArgumentException("Cannot convert a null value to string in WQConstraint!");
		else if (value instanceof Collection)
			throw new IllegalArgumentException("Cannot convert a Collection to a string in WQConstraint! " + value);
		else if (value instanceof Date)
			return new DateTime(((Date) value)).toString();
		else if (value instanceof Serializable)
			return value.toString();
		else
			throw new IllegalArgumentException("Do not know how to convert value to a WQConstraint string representation: " +
			                                   value.getClass() +
			                                   " for " +
			                                   value);
	}
}
