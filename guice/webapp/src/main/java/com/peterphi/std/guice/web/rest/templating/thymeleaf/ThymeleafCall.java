package com.peterphi.std.guice.web.rest.templating.thymeleaf;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.IContext;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import java.io.Writer;
import java.util.Map;

/**
 * Represents a ThymeLeaf Template that can be called. Constructed by {@link ThymeleafTemplater}
 */
public class ThymeleafCall implements TemplateCall
{
	private final TemplateEngine engine;
	private final AbstractContext context;
	private final String name;

	private final Timer calls;
	private final Meter failures;


	ThymeleafCall(TemplateEngine engine, AbstractContext context, String name, final Timer calls, final Meter failures)
	{
		this.engine = engine;
		this.context = context;
		this.name = name;
		this.calls = calls;
		this.failures = failures;
	}


	public TemplateEngine getEngine()
	{
		return engine;
	}


	public IContext getContext()
	{
		return context;
	}


	public String getName()
	{
		return name;
	}


	/**
	 * Sets a variable which is then exposed to the view layer
	 *
	 * @param name
	 * @param value
	 *
	 * @return
	 */
	public ThymeleafCall set(String name, Object value)
	{
		context.setVariable(name, value);

		return this;
	}


	@Override
	public ThymeleafCall setAll(final Map<String, Object> values)
	{
		for (Map.Entry<String, Object> entry : values.entrySet())
		{
			set(entry.getKey(), entry.getValue());
		}

		return this;
	}


	/**
	 * Render the template to a String
	 *
	 * @return
	 */
	public String process()
	{
		Timer.Context timer = calls.time();
		try
		{
			return engine.process(name, context);
		}
		catch (Throwable e)
		{
			failures.mark();

			throw e;
		}
		finally
		{
			timer.stop();
		}
	}


	/**
	 * Render the template to a Writer
	 */
	public void process(Writer writer)
	{
		Timer.Context timer = calls.time();
		try
		{
			engine.process(name, context, writer);
		}
		catch (Throwable e)
		{
			failures.mark();

			throw e;
		}
		finally
		{
			timer.stop();
		}
	}


	/**
	 * Render the template as the entity for a ResponseBuilder, returning the built Response
	 *
	 * @return the result of calling responseBuilder.
	 */
	public Response process(ResponseBuilder responseBuilder)
	{
		Timer.Context timer = calls.time();
		try
		{
			final String entity = process();

			responseBuilder.entity(entity);

			return responseBuilder.build();
		}
		catch (Throwable e)
		{
			failures.mark();

			throw e;
		}
		finally
		{
			timer.stop();
		}
	}
}
