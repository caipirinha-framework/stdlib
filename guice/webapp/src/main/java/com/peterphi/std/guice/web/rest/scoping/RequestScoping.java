package com.peterphi.std.guice.web.rest.scoping;

/**
 * Based on Guice ServletScopes<br />
 *
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
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

import jakarta.servlet.http.HttpServletRequest;

final class RequestScoping implements Scope
{
	public static final RequestScoping INSTANCE = new RequestScoping();

	/**
	 * A sentinel attribute value representing null.
	 */
	enum NullObject
	{
		INSTANCE
	}

	private RequestScoping()
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
				final HttpServletRequest request = ctx.getRequest();

				synchronized (request)
				{
					final Object obj = request.getAttribute(name);

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
									request.setAttribute(name, NullObject.INSTANCE);
								else
									request.setAttribute(name, t);
							}
						}

						return t;
					}
				}
			}

			@Override
			public String toString()
			{
				return String.format("%s[%s]", creator, RequestScoping.INSTANCE);
			}
		};
	}

	@Override
	public String toString()
	{
		return "RequestScoping";
	}
}
