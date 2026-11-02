package com.peterphi.std.guice.freemarker;

import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.net.URISyntaxException;


public class FreemarkerURLHelper
{
	private final URI restEndpoint;
	private final URI webappEndpoint;


	public FreemarkerURLHelper(URI restEndpoint, URI webappEndpoint)
	{
		this.restEndpoint = restEndpoint;
		this.webappEndpoint = webappEndpoint;
	}


	public UriBuilder context()
	{
		return UriBuilder.fromUri(webappEndpoint);
	}


	public UriBuilder rest()
	{
		return UriBuilder.fromUri(restEndpoint);
	}


	/**
	 * Returns a URL for a resource from the context path
	 *
	 * @param path
	 *
	 * @return
	 */
	public String context(String path)
	{
		return context().path(path).build().toString();
	}


	/**
	 * Returns a URL for a resource from the REST path (which may be different from the context path)<br />
	 *
	 * @param path
	 *
	 * @return
	 */
	public String rest(String path)
	{
		return rest().path(path).build().toString();
	}


	/**
	 * Returns a relative URL for a resource from the REST path, assuming it is relative to another path on the same server.
	 *
	 * @param path
	 *
	 * @return
	 */
	public String relativeRest(String path)
	{
		return relative(rest(path));
	}


	/**
	 * Similar to the <code>rest</code> method, however this performs concatenation using String operations rather than
	 * UriBuilder,
	 * allowing the representation of URLs which are technically invalid (e.g. using templates)
	 *
	 * @param path
	 *
	 * @return
	 */
	public String restConcat(String path)
	{
		return concat(rest().build().toString(), path);
	}


	/**
	 * Similar to the <code>relativeRest</code> method, however this performs concatenation using String operations rather than
	 * UriBuilder,
	 * allowing the representation of URLs which are technically invalid (e.g. using templates)
	 *
	 * @param path
	 *
	 * @return
	 */
	public String relativeRestConcat(String path) {
		return concat(relativeRest(""), path);
	}

	/**
	 * Return a UriBuilder for an absolute URI
	 *
	 * @param path
	 *
	 * @return
	 */
	public UriBuilder absolute(String path)
	{
		return UriBuilder.fromUri(path);
	}


	/**
	 * Return only the path for a given URL
	 *
	 * @param url
	 *
	 * @return
	 */
	public String relative(String url)
	{
		try
		{
			final URI uri = absolute(url).build();

			return new URI(null, null, uri.getPath(), uri.getQuery(), uri.getFragment()).toString();
		}
		catch (URISyntaxException e)
		{
			throw new IllegalArgumentException(e);
		}
	}


	protected String concat(String a, String b)
	{
		if (a.isEmpty() || b.isEmpty())
		{
			return a + b;
		}

		final boolean aEndSlash = !a.isEmpty() && a.charAt(a.length() - 1) == '/';
		final boolean bStartSlash = !b.isEmpty() && b.charAt(0) == '/';

		if (aEndSlash && bStartSlash)
		{
			return a + b.substring(1); // both have a /
		}
		else if (!aEndSlash && !bStartSlash)
		{
			return a + "/" + b; // neither side has a /
		}
		else
		{
			return a + b; // only one side has a /
		}
	}
}
