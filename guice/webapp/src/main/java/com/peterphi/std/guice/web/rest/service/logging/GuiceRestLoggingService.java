package com.peterphi.std.guice.web.rest.service.logging;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.annotation.ServiceName;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

@Path("/guice/logging")
@Doc("Allows the log4j log levels to be modified at runtime")
@ServiceName("Logging")
@ImplementedBy(GuiceRestLoggingServiceImpl.class)
public interface GuiceRestLoggingService
{
	@GET
	@Path("/")
	@Produces("text/html")
	String getIndex();

	@POST
	@Path("/load-config-string")
	@Produces("text/html")
	@Doc("Called to load a log4j.properties configuration directly")
	Response loadConfig(@FormParam("log4j.properties") String configuration) throws IOException;
}
