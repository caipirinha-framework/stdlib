package com.peterphi.std.guice.web.rest.auth.userprovider;

import com.google.common.base.MoreObjects;
import com.peterphi.std.guice.apploader.GuiceConstants;
import com.peterphi.std.guice.common.auth.iface.AccessRefuser;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.auth.oauth2.CredentialsRestException;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * An implementation of {@link com.peterphi.std.guice.common.auth.iface.CurrentUser} using the user attached to the context's
 * {@link jakarta.servlet.http.HttpServletRequest}
 */
class HttpCallUser implements CurrentUser
{
	@Override
	public String getAuthType()
	{
		return GuiceConstants.JAXRS_SERVER_WEBAUTH_SERVLET_PROVIDER;
	}


	@Override
	public boolean isAnonymous()
	{
		HttpServletRequest request = HttpCallContext.get().getRequest();

		return request.getUserPrincipal() == null;
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
		HttpServletRequest request = HttpCallContext.get().getRequest();

		Principal principal = request.getUserPrincipal();

		if (principal != null)
			return principal.getName();
		else
			return null;
	}


	@Override
	public String getUsername()
	{
		return getName();
	}


	@Override
	public boolean hasRole(final String role)
	{
		HttpServletRequest request = HttpCallContext.get().getRequest();

		if (StringUtils.equals(CurrentUser.ROLE_AUTHENTICATED, role))
		{
			return request.getUserPrincipal() != null;
		}
		else
		{
			return request.isUserInRole(role);
		}
	}


	@Override
	public DateTime getExpires()
	{
		return null;
	}


	@Override
	public Collection<String> getRoles()
	{
		return Collections.emptySet(); // There is no API for this, so we cannot know it
	}


	@Override
	public Map<String, Object> getClaims()
	{
		return Collections.emptyMap();
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
				                                    "Access denied for your Servlet user by rule: " +
				                                    ((constraint != null) ?
				                                     constraint.comment() :
				                                     "(default)" + ". Required one of roles: " + scope.getRoles(constraint)));
		};
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("principal", getName()).toString();
	}
}
