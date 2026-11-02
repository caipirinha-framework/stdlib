package com.peterphi.std.guice.restclient.resteasy.impl.jaxb;

import com.google.inject.Inject;
import com.peterphi.std.util.jaxb.JAXBSerialiser;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.jboss.resteasy.core.messagebody.AsyncBufferedMessageBodyWriter;
import org.xml.sax.InputSource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

@Provider
@Produces({"application/xml", "application/*+xml", "text/xml", "text/*+xml"})
@Consumes({"application/xml", "application/*+xml", "text/xml", "text/*+xml"})
public class JAXBXmlRootElementProvider<T> extends AbstractJAXBProvider<T> implements AsyncBufferedMessageBodyWriter<T>
{
	@Inject
	public JAXBXmlRootElementProvider(JAXBSerialiserFactory factory)
	{
		super(factory);
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
		return type.isAnnotationPresent(XmlRootElement.class);
	}


	@Override
	public T readFrom(final Class<T> type,
	                  final Type genericType,
	                  final Annotation[] annotations,
	                  final MediaType mediaType,
	                  final MultivaluedMap<String, String> httpHeaders,
	                  final InputStream entityStream) throws IOException, WebApplicationException
	{
		return (T) readFrom(factory.getInstance(type), type, entityStream);
	}


	@Override
	public void writeTo(final T t,
	                    final Class<?> type,
	                    final Type genericType,
	                    final Annotation[] annotations,
	                    final MediaType mediaType,
	                    final MultivaluedMap<String, Object> httpHeaders,
	                    final OutputStream entityStream) throws IOException, WebApplicationException
	{
		writeTo(factory.getInstance(type), t, entityStream);
	}


	protected Object readFrom(final JAXBSerialiser serialiser, final Class<T> type, final InputStream is) throws IOException
	{
		return super.deserialiseWithSAX(serialiser, type, new InputSource(is));
	}


	protected void writeTo(final JAXBSerialiser serialiser, final T obj, final OutputStream os) throws IOException
	{
		serialiser.serialise(obj, os);
	}
}
