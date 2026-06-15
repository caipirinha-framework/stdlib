package com.peterphi.std.guice.web.rest.service.daemons;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.annotation.ServiceName;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("/guice/threads")
@ServiceName("Threads")
@Doc("Indexes all guice daemons and centrally-launched threads")
@ImplementedBy(GuiceRestDaemonsServiceImpl.class)
public interface GuiceRestDaemonsService
{
	@GET
	@Path("/")
	@Produces("text/html")
	public String getIndex(@QueryParam("message") @Doc("for internal use") String message);

	@POST
	@Path("/trigger")
	@Produces("text/html")
	@Doc("Trigger a recurring daemon to run immediately")
	Response trigger(@FormParam("csrf_token") String providedCsrfToken,
	                 @FormParam("name") final String name,
	                 @FormParam("verbose") @DefaultValue("false") final boolean verbose);

	@GET
	@Path("/stacktrace")
	@Produces("text/html")
	@Doc("Get a live stack trace")
	String getStackTrace(@QueryParam("name") final String name);

	@POST
	@Path("/interrupt")
	@Produces("text/html")
	@Doc("Attempt to interrupt the daemon")
	Response interrupt(@FormParam("csrf_token") String providedCsrfToken, @FormParam("name") final String name);
}
