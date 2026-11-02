package com.peterphi.std.guice.config.rest.types;

import com.google.common.base.MoreObjects;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@XmlRootElement(name = "ConfigData")
public class ConfigPropertyData
{
	@XmlAttribute
	public String path;
	@XmlAttribute
	public String revision;
	@XmlAttribute
	public Date timestamp;

	@XmlElement(name = "kvp")
	public List<ConfigPropertyValue> properties = new ArrayList<>();


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
		                  .add("path", path)
		                  .add("revision", revision)
		                  .add("timestamp", timestamp)
		                  .add("properties", properties)
		                  .toString();
	}
}
