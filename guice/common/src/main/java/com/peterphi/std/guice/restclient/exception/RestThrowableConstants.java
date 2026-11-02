package com.peterphi.std.guice.restclient.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.util.HttpHeaderNames;

import java.util.Enumeration;

public class RestThrowableConstants
{
	public static final String HEADER_RICH_EXCEPTION = "X-Rich-Exception";


	/**
	 * Decides if an HTML response is acceptable to the client
	 *
	 * @param request
	 * @return
	 */
	public static boolean isHtmlAcceptable(HttpServletRequest request)
	{
		final Enumeration<String> en = request.getHeaders(HttpHeaderNames.ACCEPT);

		if (en != null)
		{
			while (en.hasMoreElements())
			{
				final String accept = en.nextElement();
				if (StringUtils.startsWithIgnoreCase(accept, "text/html"))
					return true;
			}
		}

		return false;
	}
}
