package com.peterphi.std.guice.web.rest.jaxrs.exception;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceConstants;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.serviceprops.annotations.Reconfigurable;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.pagewriter.BootstrapRestFailurePageRenderer;
import com.peterphi.std.util.ListUtility;
import org.apache.commons.lang.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A HTML renderer that will only emit HTML when the caller lists text/html as their primary Accept header value
 */
@Singleton
public class HTMLFailureRenderer extends XMLFailureRenderer implements RestFailureRenderer
{
	/**
	 * A comma-delimited list of terms that identify highlightable stack trace lines)
	 */
	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_EXCEPTION_HIGHLIGHT)
	protected String highlightTerms = GuiceConstants.JAXRS_EXCEPTION_HIGHLIGHT_SCAN_PACKAGES;


	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_EXCEPTION_HIGHLIGHT_ENABLED)
	protected boolean highlightEnabled = true;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_EXCEPTION_HTML_ENABLED)
	protected boolean enabled = true;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_EXCEPTION_HTML_ONLY_FOR_AUTHENTICATED)
	protected boolean requireLoggedIn = false;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_EXCEPTION_HTML_ONLY_FOR_AUTHENTICATED_ROLE)
	protected String requireRole = null;

	@Reconfigurable
	@Inject(optional = true)
	@Named(GuiceProperties.JAXRS_EXCEPTION_HTML_STACKTRACE)
	protected boolean stackTraceEnabled = true;


	@Inject
	GuiceConfig config;


	@Override
	public Response render(RestFailure failure)
	{
		if (!enabled)
			return null; // don't run if we have been disabled
		if (HttpCallContext.peek() == null)
			return null; // Don't run unless we have an HttpCallContext
		else if (!shouldRenderHtmlForRequest(HttpCallContext.get().getRequest()))
			return null; // Don't run unless we should be rendering HTML for this request
		else if (requireLoggedIn && isLoggedIn(requireRole))
			return null;

		BootstrapRestFailurePageRenderer writer = new BootstrapRestFailurePageRenderer(failure);

		// Optionally enable highlighting
		if (highlightEnabled)
		{
			if (StringUtils.equals(highlightTerms, GuiceConstants.JAXRS_EXCEPTION_HIGHLIGHT_SCAN_PACKAGES))
			{
				// Read scan.packages and use this instead
				writer.setHighlightTerms(config.getList(GuiceProperties.SCAN_PACKAGES, Collections.emptyList()));
			}
			else
			{
				writer.setHighlightTerms(Arrays.asList(highlightTerms.split(",")));
			}
		}

		if (stackTraceEnabled && !shouldStripStackTrace())
		{
			writer.enableStackTrace();
		}


		StringBuilder sb = new StringBuilder();
		writer.writeHTML(sb);

		ResponseBuilder builder = Response.status(failure.httpCode);
		builder.type(MediaType.TEXT_HTML_TYPE);
		builder.entity(sb.toString());

		return builder.build();
	}


	private boolean isLoggedIn(String role)
	{
		try
		{
			final CurrentUser currentUser = currentUserProvider.get();

			if (role == null)
				return !currentUser.isAnonymous();
			else
				return currentUser.hasRole(role);
		}
		catch (Throwable t)
		{
			// ignore errors here
			return false; // assume not logged in
		}
	}


	private boolean shouldRenderHtmlForRequest(HttpServletRequest request)
	{
		@SuppressWarnings("unchecked") final List<String> accepts = ListUtility.list(ListUtility.iterate(request.getHeaders(
				"Accept")));

		for (String accept : accepts)
		{
			if (StringUtils.startsWithIgnoreCase(accept, "text/html"))
				return true;
		}

		return false;
	}
}
