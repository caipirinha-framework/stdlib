package com.peterphi.usermanager.ui.api;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

@Path("/")
public interface ServiceUIService
{
	@GET
	@Path("/services")
	@Produces(MediaType.TEXT_HTML)
	String getList(@Context UriInfo query);

	@GET
	@Path("/service/{id}")
	@Produces(MediaType.TEXT_HTML)
	String get(@PathParam("id") String id);

	@POST
	@Path("/services/create")
	@Produces(MediaType.TEXT_HTML)
	Response create(@FormParam("token") final String token,
	                @FormParam("name") final String name,
	                @FormParam("required_role") final String requiredRoleName,
	                @FormParam("endpoints") final String endpoints,
	                @FormParam("roles") final List<String> roles);

	@POST
	@Path("/service/{id}/disable")
	@Produces(MediaType.TEXT_HTML)
	Response disable(@PathParam("id") String id, @FormParam("token") final String token);

	@POST
	@Path("/service/{id}/edit")
	@Produces(MediaType.TEXT_HTML)
	Response edit(@FormParam("token") final String token,
	              @PathParam("id") String id,
	              @FormParam("required_role") final String requiredRoleName,
	              @FormParam("endpoints") final String endpoints,
	              @FormParam("roles") final List<String> roles);


	@POST
	@Path("/service/{service_id}/rotate-access-key")
	@Produces(MediaType.TEXT_HTML)
	Response rotateAccessKey(@PathParam("service_id") String serviceId, @FormParam("token") String token);
}
