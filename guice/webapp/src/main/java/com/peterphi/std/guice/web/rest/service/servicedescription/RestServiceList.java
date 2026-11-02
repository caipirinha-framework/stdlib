package com.peterphi.std.guice.web.rest.service.servicedescription;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.restclient.annotations.FastFailServiceClient;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;

@Doc("Lists the REST services and resources exposed by this webapp")
@Path("/guice")
@ImplementedBy(RestServiceListImpl.class)
@FastFailServiceClient
public interface RestServiceList
{
	@Doc("Lists all services")
	@GET
	@Produces("text/html")
	@Path("/")
	public String index(@Context HttpHeaders headers, @Context UriInfo uriInfo) throws Exception;

	@Doc(value = "Describes a single service")
	@GET
	@Produces("text/html")
	@Path("/service/{service_id}")
	public String getServiceDescription(@Doc(value = "the internal id of the service") @PathParam("service_id") String serviceId,
	                                    @Context HttpHeaders headers,
	                                    @Context UriInfo uriInfo) throws Exception;


	@Doc(value = "Produce an XSD (or multi-XSD) for a service parameter or return type")
	@GET
	@Produces("text/xml")
	@Path("/type/{class}/schema.xsd")
	String getXSDSchema(@Doc("the class name of a return type or parameter to an exposed service method") @PathParam("class")
			                    String className) throws Exception;

	@Doc(value = "Produce an auto-generated example XML for a service parameter or return type")
	@GET
	@Produces("text/xml")
	@Path("/type/{class}/example.xml")
	String getExampleXML(@Doc("the class name of a return type or parameter to an exposed service method") @PathParam("class")
			                     String className,
	                     @Doc("If true then the example will only be generated 1 level deep") @QueryParam("minimal")
	                     @DefaultValue("false") final boolean minimal) throws Exception;
}
