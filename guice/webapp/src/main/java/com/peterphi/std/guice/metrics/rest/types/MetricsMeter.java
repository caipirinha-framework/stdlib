package com.peterphi.std.guice.metrics.rest.types;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "Meter")
@XmlType(name = "MeterType")
public class MetricsMeter
{
	@XmlElement
	public String name;
	@XmlElement
	public long count;

	@XmlElement
	public double rate1m;
	@XmlElement
	public double rate5m;
	@XmlElement
	public double rate15m;
	@XmlElement
	public double rateMean;


	public MetricsMeter()
	{
	}


	public MetricsMeter(final String name,
	                    final long count,
	                    final double rate1m,
	                    final double rate5m,
	                    final double rate15m,
	                    final double rateMean)
	{
		this.name = name;
		this.count = count;
		this.rate1m = rate1m;
		this.rate5m = rate5m;
		this.rate15m = rate15m;
		this.rateMean = rateMean;
	}
}
