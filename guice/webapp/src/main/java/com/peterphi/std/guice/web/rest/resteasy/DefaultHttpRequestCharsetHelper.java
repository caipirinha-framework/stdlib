package com.peterphi.std.guice.web.rest.resteasy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;

/**
 * Helper class that sets the default HTTP Request encoding if the client did not specify one
 */
class DefaultHttpRequestCharsetHelper
{
	private static final Logger log = LoggerFactory.getLogger(DefaultHttpRequestCharsetHelper.class);
	public static final String UTF8 = "UTF-8";


	public void applyDefaultCharset(final HttpServletRequest request)
	{
		// Set up request attributes for multipart/form-data requests
		{
			// By default, interpret mime parts as UTF-8 text/plain if no content type specified (instead of the default, text/plain; charset=ISO-8859-1)
			request.setAttribute(/*InputPart.DEFAULT_CONTENT_TYPE_PROPERTY*/
			                     "resteasy.provider.multipart.inputpart.defaultContentType", "text/plain; charset=UTF-8");

			// By default, interpret mime parts using the charset UTF-8 if no charset is specified (instead of the default, US-ASCII)
			request.setAttribute(/*InputPart.DEFAULT_CHARSET_PROPERTY*/
			                     "resteasy.provider.multipart.inputpart.defaultCharset", UTF8);
		}


		// Now, set the default encoding on the request if one is not already specified
		if (request.getCharacterEncoding() == null)
		{
			try
			{
				request.setCharacterEncoding(UTF8);
			}
			catch (UnsupportedEncodingException e)
			{
				log.warn("Unable to set character encoding to " + UTF8, e);
			}
		}
		else
		{
			if (log.isTraceEnabled())
				log.trace("Request already has character encoding: {}", request.getCharacterEncoding());
		}
	}
}
