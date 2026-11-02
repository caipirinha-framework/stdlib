package com.peterphi.std.guice.restclient.resteasy.impl.jaxb;

import com.peterphi.std.util.jaxb.JAXBSerialiser;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.jboss.resteasy.plugins.providers.AbstractEntityProvider;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import jakarta.xml.bind.JAXBElement;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

abstract class AbstractJAXBProvider<T> extends AbstractEntityProvider<T>
{
	private static SAXParserFactory SAX_PARSER_FACTORY;

	protected final JAXBSerialiserFactory factory;


	public AbstractJAXBProvider(final JAXBSerialiserFactory factory)
	{
		this.factory = factory;

		if (SAX_PARSER_FACTORY == null)
		{
			try
			{
				final SAXParserFactory spf = SAXParserFactory.newInstance();

				spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
				spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
				spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
				spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

				spf.setFeature("http://xml.org/sax/features/namespaces", true);
				spf.setFeature("http://xml.org/sax/features/validation", false);

				SAX_PARSER_FACTORY = spf;
			}
			catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e)
			{
				throw new RuntimeException("Unable to set up SAXParserFactory to securely decode XML inputs!", e);
			}
		}
	}


	protected T deserialiseWithSAX(final JAXBSerialiser serialiser, final Class<T> clazz, final InputSource inputSource)
	{
		// Set up a SAXSource (since we don't want to allow DTDs, etc.
		final SAXSource source;
		try
		{
			final XMLReader reader = SAX_PARSER_FACTORY.newSAXParser().getXMLReader();
			source = new SAXSource(reader, inputSource);
		}
		catch (ParserConfigurationException | SAXException e)
		{
			throw new RuntimeException("Error setting up XML source while reading " + clazz, e);
		}

		return deserialise(serialiser, clazz, source);
	}


	protected T deserialise(final JAXBSerialiser serialiser, final Class<T> clazz, final Source source)
	{
		// Unmarshal
		final Object obj = serialiser.deserialise(source);

		// Optionally unwrap & return
		if (obj instanceof JAXBElement<?> e && !clazz.equals(JAXBElement.class))
			return clazz.cast(e.getValue());
		else
			return clazz.cast(obj);
	}
}
