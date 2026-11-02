package com.peterphi.std.guice.web.rest.auth.userprovider;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.name.Names;
import com.peterphi.std.guice.apploader.GuiceConstants;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.web.rest.scoping.SessionScoped;
import com.peterphi.std.util.tracing.TracingConstants;
import org.slf4j.MDC;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public class WebappAuthenticationModule extends AbstractModule
{
	private final List<String> providerNames;

	private final String jwtHeader;
	private final String jwtCookie;
	private final String jwtSecret;
	private final String jwtIssuer;
	private final String jwtAudience;
	private final boolean jwtRequireSecure;


	public WebappAuthenticationModule(final MetricRegistry metrics, final List<String> providerNames, GuiceConfig config)
	{
		this.providerNames = providerNames;

		this.jwtSecret = config.get(GuiceProperties.AUTH_JWT_SECRET, null);
		this.jwtHeader = config.get(GuiceProperties.AUTH_JWT_HTTP_HEADER, "X-JWT");
		this.jwtCookie = config.get(GuiceProperties.AUTH_JWT_HTTP_COOKIE, "X-JWT");
		this.jwtIssuer = config.get(GuiceProperties.AUTH_JWT_ISSUER, null);
		this.jwtAudience = config.get(GuiceProperties.AUTH_JWT_AUDIENCE, null);
		this.jwtRequireSecure = config.getBoolean(GuiceProperties.AUTH_JWT_AUDIENCE, false);
	}


	@Override
	protected void configure()
	{
		// Bind a @Named("servlet") CurrentUser provider that people may use
		if (providerNames.contains(GuiceConstants.JAXRS_SERVER_WEBAUTH_SERVLET_PROVIDER))
			bind(Key.get(CurrentUser.class,
			             Names.named(GuiceConstants.JAXRS_SERVER_WEBAUTH_SERVLET_PROVIDER))).toProvider(new HttpServletUserProvider());

		// Bind a @Named("jwt") CurrentUser provider that people may use
		if (providerNames.contains(GuiceConstants.JAXRS_SERVER_WEBAUTH_JWT_PROVIDER))
			bind(Key.get(CurrentUser.class,
			             Names.named(GuiceConstants.JAXRS_SERVER_WEBAUTH_JWT_PROVIDER))).toProvider(new JWTUserProvider(jwtHeader,
			                                                                                                            jwtCookie,
			                                                                                                            jwtSecret,
			                                                                                                            jwtIssuer,
			                                                                                                            jwtAudience,
			                                                                                                            jwtRequireSecure));
	}


	@Provides
	@SessionScoped
	public CurrentUser getCurrentUser(Injector injector, HttpServletRequest request)
	{

		for (String providerName : providerNames)
		{
			final Provider<CurrentUser> provider = injector.getProvider(Key.get(CurrentUser.class, Names.named(providerName)));

			final CurrentUser user = provider.get();

			if (user != null)
			{
				// Store the user info for logging
				if (user.getUsername() != null)
				{
					MDC.put(TracingConstants.MDC_USER_ID, user.getUsername());
				}

				return user;
			}
		}

		throw new IllegalArgumentException("No provider could determine a user for HTTP request!");
	}
}
