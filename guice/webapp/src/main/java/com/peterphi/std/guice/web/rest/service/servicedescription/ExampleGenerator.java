package com.peterphi.std.guice.web.rest.service.servicedescription;

import com.google.inject.Inject;
import com.peterphi.std.types.Timecode;
import com.peterphi.std.util.DOMUtils;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class ExampleGenerator
{
	private static final Logger log = LoggerFactory.getLogger(ExampleGenerator.class);

	@Inject
	JAXBSerialiserFactory jaxb;


	public String generateExampleXML(final Class<?> clazz, final boolean minimal) throws Exception
	{
		final Object obj = generateExampleObject(clazz, new Stack<>(), minimal ? 1 : Integer.MAX_VALUE);

		if (clazz.isAnnotationPresent(XmlRootElement.class))
			return jaxb.getInstance(clazz).serialise(obj);
		else
			return "<error text=\"Cannot generate example - does not have XmlRootElement annotation!\"/>";
	}


	private static Object generateExampleObject(final Class clazz, Stack<Class> state, final int depthLimit) throws Exception
	{
		if (clazz == null)
		{
			return null;
		}
		else if (clazz.equals(String.class))
		{
			return "...";
		}
		else if (clazz.equals(Boolean.class) || clazz.equals(Boolean.TYPE))
		{
			return true;
		}
		else if (clazz.equals(Integer.class) || clazz.equals(Integer.TYPE))
		{
			return 123;
		}
		else if (clazz.equals(Long.class) || clazz.equals(Long.TYPE))
		{
			return 123L;
		}
		else if (clazz.equals(Float.class) || clazz.equals(Float.TYPE))
		{
			return 123.456F;
		}
		else if (clazz.equals(Double.class) || clazz.equals(Double.TYPE))
		{
			return 123.456D;
		}
		else if (clazz.equals(Date.class))
		{
			return new Date(0);
		}
		else if (clazz.equals(Instant.class))
		{
			return Instant.ofEpochMilli(0);
		}
		else if (clazz.equals(OffsetDateTime.class))
		{
			return OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
		}
		else if (clazz.isEnum())
		{
			return EnumSet.allOf(clazz).stream().findFirst().orElse(null);
		}
		else if (clazz.equals(Element.class))
		{
			return DOMUtils.parse("<someXml xmlns=\"urn:example\"/>").getDocumentElement();
		}
		else if (clazz.equals(Timecode.class))
		{
			return Timecode.getInstance("00:00:00:00@25");
		}
		else if (clazz.isAnnotationPresent(XmlType.class) || clazz.isAnnotationPresent(XmlRootElement.class))
		{
			final Object obj = clazz.getDeclaredConstructor().newInstance();

			populateFields(obj, state, depthLimit);

			return obj;
		}
		else
		{
			return null; // don't know how to generate instance of this type
		}
	}


	private static void populateFields(final Object obj, final Stack<Class> state, int depthLimit) throws Exception
	{
		if (state.contains(obj.getClass()))
			return; // Do not populate - we're already inside an instance of this type
		else if (depthLimit-- <= 0)
			return; // Don't populate beyond the depth limit

		state.push(obj.getClass());


		for (Field field : obj.getClass().getDeclaredFields())
		{
			if (field.isAnnotationPresent(XmlElement.class) ||
			    field.isAnnotationPresent(XmlAttribute.class) ||
			    field.isAnnotationPresent(XmlElementWrapper.class) ||
			    field.isAnnotationPresent(XmlElementRefs.class) ||
			    field.isAnnotationPresent(XmlValue.class) ||
			    field.isAnnotationPresent(XmlAnyElement.class))
			{
				field.setAccessible(true);

				if (Collection.class.isAssignableFrom(field.getType()))
				{
					if (field.isAnnotationPresent(XmlElementRefs.class) && List.class.isAssignableFrom(field.getType()))
					{
						final List examples = new ArrayList();

						for (XmlElementRef elementRef : field.getAnnotation(XmlElementRefs.class).value())
						{
							final Object example = generateExampleObject(elementRef.type(), state, depthLimit);

							if (example != null)
								examples.add(example);
						}

						set(obj, field, examples);
					}
					else
					{
						try
						{
							final Object instance = generateExampleObject(getFirstGenericParameter(field.getGenericType()),
							                                              state,
							                                              depthLimit);

							if (instance != null)
							{
								if (List.class.isAssignableFrom(field.getType()))
									set(obj, field, Collections.singletonList(instance));
								else if (Set.class.isAssignableFrom(field.getType()))
									set(obj, field, Collections.singleton(instance));
							}
						}
						catch (Throwable t)
						{
							log.warn("Error generating example instance for simple Collection!", t);
							// ignore error understanding / converting collection
						}
					}
				}
				else if (field.get(obj) == null)
				{
					set(obj, field, generateExampleObject(field.getType(), state, depthLimit));
				}
			}
		}

		state.pop();
	}


	private static void set(final Object obj,
	                        Field field,
	                        final Object val) throws IllegalAccessException, InvocationTargetException
	{
		if (field.canAccess(obj))
			field.set(obj, val);
		else
		{
			try
			{
				final Method setter = obj.getClass().getMethod("set" + StringUtils.capitalize(field.getName()), field.getType());

				setter.invoke(obj, val);
			}
			catch (NoSuchMethodException e)
			{
				// No setter, field not public. ignore.
			}
		}
	}


	private static Class getFirstGenericParameter(final Type genericType)
	{
		if (genericType instanceof ParameterizedType)
		{
			final Type[] args = ((ParameterizedType) genericType).getActualTypeArguments();

			if (args.length > 0)
				return (Class) args[0];
		}


		return null;
	}
}
