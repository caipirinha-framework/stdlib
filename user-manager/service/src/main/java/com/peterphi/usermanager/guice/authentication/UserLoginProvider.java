package com.peterphi.usermanager.guice.authentication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.peterphi.std.threading.Timeout;
import com.peterphi.usermanager.db.entity.IDPrefix;
import com.peterphi.usermanager.db.entity.UserEntity;
import com.peterphi.usermanager.guice.async.AsynchronousActionService;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.util.BasicAuthHelper;
import org.jboss.resteasy.util.HttpHeaderNames;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class UserLoginProvider implements Provider<UserLogin>
{
	public static final String LOGIN_SESSION_ATTRIBUTE = UserLoginModule.LOGIN_SESSION_ATTRIBUTE;

	/**
	 * The maximum amount of time a login transaction is permitted to run before being abandoned
	 */
	public static Timeout LOGIN_TIMEOUT = new Timeout(10, TimeUnit.SECONDS);

	private final Provider<HttpServletRequest> requestProvider;
	private final Provider<HttpSession> sessionProvider;
	private final Provider<AsynchronousActionService> asynchService;
	private final Provider<UserAuthenticationService> authService;


	@Inject
	public UserLoginProvider(final Provider<UserAuthenticationService> authService,
	                         final Provider<HttpServletRequest> requestProvider,
	                         final Provider<HttpSession> sessionProvider,
	                         final Provider<AsynchronousActionService> asynchService)
	{
		this.authService = authService;
		this.requestProvider = requestProvider;
		this.sessionProvider = sessionProvider;
		this.asynchService = asynchService;
	}


	@Override
	public UserLogin get()
	{
		final HttpServletRequest request = requestProvider.get();
		final HttpSession session = sessionProvider.get();

		// this is a new session, we should make an attempt to reconnect to a previous session;
		// this is particularly important in dev where containers are restarted & sessions lost frequently
		final UserLogin login;

		if (session.getAttribute(LOGIN_SESSION_ATTRIBUTE) != null && session.getAttribute(LOGIN_SESSION_ATTRIBUTE) instanceof UserLogin)
		{
			login = (UserLogin) session.getAttribute(LOGIN_SESSION_ATTRIBUTE);
		}
		else
		{
			login = new UserLoginImpl(null);

			session.setAttribute(LOGIN_SESSION_ATTRIBUTE, login);
		}

		// Try cookie reconnect
		if (login.isAnonymous())
		{
			final Cookie[] cookies = request.getCookies();

			if (cookies != null)
				tryRelogin(login, authService.get(), cookies);
		}

		// Try basic auth
		if (login.isAnonymous())
			tryBasicAuthLogin(login, authService.get(), request);

		return login;
	}


	private UserLogin tryRelogin(final UserLogin login, UserAuthenticationService auth, Cookie[] cookies)
	{
		for (Cookie cookie : cookies)
		{
			if (UserLogin.SESSION_RECONNECT_COOKIE.equals(cookie.getName()))
			{
				final String key = cookie.getValue();

				final Future<UserEntity> future = asynchService.get().submit(() -> trySessionReconnectLogin(auth, key));

				try
				{
					UserEntity user = LOGIN_TIMEOUT.start().resolveFuture(future, true);

					login.reload(user);
				}
				catch (Exception e)
				{
					throw new RuntimeException("Error attempting asynchronous session reconnect auth login: " + e.getMessage(),
					                           e);
				}
			}
		}

		return null;
	}


	/**
	 * Support proactive HTTP BASIC authentication
	 *
	 * @param authService
	 * 		the user authentication service
	 * @param request
	 * 		the HTTP request
	 *
	 * @return a UserLogin for the appropriate user if valid credentials were presented, otherwise null
	 */
	private UserLogin tryBasicAuthLogin(UserLogin login, UserAuthenticationService authService, HttpServletRequest request)
	{
		final String header = request.getHeader(HttpHeaderNames.AUTHORIZATION);

		if (header != null)
		{
			// N.B. returns null if not BASIC auth (e.g. Bearer auth)
			final String[] credentials = BasicAuthHelper.parseHeader(header);

			if (credentials != null)
			{
				final String username = credentials[0];
				final String password = credentials[1];

				// Special-case OAuth2 Client providing their credentials with BASIC, allow services to handle this directly
				if (StringUtils.startsWith(username, IDPrefix.OAUTH_SERVICE) &&
				    StringUtils.startsWith(password, IDPrefix.OAUTH_SERVICE_SECRET))
					return null;

				final Future<UserEntity> future = asynchService.get().submit(() -> tryLogin(authService,
				                                                                            username,
				                                                                            password,
				                                                                            true));

				try
				{
					UserEntity user = LOGIN_TIMEOUT.start().resolveFuture(future, true);

					login.reload(user);
				}
				catch (Exception e)
				{
					throw new RuntimeException("Error attempting asynchronous BASIC auth login: " + e.getMessage(), e);
				}
			}
		}

		// No authorisation (or unsupported authorisation type)
		return null;
	}


	UserEntity tryLogin(final UserAuthenticationService auth, String username, String password, final boolean basicAuth)
	{
		return auth.authenticate(username, password, basicAuth);
	}


	UserEntity trySessionReconnectLogin(final UserAuthenticationService auth, final String key)
	{
		return auth.authenticate(key);
	}


	/**
	 * Makes sure there is a UserLogin on a session; designed to be called by code unrelated to the regular login logic (the
	 * TemplateExceptionRenderer) to make sure there's at least an anonymous login session set up for that templater
	 *
	 * @param session
	 *
	 * @return
	 */
	public UserLogin ensureLoginOnSession(HttpSession session)
	{
		if (session.getAttribute(LOGIN_SESSION_ATTRIBUTE) == null)
			// Fall back on the anonymous user
			// Save in the "login" session attribute
			session.setAttribute(LOGIN_SESSION_ATTRIBUTE, new UserLoginImpl(null));

		return (UserLogin) session.getAttribute(LOGIN_SESSION_ATTRIBUTE);
	}
}
