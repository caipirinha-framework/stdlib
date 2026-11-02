package com.peterphi.std.guice.web.rest.scoping;


/**
 * Based on Guice InternalServletModule<br />
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


import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.peterphi.std.guice.web.HttpCallContext;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Adds the {@link RequestScoped} and {@link SessionScoped} scope annotations, also exposes HttpCallContext, HttpServletRequest,
 * HttpServletResponse and HttpSession based on the current HTTP call
 */
public class ServletScopingModule extends AbstractModule
{
	@Override
	protected void configure()
	{
		bindScope(RequestScoped.class, RequestScoping.INSTANCE);
		bindScope(SessionScoped.class, SessionScoping.INSTANCE);

		bind(ServletRequest.class).to(HttpServletRequest.class);
		bind(ServletResponse.class).to(HttpServletResponse.class);
	}

	@Provides
	@RequestScoped
	public HttpServletRequest getServletRequest(HttpCallContext call)
	{
		return call.getRequest();
	}

	@Provides
	@RequestScoped
	public HttpServletResponse getServletResponse(HttpCallContext call)
	{
		return call.getResponse();
	}

	@Provides
	@RequestScoped
	public HttpCallContext getServletResponse()
	{
		return HttpCallContext.get();
	}

	@Provides
	public HttpSession getServletSession(HttpCallContext call)
	{
		return call.getRequest().getSession();
	}
}
