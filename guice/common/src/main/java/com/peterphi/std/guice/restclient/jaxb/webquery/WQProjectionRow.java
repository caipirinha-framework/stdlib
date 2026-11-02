package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.google.common.base.MoreObjects;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "WQProjectionRow")
@XmlType(name = "WQProjectionRowType")
public class WQProjectionRow
{
	@XmlElement(name = "field")
	public List<WQProjectionField> fields = new ArrayList<>();


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("fields", fields).toString();
	}
}
