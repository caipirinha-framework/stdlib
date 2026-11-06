package com.peterphi.std.guice.web.rest.templating.thymeleaf;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.peterphi.std.guice.web.rest.templating.Templater;
import com.peterphi.std.thymeleaf.TestIdAttributeRemovalDialect;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Module to configure the ThymeLeaf templating engine
 */
public class ThymeleafModule extends AbstractModule
{
	@Override
	protected void configure()
	{
		bind(Templater.class).to(ThymeleafTemplater.class).in(Scopes.SINGLETON);
		bind(ITemplateResolver.class).toProvider(TemplateResolverProvider.class).in(Singleton.class);
	}


	@Provides
	@Singleton
	public TemplateEngine getTemplateEngine(ITemplateResolver resolver, Stage stage)
	{
		TemplateEngine engine = new TemplateEngine();
		engine.setTemplateResolver(resolver);
		if (stage == Stage.PRODUCTION)
			engine.addDialect(new TestIdAttributeRemovalDialect());

		return engine;
	}
}
