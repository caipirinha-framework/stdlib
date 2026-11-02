package com.peterphi.std.guice.web.rest.service.servicedescription.freemarker;

import com.peterphi.std.annotation.Doc;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RestServiceResourceParamInfo
{
	private final Type type;
	private final Class<?> clazz;
	private final Parameter parameter;


	public RestServiceResourceParamInfo(final Parameter parameter, Type type)
	{
		if (type == null)
			throw new IllegalArgumentException("Type must not be null!");

		this.type = type;
		this.parameter = parameter;

		this.clazz = parameter.getType();
	}


	public String getType()
	{
		final String pkg = clazz.getPackage() == null ? null : clazz.getPackage().getName();

		if (clazz == type && (pkg == null || pkg.startsWith("java.lang") || pkg.startsWith("java.util")))
			return clazz.getSimpleName();
		else if (clazz == UriInfo.class)
			return "";
		else
			return type.toString();
	}


	public Class<?> getDataType()
	{
		return clazz;
	}


	public String getName()
	{
		if (parameter.isAnnotationPresent(PathParam.class))
		{
			final PathParam p = parameter.getAnnotation(PathParam.class);

			return "PathParam " + p.value();
		}
		else if (parameter.isAnnotationPresent(QueryParam.class))
		{
			final QueryParam p = parameter.getAnnotation(QueryParam.class);

			return "QueryParam " + p.value();
		}
		else if (parameter.isAnnotationPresent(FormParam.class))
		{
			final FormParam p = parameter.getAnnotation(FormParam.class);

			return "FormParam " + p.value();
		}
		else if (parameter.isAnnotationPresent(HeaderParam.class))
		{
			final HeaderParam p = parameter.getAnnotation(HeaderParam.class);

			return "HeaderParam " + p.value();
		}
		else if (parameter.isAnnotationPresent(CookieParam.class))
		{
			final CookieParam p = parameter.getAnnotation(CookieParam.class);

			return "CookieParam " + p.value();
		}
		else if (UriInfo.class == clazz)
		{
			return "Query String";
		}
		else if (parameter.isAnnotationPresent(Context.class))
		{
			return "Internal Context";
		}
		else
		{
			return "unknown";
		}
	}


	public String getDefaultValue()
	{
		DefaultValue annotation = parameter.getAnnotation(DefaultValue.class);

		if (annotation != null)
			return annotation.value();
		else
			return null;
	}


	public String getDescription()
	{
		final Doc doc = getDoc();

		if (doc != null)
			return StringUtils.join(doc.value(), "\n");

		final String name = getName();

		if (String.class.equals(clazz) && "FormParam csrf_token".equals(name))
		{
			return "Token to protect against Cross-Site Request Forgery";
		}
		else if (UriInfo.class == clazz)
		{
			return "Probably: WebQuery Query String";
		}
		else if (clazz.isEnum())
		{
			return "One of: " + new SchemaGenerateUtil().getSchema(clazz);
		}

		// no doc available
		return "";
	}


	public boolean isEntity()
	{
		// Not if it has any Path/Query/Form params
		if (parameter.isAnnotationPresent(PathParam.class) ||
		    parameter.isAnnotationPresent(QueryParam.class) ||
		    parameter.isAnnotationPresent(FormParam.class) ||
		    parameter.isAnnotationPresent(HeaderParam.class) ||
		    parameter.isAnnotationPresent(CookieParam.class))
			return false;

		// Not if this comes from the JAX-RS context and not the request
		if (parameter.isAnnotationPresent(Context.class))
			return false;

		// Not if it's a jakarta.ws.rs type
		if (clazz.getPackage() != null && clazz.getPackage().getName().startsWith("jakarta.ws.rs"))
			return false;

		// has not been excluded, assume this is the entity
		return true;
	}


	public List<String> getSeeAlsoURLs()
	{
		final Doc doc = getDoc();

		if (doc != null)
		{
			return Arrays.asList(doc.href());
		}
		else if (UriInfo.class == clazz)
		{
			return List.of("https://stdlib.readthedocs.io/en/latest/framework/webquery.html#query-string-format");
		}

		return Collections.emptyList();
	}


	private Doc getDoc()
	{
		Doc doc = parameter.getAnnotation(Doc.class);

		if (doc == null && getDataType().isAnnotationPresent(Doc.class))
		{
			doc = getDataType().getAnnotation(Doc.class);
		}

		return doc;
	}
}
