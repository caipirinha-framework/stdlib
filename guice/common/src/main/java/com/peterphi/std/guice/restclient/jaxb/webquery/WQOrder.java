package com.peterphi.std.guice.restclient.jaxb.webquery;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;
import org.apache.commons.lang.StringUtils;

import java.beans.Transient;

/**
 * Defines a sort instruction
 */
@XmlType(name = "OrderType")
public class WQOrder
{
	@XmlAttribute
	public String field;

	/**
	 * ASC or DESC
	 */
	@XmlAttribute
	public String direction;


	public WQOrder()
	{
	}


	public WQOrder(final String field, final String direction)
	{
		if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc"))
			throw new IllegalArgumentException("Direction must be asc or desc, not " + direction);

		this.field = field;
		this.direction = direction.toLowerCase();
	}


	public String getField()
	{
		return field;
	}


	public String getDirection()
	{
		return direction;
	}

	@Transient
	public boolean isAsc()
	{
		return this.direction.equalsIgnoreCase("asc");
	}


	public String toLegacyForm()
	{
		return field + " " + direction;
	}


	@Override
	public WQOrder clone()
	{
		return new WQOrder(field, direction);
	}

	@Override
	public String toString()
	{
		return "WQOrder{" + toLegacyForm() + '}';
	}


	public static WQOrder asc(final String field)
	{
		return new WQOrder(field, "asc");
	}


	public static WQOrder desc(final String field)
	{
		return new WQOrder(field, "desc");
	}


	public static WQOrder parseLegacy(final String expr)
	{
		final String[] parts = StringUtils.split(expr, ' ');

		if (parts.length == 2)
			return new WQOrder(parts[0], parts[1]);
		else if (parts.length == 1)
			return WQOrder.asc(parts[0]); // Assume ascending if no order set
		else
			throw new IllegalArgumentException("Invalid Order Expression \"" + expr + "\". Expected: property [asc|desc]");
	}
}
