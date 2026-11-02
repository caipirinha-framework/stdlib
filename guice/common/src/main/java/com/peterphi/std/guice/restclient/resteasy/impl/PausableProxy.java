package com.peterphi.std.guice.restclient.resteasy.impl;

import com.peterphi.std.guice.apploader.impl.GuiceBuilder;
import com.peterphi.std.guice.common.breaker.Breaker;
import com.peterphi.std.guice.restclient.exception.ServiceBreakerTripPreventsCallException;
import com.peterphi.std.io.FileHelper;
import com.peterphi.std.threading.Deadline;
import com.peterphi.std.threading.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.BadRequestException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class PausableProxy implements InvocationHandler
{
	private static final Logger log = LoggerFactory.getLogger(PausableProxy.class);

	private static Method IS_IN_HTTP_CONTEXT;

	static
	{
		// If available, ty to use HttpCallContext to determine if we're operating within an Http Call
		try
		{
			IS_IN_HTTP_CONTEXT = Class.forName("com.peterphi.std.guice.web.HttpCallContext").getMethod("peek");
		}
		catch (ClassNotFoundException e)
		{
			IS_IN_HTTP_CONTEXT = null;
		}
		catch (Throwable t)
		{
			IS_IN_HTTP_CONTEXT = null;

			throw new IllegalArgumentException("Unable to access HttpCallContext.peek!", t);
		}
	}

	private static final Timeout MAX_WAIT = new Timeout(2, TimeUnit.HOURS);
	private static final Timeout MAX_WAIT_IF_HTTP_CONTEXT = new Timeout(30, TimeUnit.SECONDS);

	/**
	 * Apply an extremely short timeout if a service call is made during startup.
	 * This is because we must not block guice environment construction: there's no way to reset breakers during startup
	 * If running in e.g. tomcat, there is no way to cause us to unload!
	 */
	private static final Timeout MAX_WAIT_IF_IN_STARTUP = Timeout.millis(500);


	private final Object rest;
	private final AtomicInteger currentlyPausedCount;
	private final boolean isFastFailServiceClient;
	private final Breaker breaker;


	public PausableProxy(final Object resteasyProxyClient,
	                     final boolean isFastFailServiceClient,
	                     final Breaker breaker,
	                     final AtomicInteger currentlyPausedCount)
	{
		this.rest = resteasyProxyClient;
		this.isFastFailServiceClient = isFastFailServiceClient;
		this.breaker = breaker;
		this.currentlyPausedCount = currentlyPausedCount;
	}


	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable
	{
		if (isPaused() && method.getDeclaringClass() != Object.class)
		{
			pause(isFastFailServiceClient);
		}

		try
		{
			return method.invoke(rest, args);
		}
		catch (InvocationTargetException e)
		{
			final Throwable cause = e.getCause();

			// Make sure we never throw a BadRequestException, because this bubbles all the way up and throws a 400 Bad Request error against our own service if uncaught
			if (cause instanceof BadRequestException br)
			{
				final String methodName = method.getDeclaringClass().getSimpleName() + "::" + method.getName();

				if (log.isWarnEnabled())
				{
					try
					{
						if (br.getResponse() != null && br.getResponse().hasEntity())
						{
							final Object entity = br.getResponse().getEntity();

							final String body = switch (entity)
							{
								case InputStream is ->
									// TODO should read binary data and use heuristic to determine if text, otherwise base64 encode
										FileHelper.cat(is);
								case Reader r -> FileHelper.cat(r);
								case CharSequence cs -> cs.toString();
								case byte[] arr -> "byte[]: " + Base64.getEncoder().encodeToString(arr);
								case null -> "(null body)";
								default -> "Entity class=" + entity.getClass().getSimpleName() + ": " + entity;
							};

							log.warn("HTTP Call {} Encountered 400 Bad Request error, response body: {}", methodName, body, br);
						}
					}
					catch (Throwable tt)
					{
						// ignore
					}
				}

				throw new RuntimeException("Remote service call " + methodName + " returned 400 Bad Request!", br);
			}
			else
			{
				throw cause;
			}
		}
	}


	/**
	 * Wait until unpaused (or until we hit the max wait timeout)
	 */
	private void pause(final boolean fastFail)
	{
		if (!fastFail)
		{
			final Deadline deadline = getTimeoutForThread().start();

			currentlyPausedCount.incrementAndGet();

			try
			{
				while (isPaused() && deadline.isValid())
				{
					Timeout.ONE_SECOND.sleep(deadline);
				}
			}
			finally
			{
				currentlyPausedCount.decrementAndGet();
			}

			if (isPaused())
				throw new ServiceBreakerTripPreventsCallException(
						"Unable to make outgoing service call: breaker is still tripped after maximum wait");
		}
		else
		{
			throw new ServiceBreakerTripPreventsCallException(
					"Unable to make outgoing service call: breaker is tripped, service client marked as fast-fail");
		}
	}


	private static Timeout getTimeoutForThread()
	{
		if (GuiceBuilder.isStartingOnThisThread())
			return MAX_WAIT_IF_IN_STARTUP;
		else if (isHttpCall())
			return MAX_WAIT_IF_HTTP_CONTEXT;
		else
			return MAX_WAIT;
	}

	private static boolean isHttpCall()
	{
		try
		{
			return IS_IN_HTTP_CONTEXT != null && IS_IN_HTTP_CONTEXT.invoke(null) != null;
		}
		catch (IllegalAccessException | InvocationTargetException e)
		{
			return false;
		}
	}


	private boolean isPaused()
	{
		return breaker != null && breaker.isTripped();
	}
}
