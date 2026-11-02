package com.peterphi.std.guice.web.rest.auth.oauth2;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.peterphi.std.guice.apploader.GuiceConstants;
import com.peterphi.std.guice.common.auth.iface.AccessRefuser;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.lifecycle.GuiceLifecycleListener;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.scoping.SessionScoped;
import com.peterphi.usermanager.rest.type.UserManagerUser;
import com.peterphi.usermanager.util.UserManagerBearerToken;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SessionScoped
public class OAuthUser implements CurrentUser, GuiceLifecycleListener
{
	@Inject
	RedirectToOAuthAccessRefuser accessRefuser;

	@Inject
	Provider<OAuth2SessionRef> sessionRefProvider;

	/**
	 * If logged in with an Bearer API Token then use this static session instead of constructing one (this is for API calls, where there'll be a Session per Request)<br />
	 * If this is null (the default) then getSession will construct a session using {@link #sessionRefProvider}, otherwise if non-null this static value will be used
	 */
	private OAuth2SessionRef staticSession;

	@Inject
	Provider<UserManagerAccessKeyToSessionCache> apiSessionRefCacheProvider;

	DateTimeFormatter _dateFormatter = null;


	@Override
	public String getAuthType()
	{
		return GuiceConstants.JAXRS_SERVER_WEBAUTH_OAUTH2_PROVIDER;
	}


	@Override
	public boolean isAnonymous()
	{
		return !getSession().isValid();
	}


	@Override
	public boolean isDelegated()
	{
		if (isAnonymous())
			return false;
		else
			return getSession().getUserInfo().delegated;
	}


	@Override
	public boolean isService()
	{
		if (isAnonymous())
			return false;
		else
			return getSession().getUserInfo().service;
	}


	private OAuth2SessionRef getSession()
	{
		if (staticSession != null)
			return staticSession;
		else
			return sessionRefProvider.get();
	}


	@Override
	public String getName()
	{
		if (isAnonymous())
			return "Anonymous";
		else
			return getSession().getUserInfo().name;
	}


	@Override
	public String getUsername()
	{
		if (isAnonymous())
			return "anonymous@localhost";
		else
			return getSession().getUserInfo().email;
	}


	@Override
	public boolean hasRole(final String role)
	{
		if (isAnonymous())
		{
			return false;
		}
		else
		{
			if (StringUtils.equals(role, CurrentUser.ROLE_AUTHENTICATED))
				return true;
			else
				return getSession().getUserInfo().roles.contains(role);
		}
	}


	@Override
	public DateTime getExpires()
	{
		return null; // We have no info on hard expiration
	}


	@Override
	public Set<String> getRoles()
	{
		if (isAnonymous())
		{
			return Collections.emptySet();
		}
		else
		{
			final Set<String> roles = new HashSet<>(getSession().getUserInfo().roles);

			roles.add(CurrentUser.ROLE_AUTHENTICATED);

			return Collections.unmodifiableSet(roles);
		}
	}


	@Override
	public Map<String, Object> getClaims()
	{
		return Collections.emptyMap();
	}


	@Override
	public String getSimpleClaim(final String name)
	{
		return null;
	}


	@Override
	public List<String> getSimpleListClaim(final String name)
	{
		return null;
	}


	@Override
	public Set<String> getSimpleSetClaim(final String name)
	{
		return null;
	}


	@Override
	public String format(final DateTime date)
	{
		if (date != null)
			return getDateFormatter().print(date);
		else
			return null;
	}

	@Override
	public DateTimeFormatter getDateFormatter()
	{
		if (_dateFormatter != null)
		{
			return _dateFormatter;
		}
		else
		{
			if (!isAnonymous())
			{
				if (_dateFormatter == null)
				{
					final UserManagerUser userInfo = getSession().getUserInfo();

					try
					{
						_dateFormatter = userInfo.toDateTimeFormatter();
					}
					catch (Throwable t)
					{
						throw new IllegalArgumentException("Error trying to parse user dateFormat/timeZone!", t);
					}
				}

				return _dateFormatter;
			}

			return CurrentUser.DEFAULT_DATE_FORMAT;
		}
	}


	@Override
	public String format(final Instant date)
	{
		if (date != null)
			return getDateFormatter().print(date.toEpochMilli());
		else
			return null;
	}


	@Override
	public String format(final Date date)
	{
		if (date != null)
			return getDateFormatter().print(date.getTime());
		else
			return null;
	}


	@Override
	public AccessRefuser getAccessRefuser()
	{
		return accessRefuser;
	}


	public String getOrCreateDelegatedToken()
	{
		return getSession().getOrCreateDelegatedToken();
	}


	@Override
	public void postConstruct()
	{
		// If there's an Authorization: Bearer
		final HttpCallContext ctx = HttpCallContext.peek();

		if (ctx != null)
		{
			final HttpServletRequest request = ctx.getRequest();

			final String header = request.getHeader("Authorization");

			if (UserManagerBearerToken.isUserManagerBearerAuthorizationHeader(header))
			{
				final String token = UserManagerBearerToken.getTokenFromBearerAuthorizationHeader(header);

				this.staticSession = apiSessionRefCacheProvider.get().getOrCreateSessionRef(token);
			}
		}
	}


	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder("OAuthUser{");
		if (isAnonymous())
		{
			sb.append("anonymous=true");
		}
		else
		{
			try
			{
				final String username = getUsername();
				sb.append("username=").append(username);
			}
			catch (Throwable t)
			{
				sb.append("anonymous=false, username-fetch-error=").append(t.getMessage());
			}
		}
		sb.append('}');
		return sb.toString();
	}
}
