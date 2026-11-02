package com.peterphi.std.guice.restclient.exception;

import com.peterphi.std.guice.restclient.jaxb.RestFailure;

import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ResponseProcessingException;

public class RestException extends ResponseProcessingException
{
	private static final long serialVersionUID = 1L;

	private static final int INTERNAL_SERVER_ERROR = 500;

	private int httpCode;
	private long errorCode = 0;
	private RestFailure failure;

	/**
	 * Whether this exception was caused by a remote service throwing an exception. If false then this must have been thrown
	 * locally
	 */
	private boolean causedByRemote = false;

	/**
	 * Only set if this RestException is caused by a remote service throwing an exception.
	 */
	private ClientResponseContext responseContext;


	public RestException(String msg, Throwable cause)
	{
		this(INTERNAL_SERVER_ERROR, msg, cause);
	}


	public RestException(int httpCode, String msg)
	{
		this(httpCode, msg, null);
	}


	public RestException(int httpCode, String msg, Throwable cause)
	{
		super(null, msg, cause);

		setHttpCode(httpCode);
	}


	void setCausedByRemote(boolean value)
	{
		this.causedByRemote = value;
	}


	void setFailure(RestFailure failure)
	{
		this.failure = failure;
	}


	protected void setErrorCode(long code)
	{
		this.errorCode = code;
	}


	protected void setHttpCode(int httpCode)
	{
		this.httpCode = httpCode;
	}


	public long getErrorCode()
	{
		return errorCode;
	}


	public int getHttpCode()
	{
		return httpCode;
	}


	public RestFailure getFailure()
	{
		return this.failure;
	}


	public boolean isCausedByRemote()
	{
		return causedByRemote;
	}


	public void setResponseContext(final ClientResponseContext responseContext)
	{
		this.responseContext = responseContext;
	}


	public ClientResponseContext getResponseContext()
	{
		return responseContext;
	}
}
