package com.peterphi.std.guice.metrics.rest.types;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "Counter")
@XmlType(name = "CounterType")
public class MetricsCounter
{
	@XmlElement
	public String name;
	@XmlElement
	public long count;


	public MetricsCounter()
	{
	}


	public MetricsCounter(final String name, final long count)
	{
		this.name = name;
		this.count = count;
	}
}
