package com.peterphi.std.guice.web.rest.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * A text/plain WebApplicationException<br />
 * N.B. clients will not be able to catch this exception type client-side!
 */
public class TextWebException extends WebApplicationException
{
	private static final long serialVersionUID = 1L;

	public TextWebException(String detail)
	{
		this(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), detail, null);
	}

	public TextWebException(int status, String detail)
	{
		this(status, detail, null);
	}

	public TextWebException(String detail, Throwable cause)
	{
		this(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), detail, cause);
	}

	public TextWebException(int status, String detail, Throwable cause)
	{
		super(cause, buildResponse(status, detail));
	}

	public String getMessage()
	{
		return getResponse().getEntity().toString();
	}

	private static Response buildResponse(int status, String message)
	{
		return Response.status(status)
		               .type(MediaType.TEXT_PLAIN_TYPE)
		               .header("X-Java-Exception-Impl",
		                       TextWebException.class.getName())
		               .entity(message)
		               .build();
	}
}
