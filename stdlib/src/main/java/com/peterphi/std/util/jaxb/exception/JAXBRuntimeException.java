package com.peterphi.std.util.jaxb.exception;

import jakarta.xml.bind.JAXBException;

/**
 * A RuntimeException version of a JAXBException
 */
public class JAXBRuntimeException extends RuntimeException
{
	private static final long serialVersionUID = 1L;

	private JAXBException source;


	public JAXBRuntimeException(final String operation, JAXBException e)
	{
		this(computeMessage(operation, e), e.getCause() != null ? e.getCause() : e);

		this.source = source;
	}


	private static String computeMessage(final String operation, final JAXBException e)
	{
		final String message;

		if (e.getMessage() != null)
			message = e.getMessage();
		else if (e.getErrorCode() != null)
			message = e.getErrorCode();
		else if (e.getCause() != null && e.getCause().getMessage() != null)
			message = e.getCause().getMessage();
		else if (e.getLinkedException() != null && e.getLinkedException().getMessage() != null)
			message = e.getLinkedException().getMessage();
		else
			message = null;

		if (message != null)
			return operation + " failed: " + message;
		else
			return operation + " failed";
	}


	public JAXBRuntimeException(String msg, Throwable t)
	{
		super(msg, t);
	}


	public JAXBRuntimeException(Throwable t)
	{
		super(t);
	}


	public JAXBRuntimeException(String msg)
	{
		super(msg);
	}


	public JAXBException getSource()
	{
		return this.source;
	}
}
