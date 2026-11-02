package com.peterphi.std.guice.freemarker;

import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class FreemarkerCall implements TemplateCall
{
	private final Map<String, Object> data = new HashMap<String, Object>();
	private final Template template;

	public FreemarkerCall(final Template template)
	{
		this.template = template;
	}

	public Map<String, Object> getData()
	{
		return data;
	}

	@Override
	public String getName()
	{
		return template.getName();
	}

	/**
	 * Sets a variable which is then exposed to the view layer
	 *
	 * @param name
	 * @param value
	 *
	 * @return
	 */
	@Override
	public FreemarkerCall set(String name, Object value)
	{
		data.put(name, value);

		return this;
	}

	@Override
	public FreemarkerCall setAll(final Map<String, Object> data)
	{
		this.data.putAll(data);

		return this;
	}

	/**
	 * Render the template to a String
	 *
	 * @return
	 */
	@Override
	public String process()
	{
		final StringWriter sw = new StringWriter();

		process(sw);

		return sw.toString();
	}

	/**
	 * Render the template to a Writer
	 */
	@Override
	public void process(Writer writer)
	{
		try
		{
			template.process(data, writer);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error writing template to writer", e);
		}
		catch (TemplateException e)
		{
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	/**
	 * Render the template as the entity for a ResponseBuilder, returning the built Response
	 *
	 * @return the result of calling responseBuilder.
	 */
	@Override
	public Response process(Response.ResponseBuilder responseBuilder)
	{
		final String entity = process();

		responseBuilder.entity(entity);

		return responseBuilder.build();
	}
}
