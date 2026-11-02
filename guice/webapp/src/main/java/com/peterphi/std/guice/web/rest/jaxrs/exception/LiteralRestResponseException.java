package com.peterphi.std.guice.web.rest.jaxrs.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * An exception that contains the exact Response that should be sent back to the client
 */
public class LiteralRestResponseException extends WebApplicationException
{
	public LiteralRestResponseException(Response response)
	{
		super(response);
	}

	public LiteralRestResponseException(Response response, Throwable cause)
	{
		super(cause, response);
	}
}
