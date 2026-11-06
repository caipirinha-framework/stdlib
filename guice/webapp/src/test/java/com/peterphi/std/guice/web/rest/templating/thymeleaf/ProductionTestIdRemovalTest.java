package com.peterphi.std.guice.web.rest.templating.thymeleaf;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.Stage;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import org.junit.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class ProductionTestIdRemovalTest
{
	@Test
	public void applicationEngineRemovesTestIdsOnlyInProduction()
	{
		final String template = "<button data-testid='save'>Save</button>";
		for (Stage stage : Stage.values())
		{
			final var engine = new ThymeleafModule().getTemplateEngine(new StringTemplateResolver(), stage);
			assertEquals(stage.name(), stage == Stage.PRODUCTION ? "<button>Save</button>" : template,
			             engine.process(template, new Context()));
		}
	}


	@Test
	public void coreEngineRemovesTestIdsOnlyInProduction()
	{
		for (Stage stage : Stage.values())
		{
			final GuiceCoreTemplater templater = new GuiceCoreTemplater();
			templater.stage = stage;
			templater.configuration = new GuiceConfig();
			templater.metrics = new MetricRegistry();
			templater.coreRestEndpoint = URI.create("http://localhost/framework");
			templater.userProvider = () -> null;

			final String expected = stage == Stage.PRODUCTION ? "<button>Save</button>" :
			                        "<button data-testid=\"save\">Save</button>";
			assertEquals(stage.name(), expected, templater.template("templates/test-id-removal.html").process().trim());
		}
	}
}
