package com.peterphi.std.guice.web.rest.service.breaker;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.annotation.ServiceName;
import com.peterphi.std.guice.restclient.annotations.FastFailServiceClient;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/guice/breakers")
@FastFailServiceClient
@ServiceName("Breakers")
@Doc("Displays breakers and allows them to be tripped or reset")
@ImplementedBy(GuiceRestBreakerServiceImpl.class)
public interface GuiceRestBreakerService
{
	@GET
	@Path("/")
	@Produces("text/html")
	String getIndex(@QueryParam("message") @Doc("for internal use") String message);

	@POST
	@Path("/set-breaker-state")
	@Produces("text/html")
	@Doc("Trip or reset a breaker")
	Response setState(@FormParam("csrf_token") String providedCsrfToken,
	                  @FormParam("name") final String name,
	                  @FormParam("value") @DefaultValue("false") final boolean value,
	                  @FormParam("note") String note);

	@GET
	@Path("/state")
	@Produces(MediaType.TEXT_PLAIN)
	@Doc("Return CSV of breaker,state")
	String getOverview();

	@GET
	@Path("/breaker/{breaker_name}/test")
	@Produces("text/plain")
	@Doc("Returns 2xx with body 'OK' if breaker is not tripped, otherwise 503 with body 'Tripped'. If no such breaker then returns code 404. No auth is required for this method.")
	Response testBreaker(@PathParam("breaker_name") String breakerName);

	@POST
	@Path("/set-breaker-state")
	@Produces("text/plain")
	@Doc("Trip or reset a breaker")
	// N.B. this is the machine-facing (text/plain) variant. It is not reachable by browser-form CSRF (a form cannot
	// negotiate Accept: text/plain, so a forged POST routes to the text/html setState, which is CSRF-protected), so it is
	// deliberately not CSRF-token protected — that would break programmatic callers. It remains framework-admin gated.
	String setTripped(@FormParam("name") final String name,
	                  @FormParam("value") @DefaultValue("false") final boolean value,
	                  @FormParam("note") String note);
}
