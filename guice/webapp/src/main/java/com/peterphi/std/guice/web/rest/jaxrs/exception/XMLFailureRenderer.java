package com.peterphi.std.guice.web.rest.jaxrs.exception;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.serviceprops.annotations.Reconfigurable;
import com.peterphi.std.guice.restclient.exception.RestThrowableConstants;
import com.peterphi.std.guice.restclient.jaxb.ExceptionInfo;
import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import com.peterphi.std.guice.web.HttpCallContext;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

/**
 * A simple renderer that renders an XML form of the RestFailure object by letting the JAX-RS implementation serialise it<br />
 * Sets the X-Rich-Exception flag so that our code can understand and intelligently reconstruct an exception client-side
 */
@Singleton
public class XMLFailureRenderer implements RestFailureRenderer
{
	@Inject
	Provider<CurrentUser> currentUserProvider;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_REST_EXCEPTION_STACKTRACE)
	public boolean includeStackTrace = true;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_REST_EXCEPTION_STACKTRACE_REQUIRE_LOGGED_IN)
	public boolean stackTraceRequiresLoggedIn = false;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_REST_EXCEPTION_STACKTRACE_REQUIRE_ADMIN_OR_SERVICE_IF_LOGGED_IN)
	public boolean stackTraceWhenLoggedInRequiresAdminOrService = true;


	@Override
	public Response render(RestFailure failure)
	{
		final ResponseBuilder builder = Response.status(failure.httpCode);

		boolean strip = shouldStripStackTrace();

		if (strip)
			strip(failure.exception);

		builder.header(RestThrowableConstants.HEADER_RICH_EXCEPTION, "1");
		builder.type(MediaType.APPLICATION_XML_TYPE);
		builder.entity(failure);

		return builder.build();
	}


	boolean shouldStripStackTrace()
	{
		if (HttpCallContext.peek() == null)
			return false; // Don't do anything if we don't have an HttpCallContext

		boolean strip = !includeStackTrace;

		// Decide whether we should strip stack traces
		if (!strip && (stackTraceRequiresLoggedIn || stackTraceWhenLoggedInRequiresAdminOrService))
		{
			// Try to extract stack trace info
			CurrentUser user;
			try
			{
				user = currentUserProvider.get();
			}
			catch (Throwable t)
			{
				user = null;
			}

			final boolean loggedIn = (user != null && !user.isAnonymous());

			if (stackTraceRequiresLoggedIn && !loggedIn)
				strip = true;
			else if (stackTraceWhenLoggedInRequiresAdminOrService)
				strip = loggedIn && !(user.isService() || user.hasRole(CurrentUser.ROLE_ADMIN));
		}
		return strip;
	}


	private void strip(final ExceptionInfo exception)
	{
		if (exception != null)
		{
			exception.stackTrace = null;
			exception.any = null;
			strip(exception.causedBy);
		}
	}
}
