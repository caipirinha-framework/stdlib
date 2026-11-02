package com.peterphi.std.guice.restclient.jaxb.webqueryschema;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

@XmlType(name = "EntityPropertyType")
public class WQEntityProperty
{
	@XmlAttribute
	public String name;

	@XmlAttribute
	public WQDataType type;

	@XmlAttribute
	public String relation;

	@XmlAttribute
	public Boolean relationCollection;


	@XmlAttribute
	public boolean nullable;

	@XmlAttribute
	public String enumType;

	/**
	 * If datatype is enum, the permitted values for this enum
	 */
	@XmlElementWrapper(name = "enumValues")
	@XmlElement(name = "val")
	public List<String> enumValues;
}
