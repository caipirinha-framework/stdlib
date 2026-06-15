package com.peterphi.std.guice.web.rest.auth.userprovider;

import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.peterphi.std.guice.apploader.GuiceConstants;
import com.peterphi.std.guice.common.auth.iface.AccessRefuser;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.cached.CacheManager;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.auth.oauth2.CredentialsRestException;
import com.peterphi.usermanager.util.UserManagerBearerToken;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class HttpCallJWTUser implements CurrentUser
{
	private static final Logger log = LoggerFactory.getLogger(HttpCallJWTUser.class);


	/**
	 * {@link HttpServletRequest} attribute to store the decoded token under
	 */
	private static final String DECODED_JWT = "decoded-jwt";

	/**
	 * Token cache; lets us skip parsing (N.B. need to revalidate "exp" if set)
	 */
	private static final Cache<String, Map<String, Object>> TOKEN_CACHE = CacheManager.build("Parsed JWTs",
	                                                                                         CacheBuilder
			                                                                                         .newBuilder()
			                                                                                         .maximumSize(256)
			                                                                                         .expireAfterWrite(1,
			                                                                                                           TimeUnit.HOURS));

	private final String headerName;
	private final String cookieName;
	private final boolean requireSecure;
	private final JWTVerifier verifier;


	public HttpCallJWTUser(final String headerName,
	                       final String cookieName,
	                       final boolean requireSecure,
	                       final JWTVerifier verifier)
	{
		this.headerName = headerName;
		this.cookieName = cookieName;
		this.requireSecure = requireSecure;
		this.verifier = verifier;
	}


	@Override
	public String getAuthType()
	{
		return GuiceConstants.JAXRS_SERVER_WEBAUTH_JWT_PROVIDER;
	}


	@SuppressWarnings("unchecked")
	private Map<String, Object> get()
	{
		final HttpServletRequest request = HttpCallContext.get().getRequest();

		// Allow the parsed token to be used without revalidation for the duration of an HTTP request
		{
			final Object obj = request.getAttribute(DECODED_JWT);

			if (obj != null && obj instanceof Map)
				return (Map<String, Object>) (Map) obj;
		}

		final String token = getToken(request);

		if (token != null)
		{
			// Parse and validate token
			try
			{
				final Map<String, Object> data = parseToken(token);

				if (requireSecure && !request.isSecure())
				{
					log.error("JWT received over insecure channel (but secure channel mandated)! Token is probably compromised:{}",
					          data);

					throw new RuntimeException("Rejecting token, not received over secure channel");
				}

				// Cache without revalidation for the remainder of this HTTP request
				request.setAttribute(DECODED_JWT, data);

				return data;
			}
			catch (Exception e)
			{
				throw new RuntimeException("JWT Verification failed!", e);
			}
		}
		else
		{
			throw new RuntimeException("User is authenticated by JWT but no JWT found!");
		}
	}


	private Map<String, Object> parseToken(final String token) throws NoSuchAlgorithmException, InvalidKeyException, IOException, SignatureException
	{
		final Map<String, Object> cached = TOKEN_CACHE.getIfPresent(token);

		// N.B. If token is present in cache then we need to revalidate expire time
		if (cached != null)
		{
			// If the token has an expire time then we must check it has not passed yet
			// If the token has no expire time then the cache entry can be returned immediately
			if (cached.containsKey("exp"))
			{
				// Token has an expire time, must revalidate before returning

				// exp holds seconds since 1970 timestamp for the expiry time
				final long expireTimestamp = 1000L * (long) cached.get("exp");

				// N.B. if we don't return the cached value we'll
				if (expireTimestamp < System.currentTimeMillis())
					return cached;
				else
					TOKEN_CACHE.invalidate(token); // invalidate this cache entry, it has expired. The fallback code at the end of this method will run and provide a consistent token expired error message
			}
			else
			{
				// Token has no expire time, can be returned as-is
				return cached;
			}
		}

		// Fall back on parsing the token
		Map<String, Object> parsed = verifier.verify(token);

		// Store the token in the cache
		TOKEN_CACHE.put(token, parsed);

		return parsed;
	}


	/**
	 * Returns true if and only if a JWT has been provided, but <strong>does not validate the token</strong>
	 *
	 * @return
	 */
	public boolean hasToken()
	{
		final HttpServletRequest request = HttpCallContext.get().getRequest();

		return getToken(request) != null;
	}


	String getToken(final HttpServletRequest request)
	{
		// Try a bare HTTP Header
		if (headerName != null)
		{
			final String val = request.getHeader(this.headerName);

			if (val != null)
				return val;
		}

		// Try the HTTP Authorization header
		{
			final String header = request.getHeader(HttpHeaderNames.AUTHORIZATION);

			if (header != null)
			{
				return parseBasicAuth(header);
			}
		}

		// Try reading from a cookie
		if (cookieName != null)
		{
			final Cookie[] cookies = request.getCookies();

			if (cookies != null)
				for (Cookie cookie : cookies)
					if (StringUtils.equals(cookie.getName(), cookieName))
						return cookie.getValue();
		}

		// No token found
		return null;
	}


	static String parseBasicAuth(final String header)
	{
		try
		{
			if (header.length() < 6)
				return null;

			if (StringUtils.startsWithIgnoreCase(header, "Basic"))
			{
				// JWT bundled into HTTP Basic auth
				String val = header.substring("Basic".length() + 1);

				val = new String(org.apache.commons.codec.binary.Base64.decodeBase64(val.getBytes()), "UTF-8");

				String[] split = val.split(":", 2);

				if (split.length != 2)
					return null;
				else if (StringUtils.equals("jwt", split[0])) // Username jwt
					return split[1];
				else
					return null;
			}
			else if (StringUtils.startsWithIgnoreCase(header, "Bearer") &&
			         !UserManagerBearerToken.isUserManagerBearerAuthorizationHeader(header))
			{
				// Bearer token
				return header.substring("Bearer".length() + 1);
			}
			else
			{
				return null; // unrecognised auth type
			}
		}
		catch (Exception e)
		{
			log.warn("Error extracting JWT from HTTP Auth header", e);
			return null;
		}
	}


	@Override
	public boolean isAnonymous()
	{
		return (get() == null);
	}


	@Override
	public boolean isDelegated()
	{
		return false;
	}


	@Override
	public boolean isService()
	{
		return false;
	}


	@Override
	public String getName()
	{
		final Map<String, Object> data = get();

		if (data.containsKey("name"))
			return String.valueOf(data.get("name"));
		else
			return getUsername();
	}


	@Override
	public String getUsername()
	{
		final Map<String, Object> data = get();

		if (data.containsKey("sub"))
			return String.valueOf(data.get("sub"));
		else
			return "jwt-user-nosubject";
	}


	@Override
	@SuppressWarnings("unchecked")
	public boolean hasRole(final String role)
	{
		final Map<String, Object> data = get();

		// Special case the "authenticated" role - need only have a valid token, no role is required
		if (StringUtils.equals(CurrentUser.ROLE_AUTHENTICATED, role))
		{
			return data != null;
		}
		else
		{
			final List<String> roles = (List<String>) data.get("roles");

			if (roles == null)
				return false; // No roles specified!
			else
				return roles.contains(role);
		}
	}


	@Override
	public AccessRefuser getAccessRefuser()
	{
		return (scope, constraint, user) -> {
			if (user.isAnonymous())
				return new CredentialsRestException(401,
				                                    "You must log in to access this resource. Required one of roles: " +
				                                    scope.getRoles(constraint));
			else
				return new CredentialsRestException(403,
				                                    "Access denied for your JWT by rule: " +
				                                    ((constraint != null) ?
				                                     constraint.comment() :
				                                     "(default)" + ". Required one of roles: " + scope.getRoles(constraint)));
		};
	}


	@Override
	public DateTime getExpires()
	{
		final Number epochSeconds = (Number) get().get("exp");

		if (epochSeconds != null)
		{
			final long millis = epochSeconds.longValue() * 1000L;

			return new DateTime(millis);
		}
		else
		{
			return null; // No expire time set
		}
	}


	@Override
	public Set<String> getRoles()
	{
		final Map<String, Object> data = get();

		if (data == null)
		{
			return Collections.emptySet();
		}
		else {
			final List<String> roles = (List<String>) data.get("roles");

			final Set<String> ret = new HashSet<>(roles);

			ret.add(CurrentUser.ROLE_AUTHENTICATED);

			return Collections.unmodifiableSet(ret);
		}
	}


	@Override
	public Map<String, Object> getClaims()
	{
		return Collections.unmodifiableMap(get());
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("claims", get()).toString();
	}
}
