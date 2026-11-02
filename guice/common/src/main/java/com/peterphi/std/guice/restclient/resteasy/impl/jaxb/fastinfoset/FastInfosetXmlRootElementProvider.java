package com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset;

import com.google.inject.Inject;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlRootElementProvider;
import com.peterphi.std.util.jaxb.JAXBSerialiser;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ext.Provider;
import jakarta.xml.bind.JAXBElement;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Provider
@Consumes({"application/fastinfoset", "application/*+fastinfoset"})
@Produces({"application/fastinfoset", "application/*+fastinfoset"})
public class FastInfosetXmlRootElementProvider<T> extends JAXBXmlRootElementProvider<T>
{
	private static final Logger log = LoggerFactory.getLogger(FastInfosetXmlRootElementProvider.class);

	@Inject
	public FastInfosetXmlRootElementProvider(final JAXBSerialiserFactory factory)
	{
		super(factory);
	}


	@Override
	protected void writeTo(final JAXBSerialiser serialiser, final T obj, final OutputStream os) throws IOException
	{
		writeToOutputStream(serialiser, obj, os);
	}


	@Override
	protected Object readFrom(final JAXBSerialiser serialiser, final Class<T> clazz, final InputStream is) throws IOException
	{
		return readFromInputStream(serialiser, clazz, is);
	}


	protected static void writeToOutputStream(final JAXBSerialiser serialiser, final Object obj, final OutputStream os)
	{
		try
		{
			final XMLStreamWriter writer = FastInfosetHelper.getOutputFactory().createXMLStreamWriter(os, "utf8");
			try
			{
				serialiser.serialise(obj, writer);
			}
			finally
			{
				writer.close();
			}
		}
		catch (XMLStreamException e)
		{
			throw new RuntimeException("Error writing fast infoset while serialising " + obj.getClass() + ": " + e.getMessage(),
			                           e);
		}
	}


	protected static Object readFromInputStream(final JAXBSerialiser serialiser,
	                                            final Class<?> clazz,
	                                            final InputStream is) throws IOException
	{
		final Object obj;
		try (is)
		{
			final XMLStreamReader reader = FastInfosetHelper.getInputFactory().createXMLStreamReader(is);
			try
			{
				obj = serialiser.deserialise(reader);
			}
			finally
			{
				reader.close();
			}
		}
		catch (XMLStreamException e)
		{
			log.warn("Error reading fast infoset class {}", clazz, e);

			throw new RuntimeException("Error reading fast infoset input while reading " + clazz + ": " + e.getMessage(), e);
		}
		catch (IOException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			log.warn("Error reading fast infoset class {}", clazz, e);

			throw e;
		}

		// Optionally unwrap & return
		if (obj instanceof JAXBElement<?> e)
			return clazz.cast(e.getValue());
		else
			return clazz.cast(obj);
	}
}
