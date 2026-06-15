package com.peterphi.std.guice.web.rest.scoping;

/**
 * Based on Guice ServletScopes<br />
 * <p>
 * Copyright (C) 2006 Google Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.peterphi.std.guice.web.HttpCallContext;

import javax.servlet.http.HttpSession;

public final class SessionScoping implements Scope
{
	public static final SessionScoping INSTANCE = new SessionScoping();

	/**
	 * A sentinel attribute value representing null.
	 */
	enum NullObject
	{
		INSTANCE
	}


	private SessionScoping()
	{
	}


	public <T> Provider<T> scope(Key<T> key, final Provider<T> creator)
	{
		final String name = key.toString();

		return new Provider<T>()
		{
			public T get()
			{
				final HttpCallContext ctx = HttpCallContext.get();
				final HttpSession session = ctx.getRequest().getSession();

				synchronized (session)
				{
					final Object obj = session.getAttribute(name);
					if (NullObject.INSTANCE == obj)
					{
						return null;
					}
					else
					{
						@SuppressWarnings("unchecked") T t = (T) obj;

						if (t == null)
						{
							// Construct a new instance
							t = creator.get();

							// Cache the instance (unless it's a circular proxy)
							if (!Scopes.isCircularProxy(t))
							{
								if (t == null)
									session.setAttribute(name, NullObject.INSTANCE);
								else
									session.setAttribute(name, t);
							}
						}

						return t;
					}
				}
			}


			@Override
			public String toString()
			{
				return String.format("%s[%s]", creator, SessionScoping.INSTANCE);
			}
		};
	}


	public <T> void seed(Key<T> key, T instance)
	{
		final String name = key.toString();

		final HttpCallContext ctx = HttpCallContext.get();
		final HttpSession session = ctx.getRequest().getSession();

		synchronized (session)
		{
			if (exists(key))
				throw new IllegalArgumentException("Cannot seed Session scope with instance for " +
				                                   key +
				                                   ": Session scope already has an instance of this key!");

			if (instance != null)
				session.setAttribute(name, instance);
			else
				session.setAttribute(name, NullObject.INSTANCE);
		}
	}


	public <T> boolean exists(Key<T> key)
	{
		final String name = key.toString();

		final HttpCallContext ctx = HttpCallContext.get();
		final HttpSession session = ctx.getRequest().getSession();

		synchronized (session)
		{
			final Object obj = session.getAttribute(name);

			// The attribute exists only if a real value is present (a NullObject.INSTANCE sentinel is treated as absent).
			return (obj != null && obj != NullObject.INSTANCE);
		}
	}


	@Override
	public String toString()
	{
		return "SessionScoping";
	}
}
