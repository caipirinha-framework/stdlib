package com.peterphi.std.guice.web.rest.service.caches;

import com.google.common.cache.Cache;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.common.cached.CacheManager;
import com.peterphi.std.guice.web.rest.scoping.SessionScoped;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.thymeleaf.GuiceCoreTemplater;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SessionScoped
@AuthConstraint(id = "framework-admin", role = "framework-admin")
public class GuiceCacheManagerRestServiceImpl implements GuiceCacheManagerRestService
{
	/**
	 * The resource prefix
	 */
	private static final String PREFIX = "/com/peterphi/std/guice/web/rest/service/restcore/";

	@Inject
	GuiceCoreTemplater templater;

	@Inject
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	URI restEndpoint;

	private final String csrfToken = UUID.randomUUID().toString();


	private void checkCsrfToken(final String provided)
	{
		if (!StringUtils.equals(provided, csrfToken))
			throw new IllegalArgumentException("CSRF token not provided or invalid, refusing request");
	}


	@Override
	public String getIndex(final String message)
	{
		final TemplateCall template = templater.template(PREFIX + "cache_list.html");

		final Map<String, List<Cache>> cachesByName = CacheManager.getCachesByName();

		// Produce an alphabetical list of cache names
		final List<String> cacheNames = new ArrayList<>(cachesByName.keySet());
		cacheNames.sort(String :: compareToIgnoreCase);

		template.set("message", message);
		template.set("cacheNames", cacheNames);
		template.set("csrfToken", csrfToken);
		template.set("cachesByName", cachesByName);
		template.set("cacheUtils", this);

		return template.process();
	}


	public int countKeys(final List<Cache> caches)
	{
		int total = 0;

		for (Cache cache : caches)
		{
			total += cache.size();
		}

		return total;
	}


	@Override
	public Response invalidate(final String providedCsrfToken, final String name)
	{
		checkCsrfToken(providedCsrfToken);

		final String message;
		if (StringUtils.isEmpty(name))
		{
			message = "All caches invalidated at " + DateTime.now();

			CacheManager.invalidateAll();
		}
		else
		{
			message = "Caches for '" + name + "' invalidated at " + DateTime.now();

			final List<Cache> toInvalidate = CacheManager.getCachesByName().get(name);

			if (toInvalidate != null)
				toInvalidate.forEach(Cache :: invalidateAll);
		}

		return Response
				       .seeOther(UriBuilder
						                 .fromUri(restEndpoint.toString() + "/guice/caches")
						                 .queryParam("message", message)
						                 .build())
				       .build();
	}
}
