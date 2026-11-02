package com.peterphi.std.guice.web.rest.templating;

import jakarta.ws.rs.core.Response;
import java.io.Writer;
import java.util.Map;

public interface TemplateCall
{
	/**
	 * Return this template name
	 *
	 * @return
	 */
	public String getName();

	/**
	 * Sets a variable which is then exposed to the view layer
	 *
	 * @param name
	 * @param value
	 *
	 * @return
	 */
	public TemplateCall set(String name, Object value);

	/**
	 * Sets a number of variables which are then exposed to the view layer
	 *
	 * @param values
	 *
	 * @return
	 */
	public TemplateCall setAll(Map<String, Object> values);

	/**
	 * Render the template to a String
	 *
	 * @return
	 */
	public String process();

	/**
	 * Render the template to a Writer
	 */
	public void process(Writer writer);

	/**
	 * Render the template as the entity for a ResponseBuilder, returning the built Response
	 *
	 * @return the result of calling responseBuilder.
	 */
	public Response process(Response.ResponseBuilder responseBuilder);
}
