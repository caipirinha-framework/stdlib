package com.peterphi.std.guice.web.rest.service.servicedescription;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.serviceregistry.rest.RestResourceRegistry;
import com.peterphi.std.guice.web.rest.service.servicedescription.freemarker.RestServiceInfo;
import com.peterphi.std.guice.web.rest.service.servicedescription.freemarker.RestServiceResourceInfo;
import com.peterphi.std.guice.web.rest.service.servicedescription.freemarker.SchemaGenerateUtil;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.thymeleaf.GuiceCoreTemplater;
import org.apache.commons.lang.StringUtils;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

/**
 * A simple REST resource that lists the services registered with the {@link RestResourceRegistry}
 *
 * N.B. by default we do not enforce authorisation on this resource, because it can be very useful for developers (and the
 * knowledge of APIs shouldn't in and of itself introduce a security risk. Access can be restricted by setting
 * framework.webauth.scope.framework-info.force-skip=false
 */
@Singleton
@AuthConstraint(id = "framework-info", role = "framework-info", skip = true)
public class RestServiceListImpl implements RestServiceList
{
	private static final String PREFIX = "/com/peterphi/std/guice/web/rest/service/restcore/";

	@Inject
	GuiceCoreTemplater templater;

	@Inject
	@Named(GuiceProperties.STATIC_CONTAINER_PREFIX_CONFIG_NAME)
	URI containerEndpoint;

	@Inject
	@Named(GuiceProperties.STATIC_ENDPOINT_CONFIG_NAME)
	URI restEndpoint;

	@Inject
	SchemaGenerateUtil schemaGenerator;

	@Inject
	ExampleGenerator exampleGenerator;

	private final List<RestServiceInfo> services;


	@Inject
	public RestServiceListImpl()
	{
		this.services = RestServiceInfo.getAll(RestResourceRegistry.getResources());
	}


	@Override
	public String index(final HttpHeaders headers, final UriInfo uriInfo) throws Exception
	{
		final TemplateCall template = templater.template(PREFIX + "service_list.html");

		template.set("services", services);
		template.set("schemaGenerator", schemaGenerator);

		return template.process();
	}


	@Override
	public String getServiceDescription(final String serviceId, final HttpHeaders headers, final UriInfo uriInfo) throws Exception
	{
		final TemplateCall template = templater.template(PREFIX + "service_describe.html");

		template.set("services", services);
		template.set("containerUrl", containerEndpoint.toString());
		template.set("restUrl", restEndpoint.toString());
		template.set("exampleGenerator", exampleGenerator);

		final RestServiceInfo svc;
		if (StringUtils.isNumeric(serviceId))
			svc = services.get(Integer.parseUnsignedInt(serviceId));
		else
			svc = services.stream().filter(s -> serviceId.equals(s.getInterfaceName())).findFirst().orElse(null);

		if (svc == null)
			throw new IllegalArgumentException("Unknown service: " + serviceId);

		template.set("service", svc);
		template.set("serviceId", svc.getInterfaceName());

		return template.process();
	}


	@Override
	public String getXSDSchema(final String className) throws Exception
	{
		for (RestServiceInfo service : services)
		{
			for (RestServiceResourceInfo resource : service.getResources())
			{
				if (classEquals(resource.getReturnType(), className))
				{
					return schemaGenerator.getSchema(resource.getReturnType());
				}
				else if (resource.getRequestEntity() != null && classEquals(resource.getRequestEntity().getDataType(), className))
				{
					return schemaGenerator.getSchema(resource.getRequestEntity().getDataType());
				}
			}
		}

		throw new IllegalArgumentException("Class is not known as a request or response entity type!");
	}


	@Override
	public String getExampleXML(final String className, final boolean minimal) throws Exception
	{
		for (RestServiceInfo service : services)
		{
			for (RestServiceResourceInfo resource : service.getResources())
			{
				if (classEquals(resource.getReturnType(), className))
				{
					return exampleGenerator.generateExampleXML(resource.getReturnType(), minimal);
				}
				else if (resource.getRequestEntity() != null && classEquals(resource.getRequestEntity().getDataType(), className))
				{
					return exampleGenerator.generateExampleXML(resource.getRequestEntity().getDataType(), minimal);
				}
			}
		}

		throw new IllegalArgumentException("Class is not known as a request or response entity type!");
	}


	private static boolean classEquals(final Class<?> clazz, final String className)
	{
		return clazz != null && clazz.getName().equals(className);
	}
}
