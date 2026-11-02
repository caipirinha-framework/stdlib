package com.peterphi.std.guice.config.rest.types;

import com.google.common.base.MoreObjects;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;

@XmlType(name = "ConfigValueType")
public class ConfigPropertyValue
{
	public ConfigPropertyValue(){
		//default constructor for jaxb
	}

	/**
	 *
	 */
	@XmlAttribute
	public String path;

	@XmlAttribute
	public String name;

	@XmlValue
	public String value;


	public ConfigPropertyValue(final String path, final String name, final String value)
	{
		this.path = path;
		this.name = name;
		this.value = value;
	}


	public String getPath()
	{
		return path;
	}


	public String getName()
	{
		return name;
	}


	public String getValue()
	{
		return value;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
		                  .omitNullValues()
		                  .add("path", path)
		                  .add("name", name)
		                  .add("value", value)
		                  .toString();
	}
}
