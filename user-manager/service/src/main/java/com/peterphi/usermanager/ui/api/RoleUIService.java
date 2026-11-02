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
public interface RoleUIService
{
	@GET
	@Path("/roles")
	@Produces(MediaType.TEXT_HTML)
	String getRoles(@Context UriInfo query);

	@GET
	@Path("/role/{role_id}")
	@Produces(MediaType.TEXT_HTML)
	String get(@PathParam("role_id") String roleId);

	@POST
	@Path("/roles/create")
	@Produces(MediaType.TEXT_HTML)
	Response create(@FormParam("id") final String id,
	                @FormParam("token") String token,
	                @FormParam("caption") final String caption);

	@POST
	@Path("/role/{role_id}/delete")
	@Produces(MediaType.TEXT_HTML)
	Response delete(@PathParam("role_id") String roleId, @FormParam("token") String token);

	@POST
	@Path("/role/{role_id}/change-caption")
	@Produces(MediaType.TEXT_HTML)
	Response changeCaption(@PathParam("role_id") String roleId,
	                       @FormParam("token") String token,
	                       @FormParam("caption") final String newPassword);

	@POST
	@Path("/role/{role_id}/change-members")
	@Produces(MediaType.TEXT_HTML)
	Response changeMembers(@PathParam("role_id") String roleId, @FormParam("token") String token, @FormParam("members")

	final List<Integer> members);
}
