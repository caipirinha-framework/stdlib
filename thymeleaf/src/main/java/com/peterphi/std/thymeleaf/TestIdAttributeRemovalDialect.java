package com.peterphi.std.thymeleaf;

import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.dialect.IExecutionAttributeDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.engine.ITemplateHandler;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Map;

/**
 * Opts an engine into removing literal HTML data-testid attributes before template models are cached.
 * Attributes generated after parsing may remain. Runtime HTML is affected if Thymeleaf parses it too.
 */
public final class TestIdAttributeRemovalDialect implements IExecutionAttributeDialect
{
	private static final String ENABLED = TestIdAttributeRemovalDialect.class.getName();


	@Override
	public String getName()
	{
		return "Test ID attribute removal";
	}


	@Override
	public Map<String, Object> getExecutionAttributes()
	{
		return Map.of(ENABLED, true);
	}


	/**
	 * The single integration point in the upstream markup parser. No rendering context is needed here.
	 */
	public static ITemplateHandler wrap(final IEngineConfiguration configuration,
	                                    final TemplateMode templateMode,
	                                    final ITemplateHandler next)
	{
		if (templateMode != TemplateMode.HTML || !Boolean.TRUE.equals(configuration.getExecutionAttributes().get(ENABLED)))
			return next;

		return new RemovingHandler(configuration.getModelFactory(templateMode), next);
	}


	private static final class RemovingHandler extends AbstractTemplateHandler
	{
		private final IModelFactory factory;


		private RemovingHandler(final IModelFactory factory, final ITemplateHandler next)
		{
			super(next);
			this.factory = factory;
		}


		@Override
		public void handleOpenElement(final IOpenElementTag tag)
		{
			super.handleOpenElement(factory.removeAttribute(tag, "data-testid"));
		}


		@Override
		public void handleStandaloneElement(final IStandaloneElementTag tag)
		{
			super.handleStandaloneElement(factory.removeAttribute(tag, "data-testid"));
		}
	}
}
