package com.peterphi.std.guice.web.rest.service.logging;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.common.logging.LoggingModule;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.thymeleaf.GuiceCoreTemplater;
import com.peterphi.std.io.PropertyFile;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;

@Singleton
@AuthConstraint(id = "framework-admin", role = "framework-admin")
public class GuiceRestLoggingServiceImpl implements GuiceRestLoggingService
{
	/**
	 * The resource prefix
	 */
	private static final String PREFIX = "/com/peterphi/std/guice/web/rest/service/restcore/";

	@Inject
	GuiceCoreTemplater templater;

	@Inject
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	URI restEndpoint;

	@Inject
	GuiceConfig config;


	@Override
	public String getIndex()
	{
		TemplateCall call = templater.template(PREFIX + "logging.html");

		call.set("log4jProperties", getLog4jPropertiesAsString());

		return call.process();
	}


	private String getLog4jPropertiesAsString()
	{
		PropertyFile properties = LoggingModule.getProperties(config);

		if (properties == null)
		{
			return "# Service has no log4j.properties defined, so is using log4j defaults";
		}
		else
		{
			final StringWriter sw = new StringWriter(1024);

			try
			{
				properties.save(null, sw);
			}
			catch (IOException e)
			{
				throw new RuntimeException("error reading log4j properties into string", e);
			}

			return sw.toString();
		}
	}


	@Override
	public Response loadConfig(final String properties)
	{
		// Modify the in-memory config to point at the user-specified properties file
		config.set(GuiceProperties.LOG4J_PROPERTIES_FILE, properties);

		LoggingModule.manualReconfigure(config);

		// Now redirect back to the main logging page
		return Response.seeOther(URI.create(restEndpoint.toString() + "/guice/logging")).build();
	}
}
