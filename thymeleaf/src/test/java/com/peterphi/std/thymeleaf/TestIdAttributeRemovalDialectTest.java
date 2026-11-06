package com.peterphi.std.thymeleaf;

import org.junit.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TestIdAttributeRemovalDialectTest
{
	@Test
	public void removesLiteralAttributesFromHtmlTags()
	{
		final TemplateEngine engine = engine(true, TemplateMode.HTML);
		assertEquals("<button id='save' data-other='keep'>Save</button><INPUT/><br>", engine.process(
				"<button data-testid='save' id='save' data-other='keep'>Save</button>" +
				"<INPUT DATA-TESTID=field/><br data-testid>", new Context()));
	}


	@Test
	public void removesAttributesBeforeCachingIncludingFragmentsAndLoops()
	{
		final TemplateEngine engine = engine(true, TemplateMode.HTML);
		final String template = "<b th:fragment='f' data-testid='fragment'>x</b>" +
		                        "<div th:insert='~{::f}'></div>" +
		                        "<input th:each='x : ${ {1,2} }' data-testid='row'/>";
		final String expected = "<b>x</b><div><b>x</b></div><input/><input/>";

		assertEquals(expected, engine.process(template, new Context()));

		final var cache = engine.getConfiguration().getCacheManager().getTemplateCache();
		assertFalse(cache.keySet().isEmpty());
		for (var key : cache.keySet())
		{
			final var model = cache.get(key);
			for (int i = 0; i < model.size(); i++)
			{
				if (model.get(i) instanceof IProcessableElementTag tag)
					assertFalse("Literal attribute retained in cached template", tag.hasAttribute("data-testid"));
			}
		}

		// Render the cached template through the Writer API too.
		final StringWriter writer = new StringWriter();
		engine.process(template, new Context(), writer);
		assertEquals(expected, writer.toString());
	}


	@Test
	public void removesAttributesWhenTemplateCachingIsDisabled()
	{
		final StringTemplateResolver resolver = new StringTemplateResolver();
		resolver.setCacheable(false);
		final TemplateEngine engine = new TemplateEngine();
		engine.setTemplateResolver(resolver);
		engine.addDialect(new TestIdAttributeRemovalDialect());
		assertEquals("<button>Save</button>", engine.process("<button data-testid='save'>Save</button>", new Context()));
		assertEquals(0, engine.getConfiguration().getCacheManager().getTemplateCache().keySet().size());
	}


	@Test
	public void preservesAttributesGeneratedAfterParsing()
	{
		final Context context = new Context();
		context.setVariable("testId", "generated");
		context.setVariable("raw", "<b data-testid='raw'>raw</b>");
		assertEquals("<div data-testid=\"generated\"></div><div><b data-testid='raw'>raw</b></div>",
		             engine(true, TemplateMode.HTML).process(
			             "<div data-testid='literal' th:attr='data-testid=${testId}'></div>" +
			             "<div th:utext='${raw}'></div>", context));
	}


	@Test
	public void preservesAttributesWhenNotEnabled()
	{
		final String template = "<div data-testid='keep'></div><input data-testid='field'/>";
		assertEquals(template, engine(false, TemplateMode.HTML).process(template, new Context()));
	}


	@Test
	public void leavesXmlTemplatesUnchanged()
	{
		final String template = "<root data-testid='keep'><child DATA-TESTID='keep'/></root>";
		assertEquals(template, engine(true, TemplateMode.XML).process(template, new Context()));
	}


	private static TemplateEngine engine(final boolean removeTestIds, final TemplateMode mode)
	{
		final StringTemplateResolver resolver = new StringTemplateResolver();
		resolver.setTemplateMode(mode);
		resolver.setCacheable(true);
		final TemplateEngine engine = new TemplateEngine();
		engine.setTemplateResolver(resolver);
		if (removeTestIds)
			engine.addDialect(new TestIdAttributeRemovalDialect());
		return engine;
	}
}
