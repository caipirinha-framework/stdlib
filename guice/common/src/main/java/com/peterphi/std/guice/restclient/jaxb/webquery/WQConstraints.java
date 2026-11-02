package com.peterphi.std.guice.restclient.jaxb.webquery;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlType(name = "ConstraintsType")
public class WQConstraints implements ConstraintContainer<WQConstraints>
{
	@XmlAttribute
	public int offset = 0;
	@XmlAttribute
	public int limit = 0;
	@XmlAttribute
	public boolean computeSize = false;

	/**
	 * The subclass to constrain the results to
	 */
	@XmlAttribute(name = "subclass", required = false)
	public String subclass = null;

	/**
	 * The constraints. This group will be implicitly ANDed together
	 */
	@XmlElementRefs({@XmlElementRef(type = WQConstraint.class), @XmlElementRef(type = WQGroup.class)})
	public List<WQConstraintLine> constraints = new ArrayList<>();


	@Override
	public WQConstraints add(final WQConstraintLine line)
	{
		if (line != null)
			constraints.add(line);

		return this;
	}


	@Override
	public String toString()
	{
		return "WebQueryConstraints{" +
		       "offset=" +
		       offset +
		       ", limit=" +
		       limit +
		       ", computeSize=" +
		       computeSize +
		       ", clazz='" +
		       subclass +
		       '\'' +
		       ", constraints=" +
		       constraints +
		       '}';
	}


	@Override
	public WQConstraints clone()
	{
		final WQConstraints that = new WQConstraints();

		that.offset = offset;
		that.limit = limit;
		that.computeSize = computeSize;
		that.subclass = subclass;
		that.constraints = new ArrayList<>(constraints.size());

		for (WQConstraintLine constraint : this.constraints)
			that.constraints.add(constraint.clone());

		return that;
	}

	public String toQueryFragment()
	{
		StringBuilder sb = new StringBuilder();

		toQueryFragment(sb);

		return sb.toString();
	}


	public void toQueryFragment(StringBuilder sb)
	{
		boolean first = true;
		for (WQConstraintLine constraint : constraints)
		{
			if (!first)
				sb.append("\nAND ");
			else
				first = false;

			constraint.toQueryFragment(sb);
		}
	}
}
