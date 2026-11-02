package com.peterphi.std.guice.web.rest.templating.thymeleaf;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.cached.CacheManager;
import com.peterphi.std.guice.common.metrics.GuiceMetricNames;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.templating.Templater;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * Light abstraction over a ThymeLeaf TemplateEngine allowing cleaner construction of the current web context (when the template
 * engine is being used inside an http call).<br /> Exposes the following special variables:
 * <ul><li><code>currentUser</code> - of type {@link ThymeleafCurrentUserUtils}</li>
 * <ul><li><code>config</code> - of type {@link GuiceConfig}</li>
 * </ul>
 */
@Singleton
public class ThymeleafTemplater implements Templater
{
	private final TemplateEngine engine;
	private final GuiceConfig configuration;

	private Map<String, Object> data = new HashMap<String, Object>();

	private final Timer calls;
	private final Meter failures;

	// Dummy Cache impl used to clear the template cache
	private final ThymeleafCacheEmptyHook templateCacheClearer;


	@Inject
	public ThymeleafTemplater(final TemplateEngine engine,
	                          final GuiceConfig configuration,
	                          MetricRegistry metrics,
	                          Provider<CurrentUser> userProvider)
	{
		this(engine, configuration, metrics, userProvider, false);
	}


	/**
	 * @param engine              thymeleaf template engine to use
	 * @param configuration       full guice config
	 * @param metrics             metrics
	 * @param userProvider        user provider
	 * @param isTransientInstance if true, will skip registering the TemplateEngine with the CacheManager
	 */
	public ThymeleafTemplater(final TemplateEngine engine,
	                          final GuiceConfig configuration,
	                          MetricRegistry metrics,
	                          Provider<CurrentUser> userProvider,
	                          final boolean isTransientInstance)
	{
		if (engine.getTemplateResolvers().isEmpty())
		{
			throw new IllegalArgumentException("No Template Resolvers have been configured for thymeleaf (missing import?)");
		}

		this.engine = engine;
		this.configuration = configuration;

		this.calls = metrics.timer(GuiceMetricNames.THYMELEAF_CALL_TIMER);
		this.failures = metrics.meter(GuiceMetricNames.THYMELEAF_RENDER_EXCEPTION_METER);

		data.put("currentUser", new ThymeleafCurrentUserUtils(userProvider));

		if (!isTransientInstance)
		{
			// Set up a hook to allow the template cache to be cleared proactively
			this.templateCacheClearer = new ThymeleafCacheEmptyHook(() -> engine);
			CacheManager.register("ThymeleafTemplater", this.templateCacheClearer);
		}
		else
		{
			this.templateCacheClearer = null;
		}
	}


	public ThymeleafTemplater set(String key, Object value)
	{
		data.put(key, value);

		return this;
	}


	public ThymeleafCall template(final String name)
	{
		final AbstractContext ctx = makeContext();


		// Expose the service configuration
		ctx.setVariable("config", configuration);
		ctx.setVariable("consts", configuration);
		ctx.setVariables(data);

		return new ThymeleafCall(engine, ctx, name, calls, failures);
	}


	/**
	 * Build a new AbstractContext (exposing the HttpCallContext, where possible)
	 *
	 * @return
	 */
	protected AbstractContext makeContext()
	{
		final HttpCallContext http = HttpCallContext.peek();

		if (http != null)
		{
			return new WebContext(JakartaServletWebApplication
					                      .buildApplication(http.getServletContext())
					                      .buildExchange(http.getRequest(), http.getResponse()), http.getRequest().getLocale());
		}
		else
		{
			return new Context();
		}
	}
}
