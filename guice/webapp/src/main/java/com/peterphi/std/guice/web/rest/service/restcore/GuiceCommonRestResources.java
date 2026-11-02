package com.peterphi.std.guice.web.rest.service.restcore;

import com.google.inject.ImplementedBy;
import com.peterphi.std.annotation.Doc;
import org.jboss.resteasy.annotations.cache.Cache;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/guice")
@ImplementedBy(GuiceCommonRestResourcesImpl.class)
@Doc("Exposes common CSS/Javascript resources used by the core services")
public interface GuiceCommonRestResources
{
	@GET
	@Path("/style.css")
	@Produces("text/css")
	@Cache(maxAge = 2629740, mustRevalidate = false)
	byte[] getBootstrapCSS();

	@GET
	@Path("/main.js")
	@Produces("text/javascript")
	@Cache(maxAge = 2629740, mustRevalidate = false)
	String getMainJS();
}
