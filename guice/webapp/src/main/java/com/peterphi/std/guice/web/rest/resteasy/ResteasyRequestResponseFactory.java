package com.peterphi.std.guice.web.rest.resteasy;

import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.servlet.HttpRequestFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpResponseFactory;
import org.jboss.resteasy.plugins.server.servlet.HttpServletInputMessage;
import org.jboss.resteasy.plugins.server.servlet.HttpServletResponseWrapper;
import org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Factory that converts HttpServletRequests and HttpServletResponses to HttpRequests and HttpResponses
 */
class ResteasyRequestResponseFactory implements HttpRequestFactory, HttpResponseFactory
{
	private final ServletContainerDispatcher dispatcher;


	public ResteasyRequestResponseFactory(ServletContainerDispatcher dispatcher)
	{
		this.dispatcher = dispatcher;
	}


	@Override
	public HttpRequest createResteasyHttpRequest(final String httpMethod,
	                                             final HttpServletRequest request,
	                                             final ResteasyHttpHeaders headers,
	                                             final ResteasyUriInfo uriInfo,
	                                             final HttpResponse theResponse,
	                                             final HttpServletResponse response)
	{
		return new HttpServletInputMessage(request,
		                                   response,
		                                   request.getServletContext(),
		                                   theResponse,
		                                   headers,
		                                   uriInfo,
		                                   httpMethod.toUpperCase(),
		                                   (SynchronousDispatcher) dispatcher.getDispatcher());
	}


	@Override
	public HttpResponse createResteasyHttpResponse(final HttpServletResponse response, final HttpServletRequest request)
	{
		return new HttpServletResponseWrapper(response, request, dispatcher.getDispatcher().getProviderFactory());
	}
}
