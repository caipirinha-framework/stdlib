package com.peterphi.std.guice.web.rest.jaxrs.exception;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.Templater;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Enumeration;

/**
 * A RestFailureRenderer that uses the configured templating engine to render exceptions destined for humans.
 */
@Singleton
public class CustomTemplateFailureRenderer extends XMLFailureRenderer implements RestFailureRenderer
{
	private static final Logger log = LoggerFactory.getLogger(CustomTemplateFailureRenderer.class);

	@Inject
	Templater templater;


	@Inject
	@Named("exception-templater.template")
	@Doc("The thymeleaf template to use for exceptions (default 'exception')")
	String templateName = "exception";

	/**
	 * If true, a custom template of "{templateName}_{exceptionSimpleName} will be attempted first
	 */
	@Inject
	@Named("exception-templater.tryCustomised")
	@Doc("If enabled, the thymeleaf template for exceptions will be suffixed with _exceptionSimpleName (e.g. templateName_IllegalArgumentException) before falling back to the templateName page (default true)")
	boolean tryCustomised = true;


	@Override
	public Response render(final RestFailure failure)
	{
		if (shouldRender(HttpCallContext.peek()))
		{
			// If we should first try customised exceptions...
			if (tryCustomised)
			{
				try
				{
					final String customTemplateName = templateName + "_" + failure.exception.shortName;

					return render(failure, customTemplateName);
				}
				catch (Exception e)
				{
					log.trace("Error rendering custom failure for {}, fall back on general handler",
					          failure.exception.shortName,
					          e);
				}
			}

			// Fallback on the template name
			return render(failure, templateName);
		}
		else
		{
			return null;
		}
	}


	private boolean shouldRender(final HttpCallContext ctx)
	{
		if (ctx == null)
			return false; // Don't run if we can't figure out the HTTP context

		Enumeration<String> enumeration = (Enumeration<String>) ctx.getRequest().getHeaders("Accept");

		while (enumeration.hasMoreElements())
		{
			final String header = enumeration.nextElement();

			if (StringUtils.startsWithIgnoreCase(header, "text/html"))
				return true;
		}

		// Nobody wanted text/html
		return false;
	}


	private Response render(final RestFailure failure, String template)
	{
		final TemplateCall call = templater.template(template);

		call.set("failure", failure);
		call.set("ctx", HttpCallContext.peek());

		return call.process(Response.status(failure.httpCode).type(MediaType.TEXT_HTML_TYPE));
	}
}
