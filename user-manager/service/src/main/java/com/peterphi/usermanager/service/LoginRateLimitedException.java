package com.peterphi.usermanager.service;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Thrown when a login attempt is refused because the account has exceeded the permitted number of
 * failed login attempts inside the rolling window.
 *
 * <p>Extends {@link WebApplicationException} and carries a pre-built 429 Too Many Requests response, so
 * that when this exception is thrown from a JAX-RS resource method without being caught, the framework
 * automatically returns a 429 to the caller rather than a generic 500. Callers that want to render a
 * custom body (e.g. the interactive login UI) can still catch this exception explicitly before it
 * reaches the JAX-RS layer.</p>
 */
public class LoginRateLimitedException extends WebApplicationException
{
	public LoginRateLimitedException(final String message)
	{
		super(message, Response.status(429).type(MediaType.TEXT_PLAIN).entity(message).build());
	}
}
