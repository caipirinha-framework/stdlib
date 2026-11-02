package com.peterphi.std.guice.restclient.jaxb.webquery;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a group of constraints to be ANDed or ORred together
 */
@XmlRootElement(name = "ConstraintsGroup")
@XmlType(name = "ConstraintGroupType")
public class WQGroup extends WQConstraintLine implements ConstraintContainer<WQGroup>
{
	@XmlAttribute(required = true)
	public WQGroupType operator;

	@XmlElementRefs({@XmlElementRef(name = "constraint", type = WQConstraint.class),
	                 @XmlElementRef(name = "constraints", type = WQGroup.class)})
	public List<WQConstraintLine> constraints = new ArrayList<>();


	public WQGroup()
	{
	}


	public WQGroup(final WQGroupType operator)
	{
		this.operator = operator;
	}


	public WQGroup(final WQGroupType operator, final List<WQConstraintLine> constraints)
	{
		this(operator);

		this.constraints.addAll(constraints);
	}

	@Override
	public WQGroup clone() {
		WQGroup that = new WQGroup();

		that.operator = operator;

		for (WQConstraintLine constraint : constraints)
			that.constraints.add(constraint.clone());

		return that;
	}

	@Override
	public String toString()
	{
		return "WQGroup{" + operator + ", constraints=" + constraints + "} ";
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Constraints
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	public WQGroup add(WQConstraintLine line)
	{
		if (line != null)
			constraints.add(line);

		return this;
	}


	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Helper constructors
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


	/**
	 * Construct a new empty AND group
	 *
	 * @return
	 */
	public static WQGroup newAnd()
	{
		return new WQGroup(WQGroupType.AND);
	}


	/**
	 * Construct a new empty OR group
	 *
	 * @return
	 */
	public static WQGroup newOr()
	{
		return new WQGroup(WQGroupType.OR);
	}

	/**
	 * Construct a new empty NONE group
	 *
	 * @return
	 */
	public static WQGroup newNone()
	{
		return new WQGroup(WQGroupType.NONE);
	}


	@Override
	public void toQueryFragment(StringBuilder sb)
	{
		if (operator == WQGroupType.NONE)
		{
			sb.append("NOT(");

			boolean first = true;
			for (WQConstraintLine constraint : constraints)
			{
				if (!first)
					sb.append(" OR ");
				else
					first = false;

				constraint.toQueryFragment(sb);
			}
			sb.append(')');
		}
		else
		{
			if (constraints.size() == 1)
			{
				constraints.get(0).toQueryFragment(sb);
			}
			else
			{
				sb.append('(');
				final String operatorStr = operator == WQGroupType.AND ? " AND " : " OR ";
				boolean first = true;
				for (WQConstraintLine constraint : constraints)
				{
					if (!first)
						sb.append(operatorStr);
					else
						first = false;

					constraint.toQueryFragment(sb);
				}

				sb.append(')');
			}
		}
	}
}
