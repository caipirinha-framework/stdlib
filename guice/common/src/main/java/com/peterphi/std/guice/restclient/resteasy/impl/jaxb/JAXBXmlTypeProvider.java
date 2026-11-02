package com.peterphi.std.guice.restclient.resteasy.impl.jaxb;

import com.google.inject.Inject;
import com.peterphi.std.util.jaxb.JAXBSerialiser;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.jboss.resteasy.core.messagebody.AsyncBufferedMessageBodyWriter;
import org.jboss.resteasy.spi.util.Types;
import org.xml.sax.InputSource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlRegistry;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

@Provider
@Produces({"application/xml", "application/*+xml", "text/xml", "text/*+xml"})
@Consumes({"application/xml", "application/*+xml", "text/xml", "text/*+xml"})
public class JAXBXmlTypeProvider<T> extends AbstractJAXBProvider<T> implements AsyncBufferedMessageBodyWriter<T>
{
	private final ConcurrentHashMap<Class<?>, Adapter> adapters;


	@Inject
	public JAXBXmlTypeProvider(JAXBSerialiserFactory factory)
	{
		super(factory);

		this.adapters = new ConcurrentHashMap<>(64);
	}


	/**
	 * Construct a new JAXBXmlTypeProvider that will share the JAXBSerialiserFactory and Adapter Map of an existing provider
	 * @param source
	 */
	protected JAXBXmlTypeProvider(JAXBXmlTypeProvider source)
	{
		super(source.factory);

		this.adapters = source.adapters;
	}


	@Override
	public boolean isReadable(final Class<?> type,
	                          final Type genericType,
	                          final Annotation[] annotations,
	                          final MediaType mediaType)
	{
		return supports(type);
	}


	@Override
	public boolean isWriteable(final Class<?> type,
	                           final Type genericType,
	                           final Annotation[] annotations,
	                           final MediaType mediaType)
	{
		return supports(type);
	}


	protected boolean supports(final Class<?> type)
	{
		return (type.isAnnotationPresent(XmlType.class) || type.equals(JAXBElement.class)) &&
		       !type.isAnnotationPresent(XmlRootElement.class);
	}


	@Override
	public T readFrom(final Class<T> type,
	                  final Type genericType,
	                  final Annotation[] annotations,
	                  final MediaType mediaType,
	                  final MultivaluedMap<String, String> httpHeaders,
	                  final InputStream entityStream) throws IOException
	{
		final Adapter adapter = getAdapter(type, genericType);

		return super.deserialiseWithSAX(adapter.serialiser, type, new InputSource(entityStream));
	}


	@Override
	public void writeTo(final T t,
	                    final Class<?> type,
	                    final Type genericType,
	                    final Annotation[] annotations,
	                    final MediaType mediaType,
	                    final MultivaluedMap<String, Object> httpHeaders,
	                    final OutputStream entityStream)
	{
		final Adapter adapter = getAdapter(type, genericType);

		writeTo(t, adapter, entityStream);
	}


	protected Adapter getAdapter(final Class<?> type, Type genericType)
	{
		final Class<?> clazz;
		if (!type.equals(JAXBElement.class))
		{
			clazz = type;
		}
		else
		{
			// Acquire the appropriate JAXBSerialiser by querying the generic JAXBElement<X> type param
			if (genericType == null)
				throw new IllegalArgumentException("Unable to determine generic type for " +
				                                   type +
				                                   ": missing generic type information!");

			clazz = Types.getTypeArgument(genericType);
		}

		return adapters.computeIfAbsent(clazz, this :: createAdapter);
	}


	private Adapter createAdapter(final Class<?> clazz)
	{
		final Object fac = getObjectFactory(clazz);

		for (Method method : fac.getClass().getDeclaredMethods())
		{
			if (method.getParameterCount() == 1 &&
			    method.getParameterTypes()[0].equals(clazz) &&
			    method.getName().startsWith("create"))
			{
				if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()))
				{
					final JAXBSerialiser serialiser = factory.getInstance(clazz.getPackageName());

					return new Adapter(serialiser, clazz, fac, method);
				}
			}
		}

		throw new IllegalArgumentException("Cannot determine Root Element Name for " +
		                                   clazz +
		                                   ": ObjectFactory does not have create method that takes this class!");
	}


	private Object getObjectFactory(final Class<?> clazz)
	{
		final String name = clazz.getPackageName() + ".ObjectFactory";

		try
		{
			final Class<?> factoryClass = clazz.getClassLoader().loadClass(name);

			if (!factoryClass.isAnnotationPresent(XmlRegistry.class))
				throw new IllegalArgumentException("Cannot determine Root Element Name for " +
				                                   clazz +
				                                   ": ObjectFactory in same package does not have XmlRegistry annotation!");

			return factoryClass.getConstructor().newInstance();
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalArgumentException("Cannot determine Root Element Name for " +
			                                   clazz +
			                                   ": ObjectFactory could not be found!");
		}
	}


	protected void writeTo(final Object obj, final Adapter adapter, final OutputStream os)
	{
		final Object wrapped = adapter.wrap(obj);

		adapter.serialiser().serialise(wrapped, os);
	}


	protected record Adapter(JAXBSerialiser serialiser, Class<?> clazz, Object objectFactory, Method createElement)
	{
		public JAXBElement<?> wrap(final Object obj)
		{
			if (obj instanceof JAXBElement<?> e)
				return e; // Already wrapped

			try
			{
				return (JAXBElement<?>) createElement.invoke(objectFactory, obj);
			}
			catch (ReflectiveOperationException e)
			{
				throw new RuntimeException("Unable to serialise " + clazz + " to XML: ObjectFactory.create method failed", e);
			}
		}
	}
}
