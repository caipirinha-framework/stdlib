package com.peterphi.std.guice.web.rest.service.servicedescription.freemarker;

import com.google.common.collect.ComparisonChain;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.serviceregistry.rest.RestResource;
import org.apache.commons.lang.StringUtils;

import jakarta.ws.rs.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RestServiceInfo implements Comparable<RestServiceInfo>
{
	private final Class<?> clazz;


	public RestServiceInfo(RestResource resource)
	{
		this.clazz = resource.getResourceClass();
	}


	public boolean isDeprecated()
	{
		return clazz.isAnnotationPresent(Deprecated.class);
	}


	public String getInterfaceName()
	{
		return clazz.getSimpleName();
	}


	public String getPath()
	{
		final Path path = clazz.getAnnotation(Path.class);

		return path.value();
	}


	public List<String> getPaths()
	{
		String[] paths = getResources().stream().map(r -> r.getLocalPath()).toArray(i -> new String[i]);

		return getCommonPaths(getPath(), paths);
	}


	static List<String> getCommonPaths(final String servicePath, final String... resourcePaths)
	{
		// TODO build up a set of paths underneath this resource and identify the commonalities
		// e.g. /api/activity/{id} /api/activities /api/activities/create should return:
		// /api/activity /api/activities

		final String commonPrefix = StringUtils.getCommonPrefix(resourcePaths);

		// TODO if we have too many paths then just fall back on the service path?
		if (StringUtils.isNotBlank(commonPrefix) && commonPrefix.length() > 1)
			return Arrays.asList(resourcePaths)
			             .stream()
			             .map(path -> truncatePath(path, commonPrefix))
			             .map(RestServiceInfo:: stripTrailingSlash)
			             .map(path -> RestServiceResourceInfo.concat(servicePath, path)) // now build up the full path
			             .distinct()
			             .collect(Collectors.toList());
		else
			return Arrays.asList(servicePath);
	}


	static String stripTrailingSlash(String path)
	{
		if (path.endsWith("/"))
			return path.substring(0, path.length() - 1);
		else
			return path;
	}


	/**
	 * Truncate path to the completion of the segment ending with the common prefix
	 *
	 * @param path
	 * @param commonPrefix
	 *
	 * @return
	 */
	static String truncatePath(String path, String commonPrefix)
	{
		final int nextSlash = path.indexOf('/', commonPrefix.length());
		final int nextOpenCurly = path.indexOf('{', commonPrefix.length());

		if (nextSlash > 0)
			return path.substring(0, nextSlash);
		else if (nextOpenCurly > 0)
			return path.substring(0, nextOpenCurly);
		else
			return path;
	}


	public List<RestServiceResourceInfo> getResources()
	{
		final List<RestServiceResourceInfo> list = Arrays
				                                           .stream(clazz.getMethods())
				                                           .filter(RestServiceResourceInfo :: isResource)
				                                           .map(m -> new RestServiceResourceInfo(this, m))
				                                           .sorted()
				                                           .toList();

		Set<String> seenAnchors = new HashSet<>(0);

		int i = 0;
		for (RestServiceResourceInfo resource : list)
		{
			i++;

			final String baseAnchorName = RestServiceResourceInfo.buildAnchorName(resource);

			// If we encounter a duplicate name, append index - otherwise try to keep anchors stable
			if (seenAnchors.add(baseAnchorName))
				resource.setAnchorName(baseAnchorName);
			else
				resource.setAnchorName(baseAnchorName + "_d" + i);
		}

		return list;
	}


	public String getDescription()
	{
		final Doc doc = clazz.getAnnotation(Doc.class);

		if (doc != null)
			return StringUtils.join(doc.value(), "\n");
		else
			return "";
	}


	public List<String> getSeeAlsoURLs()
	{
		final Doc doc = clazz.getAnnotation(Doc.class);

		if (doc != null)
			return Arrays.asList(doc.href());
		else
			return Collections.emptyList();
	}


	public static List<RestServiceInfo> getAll(Collection<RestResource> resources)
	{
		return resources.stream().map(r -> new RestServiceInfo(r)).sorted().collect(Collectors.toList());
	}


	private boolean isFrameworkService()
	{
		return getPath().startsWith("/guice");
	}


	@Override
	public int compareTo(final RestServiceInfo that)
	{
		// Sort application services first, alphabetically by path (and then class name)
		return ComparisonChain.start()
		                      .compareFalseFirst(this.isFrameworkService(), that.isFrameworkService())
		                      .compare(this.getPath(), that.getPath())
		                      .compare(this.clazz.getName(), that.clazz.getName())
		                      .result();
	}
}
