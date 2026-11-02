package com.peterphi.std.guice.metrics.rest.types;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "HealthCheckResultType")
public class HealthCheckResult
{
	@XmlElement
	public String name;
	@XmlElement
	public HealthImplication implication;
	@XmlElement
	public boolean healthy;
	@XmlElement
	public String message;


	public HealthCheckResult()
	{
	}


	public HealthCheckResult(final String name, final HealthImplication implication, final boolean healthy, final String message)
	{
		this.name = name;
		this.implication = implication;
		this.healthy = healthy;
		this.message = message;
	}
}
