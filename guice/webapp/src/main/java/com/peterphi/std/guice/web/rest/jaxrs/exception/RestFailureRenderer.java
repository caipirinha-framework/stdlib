package com.peterphi.std.guice.web.rest.jaxrs.exception;

import com.peterphi.std.guice.restclient.jaxb.RestFailure;

import jakarta.ws.rs.core.Response;

public interface RestFailureRenderer
{
	/**
	 * Allows user-defined code to present the exception in a more user-friendly manner
	 *
	 * @param failure
	 * @return a non-null Response if desired - otherwise (or if this method throws) the default exeption renderer will be used
	 */
	Response render(RestFailure failure);

	/**
	 * Method returns true if the error is not useful to log to the application logs, and instead will be presented ephemerally to
	 * the user
	 *
	 * @param failure
	 * @return
	 */
	default boolean shouldSuppressLog(RestFailure failure)
	{
		return false;
	}
}
