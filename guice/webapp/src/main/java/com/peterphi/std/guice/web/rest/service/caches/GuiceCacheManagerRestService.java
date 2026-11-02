package com.peterphi.std.guice.web.rest.service.caches;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.annotation.ServiceName;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

@Path("/guice/caches")
@ServiceName("Caches")
@Doc("Displays cache information and allows them to be invalidated")
@ImplementedBy(GuiceCacheManagerRestServiceImpl.class)
public interface GuiceCacheManagerRestService
{
	@GET
	@Path("/")
	@Produces("text/html")
	String getIndex(@QueryParam("message") @Doc("for internal use") String message);

	@POST
	@Path("/invalidate")
	@Produces("text/html")
	@Doc("Invalidate cached values")
	Response invalidate(@FormParam("csrf_token") String providedCsrfToken,
	                    @FormParam("name") @Doc("If omitted will invalidate all caches") String cacheName);
}
