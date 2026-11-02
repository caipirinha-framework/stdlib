package com.peterphi.std.guice.metrics.rest.types;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "MetricsDocument")
@XmlType(name = "MetricsDocumentType")
public class MetricsDocument
{
	@XmlElement(name = "counter")
	public List<MetricsCounter> counters = new ArrayList<>();
	@XmlElement(name = "gauge")
	public List<MetricsGauge> gauges = new ArrayList<>();
	@XmlElement(name = "histogram")
	public List<MetricsHistogram> histograms = new ArrayList<>();
	@XmlElement(name = "meter")
	public List<MetricsMeter> meters = new ArrayList<>();
}
