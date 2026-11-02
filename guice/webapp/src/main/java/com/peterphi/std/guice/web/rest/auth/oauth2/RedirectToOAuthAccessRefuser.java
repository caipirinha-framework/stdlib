package com.peterphi.std.guice.web.rest.auth.oauth2;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
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
import java.net.URI;

@Singleton
public class RedirectToOAuthAccessRefuser implements AccessRefuser
{
	@Inject
	Provider<OAuth2SessionRef> sessionRefProvider;


	@Override
	public Throwable refuse(final AuthScope scope, final AuthConstraint constraint, final CurrentUser login)
	{
		final boolean isAnonymous = login.isAnonymous();
		final boolean isBrowser = isBrowserConsumer();

		// For authenticated API users, include role details in the error message
		String ext = "";
		if (!isAnonymous && !isBrowser)
		{
			ext = ". Delegated=" +
			      login.isDelegated() +
			      ", authType=" +
			      login.getAuthType() +
			      ", username=" +
			      login.getUsername() +", role=" + login.getRoles();
		}

		final CredentialsRestException accessDeniedException = new CredentialsRestException(403,
		                                                                                    "You do not have sufficient privileges to access this resource" +
		                                                                                    (constraint != null ?
		                                                                                     ": " + constraint.comment() :
		                                                                                     "") +
		                                                                                    ". Requires one of: " +
		                                                                                    scope.getRoles(constraint) +
		                                                                                    ". You are: anonymous=" +
		                                                                                    isAnonymous +
		                                                                                    ", browser=" +
		                                                                                    isBrowser +
		                                                                                    "" +
		                                                                                    ext);


		// If the user is logged in, deny access with a 403
		if (!isAnonymous)
		{
			throw accessDeniedException;
		}
		else if (!isBrowser)
		{
			// Non-browser consumer, send back an HTTP 401 immediately
			// TODO allow configuration of Basic with a realm?
			Response tryBasicAuth = Response.status(401).header("WWW-Authenticate", "Bearer").build();

			throw new LiteralRestResponseException(tryBasicAuth, accessDeniedException);
		}
		else if (!isGETRequest())
		{
			// Don't redirect requests other than GET (the browser will retry the POST/PUT/DELETE/etc. against the redirect endpoint!
			throw new CredentialsRestException(401,
			                                   "You must log in to access this resource! Could not redirect you to the login provider because you were submitting a form, not requesting a page. Please return to the main page of the application and proceed to log in",
			                                   accessDeniedException);
		}
		else
		{
			// Start an authorisation flow with the OAuth2 provider
			final OAuth2SessionRef sessionRef = sessionRefProvider.get();

			final URI redirectTo = sessionRef.getAuthFlowStartEndpoint(getRequestURI(), null);

			throw new LiteralRestResponseException(Response.seeOther(redirectTo).build(), accessDeniedException);
		}
	}


	private boolean isGETRequest()
	{
		final HttpCallContext ctx = HttpCallContext.peek();

		if (ctx == null)
			return false; // Not an HTTP call!

		final HttpServletRequest request = ctx.getRequest();

		if (request == null)
			return false; // No request!
		else
			return StringUtils.equalsIgnoreCase(request.getMethod(), "GET");
	}


	private boolean isBrowserConsumer()
	{
		final HttpCallContext ctx = HttpCallContext.peek();

		if (ctx == null)
		{
			return false; // Not an HTTP call!
		}

		final HttpServletRequest request = ctx.getRequest();

		if (request == null)
			return true; // be on the safe side and assume a browser
		else if (request.getHeader(HttpHeaderNames.REFERER) != null)
			return true; // assume a browser, services don't generally populate Referer
		else
			return RestThrowableConstants.isHtmlAcceptable(request);
	}


	private String getRequestURI()
	{
		final HttpCallContext ctx = HttpCallContext.peek();

		if (ctx == null)
			return null; // not an HTTP request!

		final HttpServletRequest request = ctx.getRequest();

		if (request == null)
			return null; // No request info

		final String uri = request.getRequestURL().toString();
		final String qs = request.getQueryString();

		if (qs == null)
			return uri;
		else
			return uri + "?" + qs;
	}
}
