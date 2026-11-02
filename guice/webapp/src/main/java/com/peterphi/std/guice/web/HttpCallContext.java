package com.peterphi.std.guice.web;

import com.peterphi.std.types.SimpleId;
import com.peterphi.std.util.tracing.TracingConstants;
import org.apache.commons.lang.StringUtils;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Represents the current Http Call being processed by this Thread
 */
public class HttpCallContext
{
	/**
	 * The number of bytes to use when generating a random trace id
	 */
	private static final int TRACE_ID_LENGTH = 10;
	private static ThreadLocal<HttpCallContext> contexts = new ThreadLocal<>();


	/**
	 * Retrieve the HttpCallContext associated with this Thread
	 *
	 * @return
	 *
	 * @throws IllegalStateException
	 * 		if not inside an http call
	 */
	public static HttpCallContext get() throws IllegalStateException
	{
		final HttpCallContext ctx = peek();

		if (ctx != null)
			return ctx;
		else
			throw new IllegalStateException("Not in an HttpCallContext!");
	}


	/**
	 * Retrieve the HttpCallContext associated with this Thread (or null if none is associated)
	 *
	 * @return
	 *
	 * @throws IllegalStateException
	 * 		if not inside an http call
	 */
	public static HttpCallContext peek()
	{
		return contexts.get();
	}


	public static void clear()
	{
		contexts.remove();
	}


	/**
	 * Creates and associates an HttpCallContext with the current Thread
	 *
	 * @param request
	 * @param response
	 *
	 * @return
	 */
	public static HttpCallContext set(HttpServletRequest request, HttpServletResponse response, ServletContext servletContext)
	{
		final HttpCallContext ctx = new HttpCallContext(generateTraceId(request), request, response, servletContext);

		contexts.set(ctx);

		return ctx;
	}


	/**
	 * Generates a trace id (or uses the existing trace id passed in from the requesting service)
	 *
	 * @param request
	 *
	 * @return
	 */
	private static String generateTraceId(final HttpServletRequest request)
	{
		final String existing = request.getHeader(TracingConstants.HTTP_HEADER_CORRELATION_ID);

		if (existing == null)
			return SimpleId.alphanumeric(TRACE_ID_LENGTH);
		else
			return existing;
	}

	// Instance fields and methods
	/**
	 * The id associated with this HttpCallContext for the purposes of logging
	 */
	private final String logId;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final ServletContext servletContext;


	public HttpCallContext(String logId, HttpServletRequest request, HttpServletResponse response, ServletContext servletContext)
	{
		this.logId = logId;
		this.request = request;
		this.response = response;
		this.servletContext = servletContext;
	}


	public String getLogId()
	{
		return logId;
	}


	public HttpServletRequest getRequest()
	{
		return request;
	}


	public HttpServletResponse getResponse()
	{
		return response;
	}


	public ServletContext getServletContext()
	{
		return servletContext;
	}


	/**
	 * Returns a string representation of the request (e.g. "GET /webapp/rest/resource?a=b")
	 *
	 * @return
	 */
	public String getRequestInfo()
	{
		final String method = request.getMethod();
		final String uri = request.getRequestURI();
		final String qs = request.getQueryString();

		if (StringUtils.isEmpty(qs))
			return method + " " + uri;
		else
			return method + " " + uri + "?" + qs;
	}


	public boolean isVerbose()
	{
		final String header = request.getHeader(TracingConstants.HTTP_HEADER_TRACE_VERBOSE);

		// N.B. In the future may need this value to match a config-defined token
		return (header != null && !header.isEmpty());
	}
}
