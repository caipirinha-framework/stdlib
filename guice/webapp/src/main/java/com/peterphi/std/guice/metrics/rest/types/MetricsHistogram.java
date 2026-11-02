package com.peterphi.std.guice.metrics.rest.types;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "Histogram")
@XmlType(name = "HistogramType")
public class MetricsHistogram
{
	@XmlElement
	public String name;
	@XmlElement
	public long count;

	@XmlElement
	public long snapshotSize;
	@XmlElement
	public long snapshotMin;
	@XmlElement
	public long snapshotMax;
	@XmlElement
	public double snapshotStdDev;
	@XmlElement
	public double snapshotMean;

	@XmlElement
	public double percentile50;
	@XmlElement
	public double percentile75;
	@XmlElement
	public double percentile95;
	@XmlElement
	public double percentile98;
	@XmlElement
	public double percentile99;
	@XmlElement
	public double percentile999;


	public MetricsHistogram()
	{
	}


	public MetricsHistogram(final String name,
	                        final long count,
	                        final long snapshotSize,
	                        final long snapshotMin,
	                        final long snapshotMax,
	                        final double snapshotStdDev,
	                        final double snapshotMean,
	                        final double percentile50,
	                        final double percentile75,
	                        final double percentile95,
	                        final double percentile98,
	                        final double percentile99,
	                        final double percentile999)
	{
		this.name = name;
		this.count = count;
		this.snapshotSize = snapshotSize;
		this.snapshotMin = snapshotMin;
		this.snapshotMax = snapshotMax;
		this.snapshotStdDev = snapshotStdDev;
		this.snapshotMean = snapshotMean;
		this.percentile50 = percentile50;
		this.percentile75 = percentile75;
		this.percentile95 = percentile95;
		this.percentile98 = percentile98;
		this.percentile99 = percentile99;
		this.percentile999 = percentile999;
	}
}
