package com.peterphi.std.guice.web.rest.templating.thymeleaf;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.common.cached.CacheManager;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.guice.web.rest.service.GuiceCoreServicesRegistry;
import com.peterphi.std.thymeleaf.TestIdAttributeRemovalDialect;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.lang.ref.WeakReference;
import java.net.URI;

/**
 * Acts as a ThymeleafTemplater for core guice services
 */
@Singleton
public class GuiceCoreTemplater
{
	@Inject
	GuiceConfig configuration;
	@Inject
	Stage stage;
	@Inject
	MetricRegistry metrics;
	@Inject
	@Named(GuiceProperties.REST_SERVICES_PREFIX)
	String coreRestPrefix;
	@Inject
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	URI coreRestEndpoint;
	@Inject
	GuiceCoreServicesRegistry services;

	@Inject
	Provider<CurrentUser> userProvider;

	/**
	 * We cache the TemplateEngine directly because Java may discard the ThymeleafTemplater wrapper but not the TemplateEngine
	 * (which would cost us a lot of time+memory)
	 */
	private WeakReference<TemplateEngine> engine = new WeakReference<>(null);
	private WeakReference<ThymeleafTemplater> templater = new WeakReference<>(null);

	/**
	 * Populated at guice creation
	 */
	private final ThymeleafCacheEmptyHook templateCacheClearer;


	public GuiceCoreTemplater()
	{
		// Set up a hook to allow the template cache to be cleared proactively
		this.templateCacheClearer = new ThymeleafCacheEmptyHook(this :: getEngineIfExists);
		CacheManager.register("GuiceCoreTemplates", this.templateCacheClearer);
	}


	public TemplateEngine getEngineIfExists()
	{
		if (this.engine == null)
			return null;
		else
			return this.engine.get();
	}


	public ThymeleafCall template(String template)
	{
		final ThymeleafTemplater templater = getOrCreateTemplater();

		return templater.template(template);
	}


	/**
	 * Retrieve or build a Thymeleaf templater
	 *
	 * @return
	 */
	private ThymeleafTemplater getOrCreateTemplater()
	{
		ThymeleafTemplater templater = this.templater.get();

		// Lazy-create a ThymeleafTemplater
		if (templater == null)
		{
			final TemplateEngine engine = getOrCreateEngine();

			templater = new ThymeleafTemplater(engine, configuration, metrics, userProvider, true);

			templater.set("coreRestPrefix", coreRestPrefix);
			templater.set("coreRestEndpoint", coreRestEndpoint.toString());
			templater.set("coreServices", services);
			templater.set("currentUser", new ThymeleafCurrentUserUtils(userProvider));

			this.templater = new WeakReference<>(templater);
		}

		return templater;
	}


	private TemplateEngine getOrCreateEngine()
	{
		TemplateEngine engine = this.engine.get();

		// Lazy-create the engine
		if (engine == null)
		{
			// Build and cache a new templater (previous instance must have been garbage collected)
			engine = createEngine();

			this.engine = new WeakReference<>(engine);
		}

		return engine;
	}


	private TemplateEngine createEngine()
	{
		ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();

		resolver.setTemplateMode("HTML");

		resolver.setCacheTTLMs(60 * 1000L);
		resolver.setCacheable(true);

		TemplateEngine engine = new TemplateEngine();
		engine.setTemplateResolver(resolver);
		if (stage == Stage.PRODUCTION)
			engine.addDialect(new TestIdAttributeRemovalDialect());

		return engine;
	}
}
