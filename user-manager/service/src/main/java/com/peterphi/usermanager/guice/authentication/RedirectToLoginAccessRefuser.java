package com.peterphi.usermanager.guice.authentication;

import com.peterphi.std.guice.common.auth.AuthScope;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.common.auth.iface.AccessRefuser;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.restclient.exception.RestThrowableConstants;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.jaxrs.exception.LiteralRestResponseException;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.util.HttpHeaderNames;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.util.List;
import java.util.Locale;

class RedirectToLoginAccessRefuser implements AccessRefuser
{
	@Override
	public Throwable refuse(final AuthScope scope, final AuthConstraint constraint, final CurrentUser login)
	{
		AuthenticationFailureException exception = new AuthenticationFailureException(
				"You do not have sufficient privileges to access this resource" +
				(constraint != null ? ": " + constraint.comment() : "") +
				". Required one of roles: " +
				scope.getRoles(constraint));

		if (!login.isAnonymous())
		{
			throw exception;
		}
		else
		{
			final UriBuilder builder = UriBuilder.fromPath("/login");

			// Try to populate returnTo with the page the user tried to access
			if (HttpCallContext.peek() != null)
			{
				final HttpServletRequest request = HttpCallContext.get().getRequest();

				// Apply a heuristic to determine if this is a service or browser request
				if (!isBrowserConsumer(request))
				{
					// Non-browser consumer, send back an HTTP 401 immediately
					Response tryBasicAuth = Response
							                        .status(401)
							                        .header("WWW-Authenticate", "Basic realm=\"user manager\"")
							                        .build();

					throw new LiteralRestResponseException(tryBasicAuth, exception);
				}
				else
				{
					// Don't redirect POST/HEAD/PUT requests to the login page
					if (!request.getMethod().equalsIgnoreCase("GET"))
						throw exception;

					builder.queryParam("returnTo", getRequestURI(request));
				}
			}

			builder.queryParam("errorText", "You must log in to access this page.");

			final Response response = Response.seeOther(builder.build()).build();
			throw new LiteralRestResponseException(response, exception);
		}
	}


	private boolean isBrowserConsumer(final HttpServletRequest request)
	{
		if (request == null)
			return true; // be on the safe side and assume a browser
		else if (request.getHeader(HttpHeaderNames.REFERER) != null)
			return true; // assume a browser, services don't generally populate Referer
		else
			return RestThrowableConstants.isHtmlAcceptable(request);
	}


	private String getRequestURI(HttpServletRequest request)
	{
		final String contextPath = StringUtils.equals(request.getContextPath(), "/") ? "" : request.getContextPath();
		final String requestUri = request.getRequestURI();
		final String qs = (StringUtils.isEmpty(request.getQueryString()) ? "" : "?" + request.getQueryString());

		if (requestUri.startsWith(contextPath))
			return requestUri.substring(contextPath.length()) + qs;
		else
			return requestUri + qs;
	}
}
