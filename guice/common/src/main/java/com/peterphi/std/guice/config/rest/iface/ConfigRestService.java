package com.peterphi.std.guice.config.rest.iface;

import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.config.rest.types.ConfigPropertyData;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public interface ConfigRestService
{
	@POST
	@Path("/read.action")
	@Produces(MediaType.APPLICATION_XML)
	ConfigPropertyData read(@Doc("The config path to read") @FormParam("path") final String path,
	                        @Doc("A unique id for the calling service (that will change on each reconfiguration)")
	                        @FormParam("instance-id") String instanceId,
	                        @Doc("The last config revision this service holds (if no new revision is available then no content will be returned by the service")
	                        @FormParam("last-revision") final String lastRevision);
}
