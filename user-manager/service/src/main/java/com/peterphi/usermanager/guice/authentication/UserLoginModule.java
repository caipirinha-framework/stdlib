package com.peterphi.usermanager.guice.authentication;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.web.rest.scoping.SessionScoped;
import com.peterphi.usermanager.guice.async.AsynchronousActionService;
import com.peterphi.usermanager.guice.authentication.db.InternalUserAuthenticationServiceImpl;
import com.peterphi.usermanager.guice.authentication.ldap.LocalAndLDAPAuthenticationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Handles creating the (session scoped) UserLogin object
 */
public class UserLoginModule extends AbstractModule
{
	@Doc("Authentication backend, defaults to internal, can be internal or ldap (if ldap, internal is checked first)")
	public static final String AUTHENTICATION_BACKEND = "authentication-backend";

	public static final String LOGIN_SESSION_ATTRIBUTE = "login";
	public static final String JAXRS_SERVER_WEBAUTH_PROVIDER = "user-manager";


	public UserLoginModule()
	{
	}


	@Override
	protected void configure()
	{
		bind(Key.get(CurrentUser.class, Names.named(JAXRS_SERVER_WEBAUTH_PROVIDER))).toProvider(new UserLoginProvider(getProvider(
				UserAuthenticationService.class), getProvider(
				                                                                                                              HttpServletRequest.class),
		                                                                                                              getProvider(
				                                                                                                              HttpSession.class),
		                                                                                                              getProvider(
				                                                                                                              AsynchronousActionService.class)));
	}


	@Provides
	@Singleton
	public UserAuthenticationService getUserAuthenticationService(@Named(AUTHENTICATION_BACKEND) String backend,
	                                                              Provider<InternalUserAuthenticationServiceImpl> internalProvider,
	                                                              Provider<LocalAndLDAPAuthenticationService> ldapProvider)
	{
		if (backend.equalsIgnoreCase("internal"))
			return internalProvider.get();
		else if (backend.equalsIgnoreCase("ldap"))
			return ldapProvider.get();
		else
			throw new IllegalArgumentException("Unknown authentication backend: " + backend);
	}


	/**
	 * Auto-cast the user manager's CurrentUser to a UserLogin
	 *
	 * @param user
	 *
	 * @return
	 */
	@Provides
	@SessionScoped
	public UserLogin getLogin(@Named(JAXRS_SERVER_WEBAUTH_PROVIDER) CurrentUser user)
	{
		return (UserLogin) user;
	}
}
