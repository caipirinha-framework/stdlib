package com.peterphi.std.guice.common.retry.module;

import com.peterphi.std.guice.common.retry.retry.RetryDecision;
import com.peterphi.std.guice.common.retry.retry.Retryable;
import com.peterphi.std.guice.restclient.exception.RestException;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;

final class InvocationRetryable implements Retryable<Object>
{
	private static final Logger log = LoggerFactory.getLogger(InvocationRetryable.class);

	final MethodInvocation invocation;
	final Class<? extends Throwable>[] alwaysRetry;
	final Class<? extends Throwable>[] noRetry;
	final Class<? extends Throwable>[] noRetryCore;
	final int[] noHttpCodes;


	public InvocationRetryable(MethodInvocation invocation,
	                           final Class<? extends Throwable>[] alwaysRetry,
	                           final Class<? extends Throwable>[] noRetry,
	                           final Class<? extends Throwable>[] noRetryCore,
	                           final int[] noHttpCodes)
	{
		this.invocation = invocation;
		this.alwaysRetry = alwaysRetry;
		this.noRetry = noRetry;
		this.noRetryCore = noRetryCore;
		this.noHttpCodes = noHttpCodes;
	}


	@Override
	public Object attempt(final int attempt) throws Exception
	{
		try
		{
			if (log.isTraceEnabled())
				log.trace("Invoking {} for attempt #{}", this.toString(), attempt);

			return invocation.proceed();
		}
		catch (Exception e)
		{
			throw e;
		}
		catch (Error e)
		{
			throw e;
		}
		catch (Throwable t)
		{
			throw new RuntimeException(t);
		}
	}


	@Override
	public RetryDecision shouldRetry(final int attempt, final Throwable e)
	{
		// Throw if the type is in alwaysRetry
		for (Class<? extends Throwable> type : alwaysRetry)
		{
			if (type.isInstance(e))
				return RetryDecision.BACKOFF_AND_RETRY;
		}

		// Don't throw if the type is in noRetry
		for (Class<? extends Throwable> type : noRetry)
		{
			if (type.isInstance(e))
				return RetryDecision.NO_LOG_AND_THROW;
		}

		// Don't throw if the type is in noRetryCore
		for (Class<? extends Throwable> type : noRetryCore)
		{
			if (type.isInstance(e))
				return RetryDecision.NO_LOG_AND_THROW;
		}

		// Don't retry if a RestExeption/WebApplicationException's Response HTTP Code is in noHttpCodes
		// Also don't retry if a 303 redirect with a cause with an HTTP Code in noHttpCodes
		if (e instanceof RestException || e instanceof WebApplicationException)
		{
			final int[] httpCodes = getHttpCodesForException(e);

			if (httpCodes != null)
			{
				final int httpCode = httpCodes[0];

				if (ArrayUtils.contains(this.noHttpCodes, httpCode))
					return RetryDecision.NO_LOG_AND_THROW;

				// See Other (used for login redirects)
				if (httpCode == 303)
				{
					final int causeHttpCode = httpCodes[1];

					if (ArrayUtils.contains(this.noHttpCodes, causeHttpCode))
						return RetryDecision.NO_LOG_AND_THROW;
				}
			}
		}

		// By default, retry
		return RetryDecision.BACKOFF_AND_RETRY;
	}


	private int[] getHttpCodesForException(final Throwable e)
	{
		final int httpCode;
		if (e instanceof RestException)
			httpCode = ((RestException) e).getHttpCode();
		else if (e instanceof WebApplicationException && ((WebApplicationException) e).getResponse() != null)
			httpCode = ((WebApplicationException) e).getResponse().getStatus();
		else
			return null;

		final int causeHttpCode;
		final Throwable cause = e.getCause();
		if (cause == null)
			causeHttpCode = Integer.MIN_VALUE;
		else if (cause instanceof RestException)
			causeHttpCode = ((RestException) cause).getHttpCode();
		else
			causeHttpCode = Integer.MIN_VALUE;

		return new int[]{httpCode, causeHttpCode};
	}


	@Override
	public String toString()
	{
		return invocation.getMethod().toString();
	}
}
