package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads path lists in either the comma-separated field form or the array form emitted by WebQuery's getters.
 * An empty array explicitly disables fetching/expansion; null retains the query's default behaviour.
 */
public class WQPathListDeserialiser extends JsonDeserializer<String>
{
	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) throws IOException
	{
		List<String> paths = new ArrayList<>();
		if (parser.hasToken(JsonToken.VALUE_STRING))
		{
			addPaths(parser.getText(), paths, context);
			return String.join(",", paths);
		}
		if (!parser.hasToken(JsonToken.START_ARRAY))
			return (String) context.handleUnexpectedToken(String.class, parser);

		while (parser.nextToken() != JsonToken.END_ARRAY)
		{
			if (!parser.hasToken(JsonToken.VALUE_STRING))
				return (String) context.handleUnexpectedToken(String.class, parser);
			addPaths(parser.getText(), paths, context);
		}
		return paths.isEmpty() ? "none" : String.join(",", paths);
	}

	private void addPaths(String value, List<String> paths, DeserializationContext context) throws IOException
	{
		for (String element : value.split(",", -1))
		{
			String path = element.strip();
			if (path.isEmpty())
				throw context.weirdStringException(value, String.class, "Path lists must not contain blank elements");
			paths.add(path);
		}
	}
}
