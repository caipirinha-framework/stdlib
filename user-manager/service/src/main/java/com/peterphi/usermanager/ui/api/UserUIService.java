package com.peterphi.usermanager.ui.api;

import com.peterphi.std.annotation.Doc;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

@Doc("User Manager user interface")
@Path("/")
public interface UserUIService
{
	@GET
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	String getIndex();

	@GET
	@Path("/redirect-warning")
	@Produces(MediaType.TEXT_HTML)
	String getWarnAndRedirect(@QueryParam("url") String url);

	@GET
	@Path("/users")
	@Produces(MediaType.TEXT_HTML)
	String getUsers(@Context UriInfo query);

	@GET
	@Path("/user")
	@Produces(MediaType.TEXT_HTML)
	Response getLocalUserEdit();

	@GET
	@Path("/user/{user_id}")
	@Produces(MediaType.TEXT_HTML)
	String getUserEdit(@PathParam("user_id") int userId);

	@POST
	@Path("/user/{user_id}")
	@Produces(MediaType.TEXT_HTML)
	Response editUserProfile(@PathParam("user_id") int userId,
	                         @FormParam("token") String token,
	                         @FormParam("dateFormat") String dateFormat,
	                         @FormParam("timeZone") String timeZone,
	                         @FormParam("name") String name,
	                         @FormParam("email") String email,
	                         @FormParam("roles") List<String> roles);

	@POST
	@Path("/user/{user_id}/rotate-access-key")
	@Produces(MediaType.TEXT_HTML)
	Response rotateAccessKey(@PathParam("user_id") int userId, @FormParam("token") String token);

	@POST
	@Path("/user/{user_id}/delete")
	@Produces(MediaType.TEXT_HTML)
	Response deleteUser(@PathParam("user_id") int userId, @FormParam("token") String token);

	@POST
	@Path("/user/{user_id}/change_password")
	@Produces(MediaType.TEXT_HTML)
	Response changePassword(@PathParam("user_id") int userId,
	                        @FormParam("token") String token,
	                        @FormParam("password") final String newPassword,
	                        @FormParam("passwordConfirm") final String newPasswordConfirm);

	@POST
	@Path("/user/{user_id}/reset_password")
	@Produces(MediaType.TEXT_HTML)
	Response startPasswordResetFlow(@PathParam("user_id") int userId, @FormParam("token") String token);

	@POST
	@Path("/user/{user_id}/impersonate")
	@Produces(MediaType.TEXT_HTML)
	Response impersonate(@PathParam("user_id") int userId, @FormParam("token") String token);
}
