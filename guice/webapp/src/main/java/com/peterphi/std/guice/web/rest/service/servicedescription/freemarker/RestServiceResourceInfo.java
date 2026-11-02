package com.peterphi.std.guice.web.rest.service.servicedescription.freemarker;

import com.google.common.collect.ComparisonChain;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.web.rest.service.servicedescription.ExampleGenerator;
import com.peterphi.std.util.DOMUtils;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper type to provide
 */
public class RestServiceResourceInfo implements Comparable<RestServiceResourceInfo>
{
	private static final Logger log = LoggerFactory.getLogger(RestServiceResourceInfo.class);

	private final RestServiceInfo service;
	private final Method method;
	private String anchorName;

	public RestServiceResourceInfo(final RestServiceInfo service, final Method method)
	{
		this.service = service;
		this.method = method;
	}


	public static boolean isResource(Method method)
	{
		if (method.isAnnotationPresent(Path.class))
			return true;

		for (Annotation annotation : method.getAnnotations())
			if (annotation.annotationType().isAnnotationPresent(HttpMethod.class))
				return true;

		return false;
	}


	public String getAnchorName()
	{
		return anchorName;
	}


	public void setAnchorName(final String anchorName)
	{
		this.anchorName = anchorName;
	}


	public boolean isDeprecated()
	{
		return method.isAnnotationPresent(Deprecated.class);
	}


	public String getHttpMethod()
	{
		for (Annotation annotation : method.getAnnotations())
		{
			final HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);

			if (httpMethod != null)
				return httpMethod.value();
		}

		return "UNKNOWN";
	}


	public boolean isPlainGet()
	{
		return getHttpMethod().equalsIgnoreCase("GET") && !getPath().contains("{");
	}


	public boolean isRequestXML()
	{
		return getConsumes().contains("application/xml");
	}


	public boolean isResponseXML()
	{
		return getProduces().contains("application/xml");
	}


	public String getMethodString()
	{
		return method.toGenericString();
	}


	public List<RestServiceResourceParamInfo> getParameters()
	{
		final List<RestServiceResourceParamInfo> list = new ArrayList<>();

		final Parameter[] parameters = method.getParameters();
		final Type[] types = method.getGenericParameterTypes();

		for (int i = 0; i < parameters.length; i++)
		{
			list.add(new RestServiceResourceParamInfo(parameters[i], types[i]));
		}

		return list;
	}


	public RestServiceResourceParamInfo getRequestEntity()
	{
		for (RestServiceResourceParamInfo param : getParameters())
			if (param.isEntity())
				return param;

		return null;
	}


	@Override
	public int compareTo(final RestServiceResourceInfo that)
	{
		return ComparisonChain
				       .start()
				       .compare(this.getLocalPath(), that.getLocalPath())
				       .compare(this.getHttpMethod(), that.getHttpMethod())
				       .compare(this.method.getParameterTypes().length, that.method.getParameterTypes().length)
				       .result();
	}


	public String getPath()
	{
		return concat(service.getPath(), getLocalPath());
	}


	static String concat(String a, String b)
	{
		// Strip trailing slashes from a
		while (!a.isEmpty() && a.charAt(a.length() - 1) == '/')
			a = a.substring(0, a.length() - 1);

		// Strip leading slashes from b
		while (!b.isEmpty() && b.charAt(0) == '/')
			b = b.substring(1);

		if (a.isEmpty() && !b.isEmpty())
			return "/" + b; // a must have been only / chars
		if (a.isEmpty() || b.isEmpty())
			return a + b;
		else
			return a + "/" + b;
	}


	public String getLocalPath()
	{
		Path path = method.getAnnotation(Path.class);

		if (path != null)
			return path.value();
		else
			return "/";
	}


	public Class<?> getReturnType()
	{
		final Class<?> returnType = method.getReturnType();

		if (returnType.equals(Void.TYPE))
			return null;
		else
			return returnType;
	}


	public String getConsumes()
	{
		Consumes consumes = method.getAnnotation(Consumes.class);

		if (consumes == null)
			consumes = method.getDeclaringClass().getAnnotation(Consumes.class);

		if (consumes == null || consumes.value() == null || consumes.value().length == 0)
		{
			// Special-case form param
			if (!getHttpMethod().equals("GET") &&
			    Arrays.stream(method.getParameters()).anyMatch(param -> param.isAnnotationPresent(FormParam.class)))
				return MediaType.APPLICATION_FORM_URLENCODED;

			return "*/* (default)";
		}
		else
			return StringUtils.join(consumes.value(), ", ");
	}


	public String getProduces()
	{
		Produces produces = method.getAnnotation(Produces.class);

		if (produces == null)
			produces = method.getDeclaringClass().getAnnotation(Produces.class);

		if (produces == null || produces.value() == null || produces.value().length == 0)
			return "*/* (default)";
		else
			return StringUtils.join(produces.value(), ", ");
	}


	public String getDescription()
	{
		Doc doc = method.getAnnotation(Doc.class);

		if (doc != null)
		{
			return StringUtils.join(doc.value(), "\n");
		}

		return "";
	}


	public List<String> getSeeAlsoURLs()
	{
		Doc doc = method.getAnnotation(Doc.class);

		if (doc != null)
		{
			return Arrays.asList(doc.href());
		}
		else
		{
			return Collections.emptyList();
		}
	}


	public String getCurlTemplate(String url, ExampleGenerator exampleGenerator) throws Exception
	{
		StringBuilder sb = new StringBuilder();

		sb.append("curl");

		if (!getHttpMethod().equals("GET"))
			sb.append(" -X ").append(getHttpMethod());

		// Try to add an Accept: header
		{
			Produces produces = method.getAnnotation(Produces.class);

			if (produces == null)
				produces = method.getDeclaringClass().getAnnotation(Produces.class);

			if (produces == null || produces.value() == null || produces.value().length == 0)
				sb.append(""); // don't add an Accept header
			else
				sb.append(" -H \"Accept: " + produces.value()[0] + "\"");
		}

		// Add the URL
		sb.append(" \"").append(url).append('"');


		// Add Content-Type and binary for POST/PUT/PATCH
		if (getRequestEntity() != null &&
		    (getHttpMethod().equals("POST") || getHttpMethod().equals("PUT") || getHttpMethod().equals("PATCH")))
		{
			final boolean isXML = isRequestXML();

			if (isXML)
				sb.append(" -H \"Content-Type: application/xml\" --data-binary \"@-\"");
			else if (getConsumes().contains("application/json"))
				sb.append(" -H \"Content-Type: application/json\" --data-binary \"@file.json\"");
			else if (getConsumes().contains("text/plain"))
				sb.append(" -H \"Content-Type: text/plain\" --data-binary \"").append(getExampleTextPlain()).append('"');
			else if (getRequestEntity() != null)
				sb.append(" -H \"Content-Type: " + getConsumes() + "\" --data-binary \"...\"");

			if (isXML)
			{
				sb.append(" <<EOF\n");

				try
				{
					final String example = exampleGenerator.generateExampleXML(getRequestEntity().getDataType(), true);

					sb.append(DOMUtils.pretty(DOMUtils.parse(example)));
				}
				catch (Throwable t)
				{
					// Error generating XML, ignore
					sb.append("...");
				}

				sb.append("\nEOF");
			}
		}
		else if (getRequestEntity() == null &&
		         getConsumes().equals(MediaType.APPLICATION_FORM_URLENCODED) &&
		         (getHttpMethod().equals("POST") || getHttpMethod().equals("PUT") || getHttpMethod().equals("PATCH")) &&
		         Arrays.stream(method.getParameters()).anyMatch(param -> param.isAnnotationPresent(FormParam.class)))
		{
			for (Parameter param : method.getParameters())
			{
				final FormParam form = param.getAnnotation(FormParam.class);

				if (form != null)
				{
					sb.append(" -d '" + form.value() + "=...'");
				}
			}
		}


		return sb.toString();
	}


	private String getExampleTextPlain()
	{
		if (getRequestEntity() == null || getRequestEntity().getDataType() == null)
			return "???";

		final Class<?> clazz = getRequestEntity().getDataType();

		return getExampleTextPlain(clazz);
	}


	private static String getExampleTextPlain(final Class<?> clazz)
	{
		if (clazz.equals(Boolean.class) || clazz.equals(Boolean.TYPE))
			return "true";
		else if (clazz.equals(Integer.class) ||
		         clazz.equals(Integer.TYPE) ||
		         clazz.equals(Long.class) ||
		         clazz.equals(Long.TYPE) ||
		         clazz.equals(Short.class) ||
		         clazz.equals(Short.TYPE) ||
		         clazz.equals(Float.class) ||
		         clazz.equals(Float.TYPE) ||
		         clazz.equals(Double.class) ||
		         clazz.equals(Double.TYPE))
			return "123";
		else if (clazz.isEnum())
			return Stream.of(clazz.getEnumConstants()).map(Object :: toString).collect(Collectors.joining("|"));
		else
			return "...";
	}


	public static String buildAnchorName(final RestServiceResourceInfo resource)
	{
		final String prefix;
		final String suffix;
		{
			final String method = resource.getHttpMethod();
			final boolean isGET = StringUtils.equals(method, HttpMethod.GET);
			final String types = getAbbreviatedMimeType(resource.getConsumes()) + getAbbreviatedMimeType(resource.getProduces());

			if (types.equals("xx") || (isGET && types.equals("x")))
				suffix = ""; // Make XML In/Out the default
			else if (types.equals("jj"))
				suffix = "_json"; // JSON In+Out
			else if (types.isEmpty() && method.equals(HttpMethod.DELETE))
				suffix = ""; // DELETE expected to be any/any by default
			else if (types.isEmpty())
				suffix = "_any";
			else
				suffix = "_" + types;

			if (isGET)
				prefix = "";
			else
				prefix = method.toLowerCase();
		}

		// Removing vowels from hash to avoid inadvertant profanity generation
		return prefix + sanitise(resource.getPath()) + suffix;
	}


	private static String getAbbreviatedMimeType(final String header)
	{
		if (header.contains("xml"))
			return "x";
		else if (header.indexOf('*') != -1)
			return "";
		else if (header.contains("json") || header.contains("javascript"))
			return "j";
		else if (header.contains("html"))
			return "h";
		else if (header.contains("form"))
			return "f";
		else if (header.contains("text/"))
			return "t";
		else
			return "u"; // unknown
	}


	private static final Pattern NON_ANCHOR_SAFE = Pattern.compile("[^A-Za-z0-9_\\-/:.]+");


	/**
	 * Strips out chars we don't want in anchors
	 * @param str
	 * @return
	 */
	private static String sanitise(String str)
	{
		return NON_ANCHOR_SAFE.matcher(StringUtils.replaceChars(str, '{', ':')).replaceAll("");
	}
}
