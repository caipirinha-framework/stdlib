package com.peterphi.std.util;

import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

/**
 * Hardens JAXP XML factories against XXE by disabling DTDs and external entities. Security features fail fast: if a factory
 * does not honour a feature, configuration throws rather than silently leaving the parser vulnerable.
 */
public final class XMLSecurity
{
	public static final String DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";
	public static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
	public static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
	public static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

	private XMLSecurity()
	{
	}


	public static void harden(final DocumentBuilderFactory factory) throws ParserConfigurationException
	{
		factory.setFeature(DISALLOW_DOCTYPE_DECL, true);
		factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
		factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
		factory.setFeature(LOAD_EXTERNAL_DTD, false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
	}


	public static void harden(final SAXParserFactory factory) throws ParserConfigurationException, SAXException
	{
		factory.setFeature(DISALLOW_DOCTYPE_DECL, true);
		factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
		factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
	}


	public static void harden(final TransformerFactory factory) throws TransformerConfigurationException
	{
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

		// ACCESS_EXTERNAL_* are optional; not all TransformerFactory implementations support them
		trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
		trySetAttribute(factory, XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
	}


	private static void trySetAttribute(final TransformerFactory factory, final String name, final Object value)
	{
		try
		{
			factory.setAttribute(name, value);
		}
		catch (IllegalArgumentException e)
		{
			// Attribute not supported by this implementation
		}
	}
}
