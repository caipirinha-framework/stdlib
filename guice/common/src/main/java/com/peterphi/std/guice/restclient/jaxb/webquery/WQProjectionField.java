package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.google.common.base.MoreObjects;
import com.peterphi.std.types.SampleCount;
import com.peterphi.std.types.Timecode;
import org.joda.time.DateTime;

import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.Date;

@XmlRootElement(name = "WQProjectionField")
@XmlType(name = "WQProjectionFieldType")
public class WQProjectionField
{
	@XmlAttribute
	public String name;

	@XmlAttribute
	public String value;

	@XmlAnyElement(lax = false)
	public Object objectValue;


	public WQProjectionField()
	{
	}


	public WQProjectionField(final String name, final String str, final Object value)
	{
		this.name = name;

		if (str != null)
			this.value = str;
		else
			this.objectValue = value;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("name", name).add("value", value).add("objectValue", objectValue).toString();
	}


	/**
	 * Helper method that returns True if the provided value should be represented as a String
	 *
	 * @param value
	 *
	 * @return
	 */
	public static boolean isPrimitive(final Object value)
	{
		return (value == null ||
		        value instanceof String ||
		        value instanceof Number ||
		        value instanceof Boolean ||
		        value instanceof DateTime ||
		        value instanceof Date ||
		        value instanceof SampleCount ||
		        value instanceof Timecode ||
		        value.getClass().isEnum() ||
		        value.getClass().isPrimitive());
	}
}
