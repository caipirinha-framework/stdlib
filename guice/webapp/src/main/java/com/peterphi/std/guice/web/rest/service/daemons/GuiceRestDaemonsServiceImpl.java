package com.peterphi.std.guice.web.rest.service.daemons;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.util.Enhanced;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.common.daemon.GuiceDaemon;
import com.peterphi.std.guice.common.daemon.GuiceDaemonRegistry;
import com.peterphi.std.guice.common.daemon.GuiceRecurringDaemon;
import com.peterphi.std.guice.web.rest.scoping.SessionScoped;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.thymeleaf.GuiceCoreTemplater;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@SessionScoped
@AuthConstraint(id = "framework-admin", role = "framework-admin")
public class GuiceRestDaemonsServiceImpl implements GuiceRestDaemonsService
{
	/**
	 * The resource prefix
	 */
	private static final String PREFIX = "/com/peterphi/std/guice/web/rest/service/restcore/";

	@Inject
	GuiceCoreTemplater templater;

	@Inject
	GuiceDaemonRegistry registry;


	@Inject
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	URI restEndpoint;

	private final String csrfToken = UUID.randomUUID().toString();


	private void checkCsrfToken(final String provided)
	{
		if (!StringUtils.equals(provided, csrfToken))
			throw new IllegalArgumentException("CSRF token not provided or invalid, refusing request");
	}


	@Override
	public String getIndex(String message)
	{
		final TemplateCall template = templater.template(PREFIX + "daemon_list.html");

		template.set("message", message);
		template.set("registry", registry);
		template.set("csrfToken", csrfToken);
		template.set("daemonDescriber", (Function<GuiceDaemon, String>) this :: getDescription);

		return template.process();
	}


	@Override
	public Response trigger(final String providedCsrfToken, final String name, final boolean verbose)
	{
		checkCsrfToken(providedCsrfToken);

		final Optional<GuiceRecurringDaemon> result = registry.getRecurring()
		                                                      .stream()
		                                                      .filter(d -> StringUtils.equals(name,
		                                                                                      d.getName()))
		                                                      .findFirst();

		if (result.isPresent())
		{
			final GuiceRecurringDaemon daemon = result.get();

			if (verbose)
				daemon.makeNextRunVerbose();

			daemon.trigger();

			final String message = "Daemon " + daemon.getName() + " triggered at " + DateTime.now() + " with verbose=" + verbose;

			return Response.seeOther(UriBuilder.fromUri(restEndpoint.toString() + "/guice/threads")
			                                   .queryParam("message", message)
			                                   .build()).build();
		}
		else
		{
			throw new IllegalArgumentException("No recurring daemon with name: " + name);
		}
	}


	@Override
	public String getStackTrace(final String name)
	{
		final Optional<GuiceDaemon> result = registry
				                                     .getAll()
				                                     .stream()
				                                     .filter(d -> StringUtils.equals(name, d.getName()))
				                                     .findFirst();

		if (result.isPresent())
		{
			final GuiceDaemon daemon = result.get();

			final StackTraceElement[] stack;
			if (daemon.isThreadRunning() && daemon.getThread() != null)
			{
				Thread thread = daemon.getThread();

				stack = thread.getStackTrace();
			}
			else
			{
				stack = null;
			}

			final TemplateCall template = templater.template(PREFIX + "daemon_stacktrace.html");

			template.set("stack", stack);
			template.set("name", daemon.getName());
			template.set("registry", registry);
			template.set("daemonDescriber", (Function<GuiceDaemon, String>) this :: getDescription);

			return template.process();
		}
		else
		{
			throw new IllegalArgumentException("No daemon with name: " + name);
		}
	}


	@Override
	public Response interrupt(final String providedCsrfToken, final String name)
	{
		checkCsrfToken(providedCsrfToken);

		final Optional<GuiceDaemon> result = registry
				                                     .getAll()
				                                     .stream()
				                                     .filter(d -> StringUtils.equals(name, d.getName()))
				                                     .findFirst();

		if (result.isPresent())
		{
			final GuiceDaemon daemon = result.get();

			if (daemon.isThreadRunning() && daemon.getThread() != null)
			{
				Thread thread = daemon.getThread();

				thread.interrupt();
			}

			final String message = "Daemon " + daemon.getName() + " sent interrupt at " + DateTime.now();

			return Response
					       .seeOther(UriBuilder
							                 .fromUri(restEndpoint.toString() + "/guice/threads")
							                 .queryParam("message", message)
							                 .build())
					       .build();
		}
		else
		{
			throw new IllegalArgumentException("No daemon with name: " + name);
		}
	}


	private String getDescription(GuiceDaemon daemon)
	{
		Class<?> clazz = daemon.getClass();


		// If we get a guice-enhanced class then we should go up one level to get the class name from the user's code
		if (Enhanced.isEnhanced(clazz))
			clazz = Enhanced.unenhancedClass(clazz).orElseThrow();

		if (clazz.isAnnotationPresent(Doc.class))
		{
			return StringUtils.join(clazz.getAnnotation(Doc.class).value(), "\n");
		}
		else
			return "";
	}
}
