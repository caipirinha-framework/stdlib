package com.peterphi.std.guice.restclient.exception;

import com.peterphi.std.guice.restclient.jaxb.RestFailure;

import jakarta.ws.rs.client.ClientResponseContext;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A factory that reflectively builds instances of RestException<br />
 * This factory is used to gain access to the package-private setter methods in RestException
 */
public class RestExceptionFactory
{
	private static final int MAX_CACHE_SIZE = 1024;
	private static final int MAX_CLASS_NAME_LENGTH = 1024;


	private final Map<String, Optional<Constructor<RestException>>> cached = new HashMap<>();


	public RestException build(final RestFailure failure, final ClientResponseContext responseContext)
	{
		final Constructor<RestException> constructor = getExceptionConstructor(failure);

		final RestException exception;
		if (constructor != null)
		{
			exception = buildKnown(constructor, failure);
		}
		else
		{
			exception = buildUnknown(failure);
		}

		// Copy the information from the failure+response to the newly-built exception
		exception.setFailure(failure);
		exception.setCausedByRemote(true);
		exception.setResponseContext(responseContext);
		exception.setHttpCode(responseContext.getStatus());
		exception.setErrorCode(failure.errorCode);

		return exception;
	}


	@SuppressWarnings("unchecked")
	@Nullable
	private synchronized Constructor<RestException> getExceptionConstructor(@Nonnull RestFailure failure)
	{
		final String className = failure.exception != null ? failure.exception.className : null;

		if (className != null && className.length() < MAX_CLASS_NAME_LENGTH)
		{
			final Optional<Constructor<RestException>> existing = cached.get(className);

			if (existing != null)
			{
				return existing.orElse(null);
			}
			else
			{
				try
				{
					final Class<?> clazz = Class.forName(className, false, getClass().getClassLoader());

					if (RestException.class.isAssignableFrom(clazz))
					{
						final Constructor<RestException> constructor = (Constructor<RestException>) clazz.getConstructor(String.class,
						                                                                                                 Throwable.class);

						if (cached.size() >= MAX_CACHE_SIZE)
							cached.clear();

						cached.put(className, Optional.of(constructor));

						return constructor;
					}
					else
					{
						cached.putIfAbsent(className, Optional.empty());
					}
				}
				catch (Exception | Error e)
				{
					// Cannot construct this exception type, don't try again in the future
					cached.putIfAbsent(className, Optional.empty());
					return null;
				}
			}
		}

		return null;
	}


	/**
	 * Build an exception for a known exception type
	 *
	 * @param constructor
	 * @param failure
	 * @return
	 */
	private RestException buildKnown(Constructor<RestException> constructor, RestFailure failure)
	{
		try
		{
			return constructor.newInstance(failure.exception.detail, null);
		}
		catch (Exception e)
		{
			return buildUnknown(failure);
		}
	}


	/**
	 * Build an exception to represent an unknown or problematic exception type
	 *
	 * @param failure
	 * @return
	 */
	private UnboundRestException buildUnknown(RestFailure failure)
	{
		// We need to build up exception detail that reasonably accurately describes the source exception
		final String msg = failure.exception.shortName + ": " + failure.exception.detail + " (" + failure.id + ")";

		return new UnboundRestException(msg);
	}
}
