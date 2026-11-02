package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Base class for components of a Constraint Group
 */
@XmlType(name = "ConstraintLineType")
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({@JsonSubTypes.Type(WQConstraint.class), @JsonSubTypes.Type(WQGroup.class)})
public abstract class WQConstraintLine
{
	public abstract void toQueryFragment(StringBuilder sb);


	public WQConstraintLine not()
	{
		final WQGroup g = WQGroup.newNone();
		g.add(this);
		return g;
	}

	public abstract WQConstraintLine clone();
}
