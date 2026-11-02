package com.peterphi.std.guice.metrics.rest.api;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.annotation.ServiceName;
import com.peterphi.std.guice.metrics.rest.impl.MetricsRestServiceImpl;
import com.peterphi.std.guice.metrics.rest.types.MetricsDocument;
import com.peterphi.std.guice.restclient.annotations.FastFailServiceClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/guice/metrics")
@ImplementedBy(MetricsRestServiceImpl.class)
@FastFailServiceClient
@ServiceName("Metrics")
@Doc("UI and API for service metrics")
public interface MetricsRestService
{
	@GET
	@Path("/")
	@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
	public MetricsDocument getMetrics();

	@GET
	@Path("/prometheus")
	@Produces(MediaType.TEXT_PLAIN)
	public String getPrometheusMetrics();

	@GET
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	public String getIndex();
}
