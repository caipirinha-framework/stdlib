package com.peterphi.std.guice.web.rest.jaxrs.exception;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.restclient.exception.RestThrowableConstants;
import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.auth.oauth2.CredentialsRestException;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.spi.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Takes an Exception thrown by a JAX-RS provider and renders it prettily
 */
@Provider
public class JAXRSExceptionMapper implements ExceptionMapper<ApplicationException>
{
	private static final Logger log = LoggerFactory.getLogger(JAXRSExceptionMapper.class);

	@Inject
	RestFailureMarshaller marshaller;

	@Inject(optional = true)
	private RestFailureRenderer renderer = null;

	@Inject
	private HTMLFailureRenderer htmlRenderer;

	@Inject
	private XMLFailureRenderer xmlRenderer;

	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_REST_EXCEPTION_CORE_LOGGER_LOGS_TRIMMED_STACKTRACES)
	private boolean coreLoggerLogsTrimmedTraces = true;

	@Override
	public Response toResponse(ApplicationException exception)
	{
		if (exception.getCause() != null && exception instanceof ApplicationException)
		{
			return getResponse(exception.getCause());
		}
		else
		{
			return getResponse(exception);
		}
	}


	public Response getResponse(Throwable exception)
	{
		// WebApplicationException already includes the desired Response to send to the client, so return them directly
		// N.B. we must take care to avoid returning WebApplicationExceptions thrown by JAX-RS Clients, since using their
		// Response will result in the code to render the Response hanging.
		if (exception instanceof WebApplicationException)
		{
			final WebApplicationException webappException = (WebApplicationException) exception;

			// Ignore null responses or exceptions whose Response object is actually a client response (a failure in a remote service call)
			if (webappException.getResponse() != null && !(webappException.getResponse() instanceof ClientResponse))
			{
				return webappException.getResponse();
			}
		}

		// Represent the exception as a RestFailure object
		final RestFailure failure = marshaller.renderFailure(exception);

		Response response = null;

		boolean suppressLog = false;
		// Try to use the custom renderer (if present)
		if (renderer != null)
		{
			try
			{
				response = renderer.render(failure);
				suppressLog = renderer.shouldSuppressLog(failure) || exception instanceof CredentialsRestException;
			}
			catch (Exception e)
			{
				log.warn("Exception rendering RestFailure", e);
			}
		}

		if (!suppressLog)
		{
			if (log.isErrorEnabled())
			{
				if (coreLoggerLogsTrimmedTraces && failure.exception.stackTrace != null)
				{
					// Log the failure
					log.error("{} {} threw exception:\n{}", failure.id, HttpCallContext.get().getRequestInfo(), new PrerenderedStack(failure));
				}
				else {
					// Fall back on full stack trace
					log.error("{} {} threw exception:", failure.id, HttpCallContext.get().getRequestInfo(), exception);
				}
			}
		}

		// Give the HTML render an opportunity to run
		if (response == null && isHtmlAcceptable(HttpCallContext.get()))
			response = htmlRenderer.render(failure);

		// Use the XML renderer if no other renderer has wanted to build the response
		if (response == null)
			return xmlRenderer.render(failure); // Fall back on the XML renderer
		else
			return response;
	}


	private boolean isHtmlAcceptable(HttpCallContext ctx)
	{
		if (ctx == null)
			return false;
		else if (ctx.getRequest() == null)
			return false;
		else
			return RestThrowableConstants.isHtmlAcceptable(ctx.getRequest());
	}
}
