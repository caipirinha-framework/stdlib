package com.peterphi.std.util;

import org.apache.commons.io.IOUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.DOMBuilder;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.DOMOutputter;
import org.jdom2.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public class JDOMUtils
{
	private JDOMUtils()
	{
	}

	//
	// DOM <--> JDOM conversion
	//

	/**
	 * Convert a JDOM Document to a DOM Document
	 *
	 * @param node
	 *
	 * @return
	 */
	public static org.w3c.dom.Document convert(org.jdom2.Document node) throws JDOMException
	{
		if (node == null)
			return null; // Convert null->null

		try
		{
			DOMOutputter outputter = new DOMOutputter();

			return outputter.output(node);
		}
		catch (JDOMException e)
		{
			throw new RuntimeException("Error converting JDOM to DOM: " + e.getMessage(), e);
		}
	}

	/**
	 * Convert a DOM Document to a JDOM Document
	 *
	 * @param node
	 *
	 * @return
	 */
	public static org.jdom2.Document convert(org.w3c.dom.Document node)
	{
		if (node == null)
			return null; // Convert null->null

		DOMBuilder builder = new DOMBuilder();
		return builder.build(node);
	}

	/**
	 * Convert a JDOM Element to a DOM Element
	 *
	 * @param node
	 *
	 * @return
	 */

	public static org.w3c.dom.Element convert(org.jdom2.Element node) throws JDOMException
	{
		if (node == null)
			return null; // Convert null->null

		try
		{
			DOMOutputter outputter = new DOMOutputter();

			return outputter.output(node);
		}
		catch (JDOMException e)
		{
			throw new RuntimeException("Error converting JDOM to DOM: " + e.getMessage(), e);
		}
	}

	/**
	 * Convert a DOM Element to a JDOM Element
	 *
	 * @param node
	 *
	 * @return
	 */
	public static org.jdom2.Element convert(org.w3c.dom.Element node)
	{
		if (node == null)
			return null; // Convert null->null

		DOMBuilder builder = new DOMBuilder();

		return builder.build(node);
	}


	//
	// Parse
	//

	private static SAXBuilder createHardenedSAXBuilder()
	{
		SAXBuilder builder = new SAXBuilder();

		builder.setFeature(XMLSecurity.DISALLOW_DOCTYPE_DECL, true);
		builder.setFeature(XMLSecurity.EXTERNAL_GENERAL_ENTITIES, false);
		builder.setFeature(XMLSecurity.EXTERNAL_PARAMETER_ENTITIES, false);
		builder.setExpandEntities(false);

		return builder;
	}

	public static Document parse(String xml)
	{
		return parse(new StringReader(xml));
	}

	public static Document parse(byte[] xml)
	{
		return parse(new ByteArrayInputStream(xml));
	}

	public static Document parse(File file)
	{
		try
		{
			FileInputStream fis = new FileInputStream(file);
			try
			{
				return parse(fis);
			}
			finally
			{
				IOUtils.closeQuietly(fis);
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error parsing XML from " + file + ": " + e.getMessage(), e);
		}
	}

	public static Document parse(InputStream is)
	{
		try
		{
			return createHardenedSAXBuilder().build(is);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error parsing XML: " + e.getMessage(), e);
		}
		catch (JDOMException e)
		{
			throw new RuntimeException("Error parsing XML: " + e.getMessage(), e);
		}
	}

	public static Document parse(Reader reader)
	{
		try
		{
			return createHardenedSAXBuilder().build(reader);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error parsing XML: " + e.getMessage(), e);
		}
		catch (JDOMException e)
		{
			throw new RuntimeException("Error parsing XML: " + e.getMessage(), e);
		}
	}


	//
	// Serialise
	//

	public static String serialise(org.jdom2.Document doc)
	{
		StringWriter sw = new StringWriter(1024);

		serialise(doc, sw);

		return sw.toString();
	}

	public static void serialise(org.jdom2.Document doc, Writer writer)
	{
		if (doc == null)
			throw new IllegalArgumentException("Must provide non-null XML document to serialise!");
		if (writer == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		try
		{
			new XMLOutputter().output(doc, writer);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error serialising XML: " + e.getMessage(), e);
		}
	}

	public static void serialise(org.jdom2.Document doc, OutputStream os)
	{
		if (doc == null)
			throw new IllegalArgumentException("Must provide non-null XML document to serialise!");
		if (os == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		try
		{
			new XMLOutputter().output(doc, os);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error serialising XML: " + e.getMessage(), e);
		}
	}


	public static String serialise(org.jdom2.Element el)
	{
		if (el == null)
			throw new IllegalArgumentException("Must provide non-null XML element to serialise!");

		StringWriter sw = new StringWriter(1024);

		serialise(el, sw);

		return sw.toString();
	}

	public static byte[] serialiseBytes(org.jdom2.Element el)
	{
		if (el == null)
			throw new IllegalArgumentException("Must provide non-null XML element to serialise!");

		ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);

		serialise(el, bos);

		return bos.toByteArray();
	}

	public static void serialise(org.jdom2.Element el, Writer writer)
	{
		if (el == null)
			throw new IllegalArgumentException("Must provide non-null XML element to serialise!");
		if (writer == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		try
		{
			new XMLOutputter().output(el, writer);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error serialising XML: " + e.getMessage(), e);
		}
	}

	public static void serialise(org.jdom2.Element el, OutputStream os)
	{
		if (el == null)
			throw new IllegalArgumentException("Must provide non-null XML element to serialise!");
		if (os == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		try
		{
			new XMLOutputter().output(el, os);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error serialising XML: " + e.getMessage(), e);
		}
	}
}
