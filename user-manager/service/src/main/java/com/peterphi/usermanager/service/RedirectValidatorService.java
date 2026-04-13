package com.peterphi.usermanager.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.usermanager.db.dao.hibernate.OAuthServiceDaoImpl;
import com.peterphi.usermanager.db.entity.OAuthServiceEntity;
import org.apache.commons.lang.StringUtils;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Singleton
public class RedirectValidatorService
{
	private static final Pattern USER_MANAGER_PREFIX = Pattern.compile(".*?/user-manager/?", Pattern.CASE_INSENSITIVE);

	@Inject
	OAuthServiceDaoImpl serviceDao;

	@Inject(optional = true)
	@Named("redirect-validator.allow-relative")
	public boolean allowAllRelativeRedirects = true;

	private List<String> serviceUrls = null;


	/**
	 * Rewrite a redirect URL if necessary to ensure that the redirect will go to a safe URL (either a trusted URL or to a warning
	 * that an unsafe redirection is about to happen)
	 *
	 * @param url
	 * @return
	 */
	public String rewriteRedirect(final String url)
	{
		if (StringUtils.isEmpty(url))
			return "/";

		// Reject any URL that isn't plainly http(s) or a server-relative path. This stops dangerous schemes
		// (javascript:, data:, vbscript:, ...) being carried through to the /redirect-warning template where
		// a user click would otherwise execute them in-origin.
		if (!isHttpOrRelative(url))
			return "/";

		URI uri = URI.create(url);

		if (StringUtils.startsWithIgnoreCase(uri.getPath(), "/user-manager"))
			return USER_MANAGER_PREFIX
					       .matcher(url)
					       .replaceFirst("/"); // Rewrite any user-manager redirects to be relative to this service
		else if (isSafe(url))
			return url;
		else
			return UriBuilder.fromPath("/redirect-warning").queryParam("url", url).build().toASCIIString();
	}


	/**
	 * Returns true if the URL is either a server-relative path (and not protocol-relative), or uses the http/https
	 * scheme. Any other scheme (javascript:, data:, file:, vbscript:, ...) is rejected.
	 */
	private static boolean isHttpOrRelative(final String url)
	{
		if (StringUtils.isBlank(url))
			return true;

		// Server-relative path (but not "//..." which is protocol-relative)
		if (url.startsWith("/") && !url.startsWith("//"))
			return true;

		// Explicit http / https only
		if (StringUtils.startsWithIgnoreCase(url, "http://") || StringUtils.startsWithIgnoreCase(url, "https://"))
			return true;

		return false;
	}


	boolean isSafe(final String url)
	{
		if (StringUtils.isBlank(url))
			return true;

		if (!isHttpOrRelative(url))
			return false;

		if (allowAllRelativeRedirects && (url.startsWith("/") && !url.startsWith("//") || url.startsWith("..")))
			return true;
		else if (isKnownClientService(url))
			return true;
		else
			return false;
	}


	private boolean isKnownClientService(final String url)
	{
		List<String> temp = this.serviceUrls;

		if (temp != null)
		{
			if (hasPermittedPrefix(url, temp))
				return true;
		}

		// No prefixes matched (or no prefixes available). Regenerate the prefix list in case the db side has changed
		temp = repopulateServiceUrls();
		this.serviceUrls = temp;

		return hasPermittedPrefix(url, temp);
	}


	private boolean hasPermittedPrefix(final String url, final List<String> prefixes)
	{
		if (prefixes == null)
			return false;

		for (String prefix : prefixes)
		{
			if (StringUtils.startsWithIgnoreCase(url, prefix))
				return true;
		}

		return false;
	}


	@Transactional
	public List<String> repopulateServiceUrls()
	{
		List<String> whitelist = new ArrayList<>();

		for (OAuthServiceEntity service : serviceDao.getAll())
		{
			for (String url : StringUtils.split(StringUtils.remove(service.getEndpoints(), "\r"), '\n'))
			{
				if (url.charAt(0) == '*')
					whitelist.add(url.substring(1)); // Star is to allow a redirect to anywhere in a service, but not to allow any prefix to be used for an OAuth callback URL
				else if (url.startsWith("http")) // N.B. we only whitelist absolute URLs here - relative URLs are covered by a validator flag
					whitelist.add(url);
			}
		}

		return whitelist;
	}
}
