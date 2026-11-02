package com.peterphi.usermanager.ui.api;

import com.peterphi.std.annotation.Doc;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/register")
@Doc("Handles new user registration")
public interface RegisterUIService
{
	@GET
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	String getRegister();

	@POST
	@Path("/")
	@Produces(MediaType.TEXT_HTML)
	Response doRegister(@FormParam("token") String token,
	                    @FormParam("email") String email,
	                    @FormParam("name") String name,
	                    @FormParam("dateFormat") String dateFormat,
	                    @FormParam("timeZone") String timeZone,
	                    @FormParam("password") String password,
	                    @FormParam("passwordConfirm") String passwordConfirm,
	                    @FormParam("roles") List<String> roles);
}
