package com.peterphi.usermanager.rest.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.retry.annotation.Retry;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.Templater;
import com.peterphi.usermanager.db.dao.hibernate.OAuthDelegatedTokenDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.OAuthServiceDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.OAuthSessionContextDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.OAuthSessionDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.UserDaoImpl;
import com.peterphi.usermanager.db.entity.OAuthDelegatedTokenEntity;
import com.peterphi.usermanager.db.entity.OAuthServiceEntity;
import com.peterphi.usermanager.db.entity.OAuthSessionContextEntity;
import com.peterphi.usermanager.db.entity.OAuthSessionEntity;
import com.peterphi.usermanager.db.entity.RoleEntity;
import com.peterphi.usermanager.db.entity.UserEntity;
import com.peterphi.usermanager.guice.authentication.UserLogin;
import com.peterphi.usermanager.guice.token.CSRFTokenStore;
import com.peterphi.usermanager.rest.iface.oauth2server.UserManagerOAuthService;
import com.peterphi.usermanager.rest.iface.oauth2server.types.OAuth2TokenResponse;
import com.peterphi.usermanager.rest.marshaller.UserMarshaller;
import com.peterphi.usermanager.rest.type.UserManagerUser;
import com.peterphi.usermanager.service.LoginRateLimiter;
import com.peterphi.usermanager.util.UserManagerBearerToken;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.util.BasicAuthHelper;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class UserManagerOAuthServiceImpl implements UserManagerOAuthService
{
	private static final Logger log = LoggerFactory.getLogger(UserManagerOAuthServiceImpl.class);

	private static final String NO_CACHE = "no-cache";

	@Inject(optional = true)
	@Named("auth.token.refresh-period")
	@Doc("The period after which an OAuth2 consumer will have to refresh their access token. If they do not expire it by this timeout then a new session must be re-established (default PT3H)")
	public Period tokenRefreshInterval = Period.parse("PT3H");

	@Inject(optional = true)
	@Named("auth.all.create-new-session-context-if-necessary")
	@Doc("If true, access will be permitted automatically to services they have never used before regardless of how the user was authenticated (default false)")
	public boolean autoGrantAccessToAllServices = false;

	@Inject(optional = true)
	@Named("auth.interactive.create-new-session-context-if-necessary")
	@Doc("If true, when used interactively then access will be permitted automatically to services they have never used before without prompting (default false)")
	boolean autoGrantInteractiveAccessToAllServices = false;

	@Inject(optional = true)
	@Named("auth.access-key.create-new-session-context-if-necessary")
	@Doc("If true then when using an Access Key, access will be permitted automatically to services they have never used before (default false)")
	public boolean autoGrantAccessKeysToAccessAllServices = false;

	@Inject
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	public URI ownEndpoint;

	@Inject
	Templater templater;

	@Inject
	Provider<CSRFTokenStore> tokenStoreProvider;

	@Inject
	Provider<UserLogin> loginProvider;

	@Inject
	OAuthSessionContextDaoImpl contextDao;

	@Inject
	OAuthSessionDaoImpl sessionDao;

	@Inject
	UserDaoImpl userDao;

	@Inject
	OAuthServiceDaoImpl serviceDao;

	@Inject
	OAuthDelegatedTokenDaoImpl delegatedTokenDao;

	@Inject
	UserMarshaller marshaller;

	@Inject
	LoginRateLimiter loginRateLimiter;


	@Override
	@AuthConstraint(id = "oauth2server_auth", role = "authenticated", comment = "Must be logged in to the User Manager to initiate a service login")
	@Retry
	public Response getAuth(final String responseType,
	                        final String clientId,
	                        final String redirectUri,
	                        final String state,
	                        final String scope)
	{
		// Has the current user approved this client+scope before? If so just redirect straight back
		// Otherwise, bring up the authorisation UI
		final Response response = createSessionAndRedirect(responseType, clientId, redirectUri, state, scope,
		                                                   autoGrantInteractiveAccessToAllServices);

		if (response != null)
		{
			return response;
		}
		else
		{
			final OAuthServiceEntity client = serviceDao.getByClientIdAndEndpoint(clientId, redirectUri);

			if (client == null)
				throw new IllegalArgumentException("Unknown client_id=" +
				                                   clientId +
				                                   " or invalid redirect uri for this service: " +
				                                   redirectUri);

			final TemplateCall call = templater.template("connect_to_service");

			CSRFTokenStore tokenStore = tokenStoreProvider.get();

			// Provide additional client information
			call.set("client", client);
			call.set("token", tokenStore.allocate());

			// Scopes as a list
			if (StringUtils.isBlank(scope))
				call.set("scopes", Collections.emptyList());
			else
				call.set("scopes", Arrays.asList(StringUtils.trimToEmpty(scope).split(" ")));

			// Copy the request info
			call.set("clientId", client.getId());
			call.set("responseType", responseType);
			call.set("redirectUri", redirectUri);
			call.set("scope", scope);
			call.set("state", state);

			return call.process(Response.ok().type(MediaType.TEXT_HTML).cacheControl(CacheControl.valueOf(NO_CACHE)));
		}
	}


	@Override
	@AuthConstraint(id = "oauth2server_auth", role = "authenticated", comment = "Must be logged in to the User Manager")
	public Response userMadeAuthDecision(final String responseType,
	                                     final String clientId,
	                                     final String redirectUri,
	                                     final String state,
	                                     final String scope,
	                                     final String token,
	                                     final String decision)
	{
		final CSRFTokenStore tokenStore = tokenStoreProvider.get();

		// Make sure the token is valid before we do anything. This makes sure we are responding to a real user interacting with our UI
		tokenStore.validate(token);

		if (StringUtils.equalsIgnoreCase(decision, "allow"))
		{
			// Create a new Session (creating an approval record for this client+scope) and redirect the user back to the calling site
			return createSessionAndRedirect(responseType, clientId, redirectUri, state, scope, true);
		}
		else
		{
			return redirectError(redirectUri,
			                     state,
			                     "access_denied",
			                     "The Deny button was clicked, denying authorisation to the user account for this service");
		}
	}


	@Transactional
	Response createSessionAndRedirect(final String responseType,
	                                         final String clientId,
	                                         final String redirectUri,
	                                         final String state,
	                                         final String scope,
	                                         final boolean allowCreateApproval)
	{
		final OAuthSessionEntity session = createSession(loginProvider.get().getId(),
		                                                 clientId,
		                                                 redirectUri,
		                                                 scope,
		                                                 allowCreateApproval);

		if (session != null)
		{

			// Figure out where to redirect the user back to
			return redirectSuccess(responseType, redirectUri, state, session);
		}
		else
		{
			return null; // No session was created
		}
	}


	private URI buildRedirectUri(final String redirectUri)
	{
		final URI desired = URI.create(redirectUri);

		// If URI matches our endpoint's protocol/host/port, return a server-relative URI
		if (desired.getScheme().equals(ownEndpoint.getScheme()) &&
		    desired.getHost().equals(ownEndpoint.getHost()) &&
		    desired.getPort() == ownEndpoint.getPort())
		{
			String path = desired.getRawPath();
			String query = desired.getRawQuery();
			return URI.create(path + (query != null ? "?" + query : ""));
		}


		return desired;
	}

	private Response redirectSuccess(final String responseType,
	                                 final String redirectUri,
	                                 final String state,
	                                 final OAuthSessionEntity session)
	{
		final URI redirectTo;
		{
			final URI redirect = buildRedirectUri(redirectUri);

			final UriBuilder builder = UriBuilder.fromUri(redirect);

			builder.replaceQueryParam(responseType, session.getAuthorisationCode());

			if (state != null)
				builder.replaceQueryParam("state", state);

			redirectTo = builder.build();
		}

		// Redirect the user
		if (!redirectTo.isAbsolute())
			return Response.status(Response.Status.SEE_OTHER).header("Location", redirectTo.toString()).build();
		else
			return Response.seeOther(redirectTo).build();
	}


	private Response redirectError(final String redirectUri, final String state, final String error, final String errorText)
	{
		final URI redirectTo;
		{
			final UriBuilder builder = UriBuilder.fromUri(buildRedirectUri(redirectUri));

			builder.replaceQueryParam("error", error);
			builder.replaceQueryParam("error_description", errorText);

			if (state != null)
				builder.replaceQueryParam("state", state);

			redirectTo = builder.build();
		}

		// Redirect the user
		if (!redirectTo.isAbsolute())
			return Response.status(Response.Status.SEE_OTHER).header("Location", redirectTo.toString()).build();
		else
			return Response.seeOther(redirectTo).build();
	}

	OAuthSessionEntity createSession(final int userId,
	                                        final String clientId,
	                                        final String redirectUri,
	                                        final String scope,
	                                        final boolean allowCreateApproval) throws ServiceAccessPreconditionFailed
	{
		final OAuthServiceEntity client = serviceDao.getByClientIdAndEndpoint(clientId, redirectUri);

		if (client == null)
			throw new IllegalArgumentException("No such client with id " +
			                                   clientId +
			                                   " at the provided endpoint! There is a problem with the service that sent you here.");

		final boolean canAccessService = canUserAccessService(userId, client.getRequiredRoleName());

		if (!canAccessService)
			throw new ServiceAccessPreconditionFailed("This user is missing the required role to permit it to use this service!");

		OAuthSessionContextEntity context = contextDao.get(userId, client.getId(), scope);

		// Try to create a context for a session to live within (if permitted)
		if (context == null)
		{
			if (allowCreateApproval || autoGrantAccessToAllServices)
				context = contextDao.create(userDao.getById(userId), client, scope);
			else
				return null; // Not allowed to create an approval so cannot create a session
		}

		// Now create a Session
		return sessionDao.create(context, computeInitiatorInfo(), DateTime.now().plus(tokenRefreshInterval));
	}


	private boolean canUserAccessService(final int userId, final String requiredRoleName)
	{
		if (StringUtils.isNotEmpty(requiredRoleName))
		{
			final UserEntity user = userDao.getById(userId);

			final Set<String> roleNames = user
					                              .getRoles()
					                              .stream()
					                              .map(r -> StringUtils.lowerCase(r.getId()))
					                              .collect(Collectors.toSet());

			for (String role : StringUtils.split(requiredRoleName, '|'))
			{
				if (roleNames.contains(StringUtils.lowerCase(StringUtils.trimToEmpty(role))))
				{
					// Found role match
					return true;
				}
			}

			// No roles matched
			return false;
		}
		else
		{
			return true; // no restriction
		}
	}


	private String computeInitiatorInfo()
	{
		final HttpServletRequest request = HttpCallContext.get().getRequest();


		final String forwardedFor = request.getHeader("X-Forwarded-For");

		if (StringUtils.isBlank(forwardedFor))
			return "addr:" + request.getRemoteAddr();
		else
			return "addr:" + request.getRemoteAddr() + " via-proxies:" + forwardedFor;
	}


	@Override
	@Transactional
	@AuthConstraint(id = "oauth2server_token", skip = true)
	public String getToken(final String grantType,
	                       final String code,
	                       final String redirectUri,
	                       String clientId,
	                       String secret,
	                       final String refreshToken,
	                       final String username,
	                       final String password,
	                       final String subjectToken,
	                       final String authorizationHeader)
	{
		OAuthSessionEntity session;

		// Allow clients to supply their ID and Secret using Basic Auth (per RFC)
		if (StringUtils.isNotEmpty(authorizationHeader) && StringUtils.isEmpty(clientId) && StringUtils.isEmpty(secret))
		{
			// N.B. returns null if not BASIC auth (e.g. Bearer auth)
			final String[] credentials = BasicAuthHelper.parseHeader(authorizationHeader);

			if (credentials != null)
			{
				clientId = credentials[0];
				secret = credentials[1];
			}
		}

		switch (grantType)
		{
			case GRANT_TYPE_AUTHORIZATION_CODE:
			{
				final OAuthServiceEntity service = serviceDao.getByClientIdAndSecretAndEndpoint(clientId, secret, redirectUri);

				if (service == null)
					throw new IllegalArgumentException("One or more of OAuth Client's Client ID / Client Secret / Redirect URI were not valid");

				session = sessionDao.exchangeCodeForToken(service, code);

				if (session == null)
					throw new IllegalArgumentException("Unable to exchange authorisation code for a token!");

				break;
			}
			case GRANT_TYPE_REFRESH_TOKEN:
			{
				if (UserManagerBearerToken.isUserManagerServiceBearer(refreshToken))
				{
					// If a Service Token is provied as a Refresh Token, treat the call as a Service API Key Token Exchange
					// This is necessary because a Service User isn't a real user and so can't have a Session
					return getToken(GRANT_TYPE_TOKEN_EXCHANGE,
					                code,
					                redirectUri,
					                clientId,
					                secret,
					                null,
					                username,
					                password,
					                refreshToken, // Use provided refresh token as the subject token for new invocation
					                authorizationHeader);
				}
				else
				{
					// Regular refresh token
					final OAuthServiceEntity service = serviceDao.getByClientIdAndSecretAndEndpoint(clientId,
					                                                                                secret,
					                                                                                redirectUri);

					if (service == null)
						throw new IllegalArgumentException(
								"One or more of OAuth Client's Client ID / Client Secret / Redirect URI were not valid");

					session = sessionDao.exchangeRefreshTokenForNewToken(service,
					                                                     refreshToken,
					                                                     new DateTime().plus(tokenRefreshInterval));

					if (session == null)
						throw new IllegalArgumentException("Unable to exchange refresh token for a token!");

					break;
				}
			}
			case GRANT_TYPE_PASSWORD:
			{
				// N.B. Don't expect the clientSecret from this call

				// Enforce per-account failed-login rate limit (defaults to 5 failures per 60s window)
				loginRateLimiter.checkAllowed(username);

				final UserEntity user = userDao.login(username, password);

				if (user == null)
				{
					loginRateLimiter.recordFailure(username);
					throw new IllegalArgumentException("Incorrect username/password combination");
				}

				loginRateLimiter.recordSuccess(username);

				// Accept the use of the service and create a new session
				session = createSession(user.getId(), clientId, redirectUri, "password-to-token", true);

				// Take the authorisation code internally and exchange it for a token
				session = sessionDao.exchangeCodeForToken(session.getContext().getService(), session.getAuthorisationCode());

				if (session == null)
					throw new IllegalArgumentException("Unable to exchange username/password for a token!");

				break;
			}
			case GRANT_TYPE_TOKEN_EXCHANGE:
			{
				final OAuthServiceEntity service = serviceDao.getByClientIdAndSecretAndEndpoint(clientId, secret, redirectUri);

				if (service == null)
					throw new IllegalArgumentException("One or more of OAuth Client's Client ID / Client Secret / Redirect URI were not valid");

				if (UserManagerBearerToken.isUserManagerDelegatedBearer(subjectToken))
				{
					final String delegatedTokenId = subjectToken.substring(UserManagerBearerToken.PREFIX_DELEGATED.length());

					OAuthDelegatedTokenEntity delegated = delegatedTokenDao.getByIdUnlessExpired(delegatedTokenId);

					if (delegated == null)
						throw new IllegalArgumentException("Delegated Token ID invalid, expired or refers to expired or invalidated Session!");

					// Return a fake session reference
					return new OAuth2TokenResponse(subjectToken, null, delegated.getExpires().toDate()).encode();
				}
				else if (UserManagerBearerToken.isUserManagerServiceBearer(subjectToken))
				{
					final OAuthServiceEntity serviceUser = serviceDao.getByAccessKey(subjectToken);

					if (serviceUser == null)
						throw new IllegalArgumentException("Service Access Key not recognised");

					// Return an expiring session (to allow old keys to be rotated and invalidated)
					// N.B. refresh token is the same as this token, which will cause this logic to run again on refresh
					return new OAuth2TokenResponse(subjectToken, subjectToken, new DateTime().plus(tokenRefreshInterval).toDate()).encode();
				}
				else
				{
					final UserEntity user = userDao.loginByAccessKey(subjectToken);

					if (user == null)
						throw new IllegalArgumentException("Access Key not recognised");

					// Accept the use of the service and create a new session
					// N.B. do not allow token exchange to be used to gain access to a service this user has not explicitly granted
					session = createSession(user.getId(),
					                        clientId,
					                        redirectUri,
					                        GRANT_TYPE_TOKEN_EXCHANGE,
					                        autoGrantAccessKeysToAccessAllServices);

					if (session == null)
						throw new IllegalArgumentException("The User associated with this Access Key (" +
						                                   user.getName() +
						                                   ") has not approved access to this service yet");

					// Take the authorisation code internally and exchange it for a token
					session = sessionDao.exchangeCodeForToken(session.getContext().getService(), session.getAuthorisationCode());

					if (session == null)
						throw new IllegalArgumentException("Unable to exchange token!");
				}

				break;
			}
			case GRANT_TYPE_CLIENT_CREDENTIALS:
			default:
			{
				throw new IllegalArgumentException("unsupported grant_type: " + grantType);
			}
		}

		if (session == null)
			throw new IllegalArgumentException("Unable to acquire token.");

		return new OAuth2TokenResponse(session.getToken(), session.getId(), session.getExpires().toDate()).encode();
	}


	@Override
	@Transactional(readOnly = true)
	@AuthConstraint(id = "oauth2server_token_to_userinfo", skip = true)
	public UserManagerUser get(final String token, final String clientId)
	{
		if (UserManagerBearerToken.isUserManagerDelegatedBearer(token))
		{
			final String delegatedTokenId = token.substring(UserManagerBearerToken.PREFIX_DELEGATED.length());
			final OAuthDelegatedTokenEntity delegated = delegatedTokenDao.getByIdUnlessExpired(delegatedTokenId);

			if (delegated == null)
				throw new IllegalArgumentException("Delegated Token provided is invalid or has expired!");

			final UserEntity user = delegated.getSession().getContext().getUser();

			UserManagerUser obj = marshaller.marshal(user);

			obj.delegated = true;
			obj.originService = delegated.getSession().getContext().getService().getId();
			obj.roles.add("origin_" + obj.originService); // Expose the service in communication with the user as a role too for legacy clients

			obj.roles.add(CurrentUser.ROLE_DELEGATED);
			obj.roles.add(CurrentUser.ROLE_SERVICE_CALL);

			// Add the service's roles too
			for (RoleEntity serviceRole : delegated.getSession().getContext().getService().getRoles())
			{
				obj.roles.add(serviceRole.getId());
			}

			return obj;
		}
		else if (UserManagerBearerToken.isUserManagerServiceBearer(token))
		{
			final OAuthServiceEntity serviceUser = serviceDao.getByAccessKey(token);

			if (serviceUser == null)
				throw new IllegalArgumentException("Service Access Key not recognised");

			UserEntity fakeUser = new UserEntity();

			fakeUser.setName(serviceUser.getId());
			fakeUser.setEmail(serviceUser.getId() + "@service.localhost");
			fakeUser.setLocal(true);
			fakeUser.setRoles(new ArrayList<>(serviceUser.getRoles()));
			fakeUser.setTimeZone(CurrentUser.DEFAULT_TIMEZONE);
			fakeUser.setDateFormat(CurrentUser.ISO_DATE_FORMAT_STRING);

			UserManagerUser obj = marshaller.marshal(fakeUser);

			obj.service = true;
			obj.delegated = false;

			obj.roles.add(CurrentUser.ROLE_SERVICE_CALL);

			return obj;
		}
		else
		{
			final OAuthSessionEntity session = getSessionForToken(token, clientId);

			return marshaller.marshal(session.getContext().getUser());
		}
	}


	OAuthSessionEntity getSessionForToken(final String token, final String clientId) {
		OAuthSessionEntity session = sessionDao.getByToken(token);

		if (session == null)
			throw new IllegalArgumentException("No such user with token " + token);

		// Check the clientId matches (if provided)
		if (clientId != null)
		{
			final OAuthServiceEntity service = session.getContext().getService();

			if (!StringUtils.equals(service.getId(), clientId))
			{
				log.warn(
						"Service {} tried to swap token in context {} for user info but token was generated for {} instead! User may be under attack.",
						clientId,
						session.getContext().getId(),
						session.getContext().getService().getId());

				throw new IllegalArgumentException("This token was not generated for client " + clientId + "!");
			}
		}

		return session;
	}


	@Override
	@Transactional(readOnly = true)
	@AuthConstraint(id = "oauth2server_token_to_userinfo", skip = true)
	public Response getOIDCUserInfo(final String bearerTokenHeader)
	{
		if (StringUtils.isEmpty(bearerTokenHeader))
		{
			return Response.status(401).header("WWW-Authenticate", "Bearer").build();
		}
		else
		{
			try
			{
				final String token = UserManagerBearerToken.getTokenFromBearerAuthorizationHeader(bearerTokenHeader);

				// OpenID Connect userinfo doesn't provide Client ID, so we can't provide additional protection against this token from being forced into a different client
				final OAuthSessionEntity session = getSessionForToken(token, null);

				final String json = createOpenIDConnectUserInfo(session);
				return Response.ok(json, MediaType.APPLICATION_JSON).build();
			}
			catch (Throwable t)
			{
				log.warn("Error in UserInfo resource", t);

				return Response
						       .status(401)
						       .header("WWW-Authenticate",
						               "error=\"invalid_token\", error_description=\"invalid or expired token\"")
						       .build();
			}
		}
	}


	String createOpenIDConnectUserInfo(OAuthSessionEntity session)
	{
		try
		{
			final ObjectMapper mapper = new ObjectMapper();
			final ObjectNode obj = mapper.createObjectNode();

			final UserEntity user = session.getContext().getUser();

			obj.put("sub", user.getId()); // Unique ID for this user
			obj.put("client_id", session.getContext().getService().getId()); // Audience
			obj.put("iat", session.getCreated().getMillis() / 1000); // Issued At
			obj.put("exp", session.getExpires().getMillis() / 1000); // Expires

			// Optional fields (N.B. technically per spec this info should be in a specifically requested scope)
			obj.put("name", user.getName());
			obj.put("email", user.getEmail());
			obj.put("zoneinfo", user.getTimeZone());
			obj.put("datetime_format", user.getDateFormat());

			final ArrayNode roles = mapper.createArrayNode();
			user.getRoles().stream().map(r -> r.getId()).forEach(roles :: add);

			// Different clients use different key names here, so populate many claims with the same data
			obj.set("groups", roles);
			obj.set("roles", roles);
			obj.set("group", roles);
			obj.set("role", roles);

			return mapper.writeValueAsString(obj);
		}
		catch (JsonProcessingException e)
		{
			throw new RuntimeException("Unable to serialise OAuth2TokenResponse: " + e.getMessage(), e);
		}
	}


	@Override
	@AuthConstraint(id = "oauth2server_token_to_userinfo", skip = true)
	public Response getOIDCUserInfoPost(final String bearerTokenHeader)
	{
		return getOIDCUserInfo(bearerTokenHeader);
	}


	@Override
	@Transactional
	@AuthConstraint(id = "oauth2server_token", skip = true)
	public String createDelegatedAccessToken(String clientId,
	                                         String secret,
	                                         final long validityPeriod,
	                                         final String refreshToken,
	                                         final String authorizationHeader)
	{
		if (validityPeriod > Integer.MAX_VALUE)
			throw new IllegalArgumentException("Validity period too long!");

		// Allow clients to supply their ID and Secret using Basic Auth (per RFC)
		if (StringUtils.isNotEmpty(authorizationHeader) && StringUtils.isEmpty(clientId) && StringUtils.isEmpty(secret))
		{
			// N.B. returns null if not BASIC auth (e.g. Bearer auth)
			final String[] credentials = BasicAuthHelper.parseHeader(authorizationHeader);

			if (credentials != null)
			{
				clientId = credentials[0];
				secret = credentials[1];
			}
		}

		final OAuthServiceEntity service = serviceDao.getByClientIdAndSecretOnly(clientId, secret);

		if (service == null)
			throw new IllegalArgumentException(
					"One or more of OAuth Client's Client ID / Client Secret / Redirect URI were not valid");

		final OAuthSessionEntity session = sessionDao.getSessionToDelegateByRefreshToken(service, refreshToken);

		if (session == null)
			throw new IllegalArgumentException("Refresh Token provided is no longer valid!");

		final DateTime expires = DateTime.now().plusMillis((int) validityPeriod);

		final OAuthDelegatedTokenEntity delegatedToken = delegatedTokenDao.create(session, expires);

		// Return the delegated token as a User Manager Bearer Token (opaque to the client service)
		return UserManagerBearerToken.PREFIX_DELEGATED + delegatedToken.getId();
	}
}
