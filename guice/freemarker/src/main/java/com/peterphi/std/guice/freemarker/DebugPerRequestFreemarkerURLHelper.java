package com.peterphi.std.guice.freemarker;

import com.peterphi.std.guice.web.HttpCallContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;

/**
 * A special implementation of FreemarkerURLHelper that builds webapp/rest relative URLs by inspecting the requested path.<br />
 * Should only be used when debugging!
 */
public class DebugPerRequestFreemarkerURLHelper extends FreemarkerURLHelper
{
	private final String restPrefix;


	public DebugPerRequestFreemarkerURLHelper(String restPrefix)
	{
		super(null, null);

		this.restPrefix = restPrefix;
	}


	@Override
	public UriBuilder rest()
	{
		final HttpServletRequest req = HttpCallContext.get().getRequest();
		final URI requestURL = URI.create(req.getRequestURL().toString());
		final String path = concat(req.getContextPath(), restPrefix);

		return UriBuilder.fromUri(requestURL).replacePath(path);
	}


	@Override
	public UriBuilder context()
	{
		final HttpServletRequest req = HttpCallContext.get().getRequest();
		final URI requestURL = URI.create(req.getRequestURL().toString());

		return UriBuilder.fromUri(requestURL).replacePath(req.getContextPath());
	}
}
