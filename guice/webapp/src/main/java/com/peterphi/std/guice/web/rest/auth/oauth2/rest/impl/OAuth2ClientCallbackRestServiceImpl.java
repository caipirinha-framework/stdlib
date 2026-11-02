package com.peterphi.std.guice.web.rest.auth.oauth2.rest.impl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.auth.oauth2.OAuth2SessionRef;
import com.peterphi.std.guice.web.rest.auth.oauth2.rest.api.OAuth2ClientCallbackRestService;
import com.peterphi.std.util.tracing.Tracing;
import com.peterphi.usermanager.rest.iface.oauth2server.UserManagerOAuthService;
import com.peterphi.usermanager.rest.iface.oauth2server.types.OAuth2TokenResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import java.net.URI;

public class OAuth2ClientCallbackRestServiceImpl implements OAuth2ClientCallbackRestService
{
	private static final Logger log = LoggerFactory.getLogger(OAuth2ClientCallbackRestServiceImpl.class);

	private static final String NO_CACHE = "no-cache";

	// If transparent
	private static long LAST_TRANSPARENT_REDIRECT = System.currentTimeMillis();
	private static final long MAX_TRANSPARENT_REDIRECT_RATE = 30 * 1000;

	@Inject
	UserManagerOAuthService remote;

	@Inject
	@Named("service.oauth2.client_id")
	@Doc("The (public) client_id value shared with the OAuth2 server")
	public String clientId;

	@Inject
	@Named("service.oauth2.client_secret")
	@Doc("The (private) client_secret value shared with the OAuth2 server")
	public String clientSecret;

	@Inject
	Provider<OAuth2SessionRef> sessionRefProvider;

	@Inject(optional = true)
	@Named("service.oauth2.follow-redirect-on-oauth-callback-failure")
	@Doc("If true, instead of displaying an error on an oauth callback error, just redirect the user back to the homepage to go through the oauth cycle again. Subject to global rate limit of default 30s (default true)")
	public boolean followRedirectAnywayOnOAuthCallbackFailure = true;


	@Override
	@AuthConstraint(id = "oauth2_client_callback", skip = true, comment = "Allow non-logged-in users to be redirected to the callback page so they can be logged in")
	public Response callback(final String code,
	                         final String state,
	                         final String error,
	                         final String errorText,
	                         final String errorUri)
	{
		try
		{
			final OAuth2SessionRef sessionRef = sessionRefProvider.get();

			if (StringUtils.isNotBlank(error))
			{
				throw new OAuthServerDeclinedAuthorisationException(
						"The authorisation server failed the authorisation request with error " +
						error +
						" with description " +
						errorText +
						"." +
						((errorUri != null) ? " Additional information can be found at this page: " + errorUri : ""));
			}


			// Now call to exchange the authorisation code for a token
			final String responseStr = remote.getToken(UserManagerOAuthService.GRANT_TYPE_AUTHORIZATION_CODE,
			                                           code,
			                                           sessionRef.getOwnCallbackUri().toString(),
			                                           clientId,
			                                           clientSecret,
			                                           null,
			                                           null,
			                                           null,
			                                           null,
			                                           null);
			final OAuth2TokenResponse response = OAuth2TokenResponse.decode(responseStr);

			// Store the token information so that it is accessible across the session
			sessionRef.load(response);

			return doRedirect(sessionRef, state);
		}
		catch (OAuthServerDeclinedAuthorisationException e)
		{
			// Never attempt to reauth if an error was delivered from the OAuth Server
			throw e;
		}
		catch (Throwable t)
		{
			log.warn("Encountered throwable during OAuth2 validate/redirect. Tracing Local service ID: {}", clientId, t);

			if (followRedirectAnywayOnOAuthCallbackFailure && recordOAuthFailureAndTestIfTransparentRedirectPermitted())
			{
				final String src;
				final HttpCallContext ctx = HttpCallContext.peek();
				if (ctx != null)
				{
					src = ctx.getRequest().getRemoteAddr() +
					      " of " +
					      ctx.getRequest().getRequestURI() +
					      " req sid " +
					      ctx.getRequest().getRequestedSessionId();
				}
				else
				{
					src = "unknown, trace: " + Tracing.getTraceId();
				}

				log.error(
						"Service encountered error processing oauth client callback from client ({}). Redirecting user to site root. Details on request: code={}, state={}, error={}, errorText={}, errorUri={}",
						src,
						code,
						state,
						error,
						errorText,
						errorUri,
						t);

				return doRedirect(null, state);
			}
			else
			{
				throw t;
			}
		}
	}


	/**
	 * Records that we want to trigger a Transparent Redirect on OAuth2 Failure, and are seeking approval.<br />
	 * This will only be permitted if the last event of this type happened long enough ago.
	 *
	 * @return true if the attempt is permitted
	 */
	private boolean recordOAuthFailureAndTestIfTransparentRedirectPermitted()
	{
		final long now = System.currentTimeMillis();
		final long cutoff = now - MAX_TRANSPARENT_REDIRECT_RATE;

		if (LAST_TRANSPARENT_REDIRECT < cutoff)
		{
			// Last redirect happened a while ago, so permit the redirect to proceed
			LAST_TRANSPARENT_REDIRECT = now;
			return true;
		}
		else
		{
			LAST_TRANSPARENT_REDIRECT = now;

			log.warn(
					"Encountered error processing oauth client callback in quick succession, temporarily refusing to transparently redirect in case a client is in a redirect loop");

			return false;
		}
	}


	private Response doRedirect(final OAuth2SessionRef sessionRef, final String state)
	{
		URI redirectTo = null;

		if (sessionRef != null)
		{
			// Check the state nonce value and retrieve the returnTo data
			// This ensures that we always warn the user if the nonce value does not match
			redirectTo = sessionRef.getRedirectToFromState(state);
		}

		return Response.seeOther(redirectTo == null ? URI.create("/") : redirectTo).cacheControl(CacheControl.valueOf(NO_CACHE)).build();
	}
}
