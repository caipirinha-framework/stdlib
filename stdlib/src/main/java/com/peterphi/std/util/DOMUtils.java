package com.peterphi.std.util;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class DOMUtils
{
	private static DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;
	private static final int DEFAULT_INDENT = 2;

	private DOMUtils()
	{
	}


	private static DocumentBuilderFactory createDocumentBuilderFactory()
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		try
		{
			factory.setNamespaceAware(true);

			XMLSecurity.harden(factory);
		}
		catch (ParserConfigurationException e)
		{
			throw new RuntimeException("Could not configure XML DocumentBuilderFactory!");
		}

		return factory;
	}


	private static TransformerFactory createTransformerFactory()
	{
		TransformerFactory factory = TransformerFactory.newInstance();

		try
		{
			XMLSecurity.harden(factory);
		}
		catch (TransformerConfigurationException e)
		{
			throw new RuntimeException("Could not configure XML TransformerFactory!", e);
		}

		return factory;
	}


	/**
	 * Create a new (namespace-aware) DocumentBuilder
	 *
	 * @return
	 */
	public static DocumentBuilder createDocumentBuilder()
	{
		try
		{
			if (DOCUMENT_BUILDER_FACTORY == null)
			{
				DOCUMENT_BUILDER_FACTORY = createDocumentBuilderFactory();
			}

			return DOCUMENT_BUILDER_FACTORY.newDocumentBuilder();
		}
		catch (ParserConfigurationException e)
		{
			throw new RuntimeException("Error creating DocumentBuilder: " + e.getMessage(), e);
		}
	}


	public static Document parse(File file)
	{
		if (file == null)
			throw new IllegalArgumentException("Must provide non-null XML input to parse!");

		try
		{
			final Document document;

			DocumentBuilder documentBuilder = createDocumentBuilder();
			document = documentBuilder.parse(file);

			return document;
		}
		catch (SAXException e)
		{
			throw new RuntimeException("Error parsing xml: " + e.getMessage(), e);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error parsing xml: " + e.getMessage(), e);
		}
	}


	public static Document parse(String xml)
	{
		if (xml == null)
			throw new IllegalArgumentException("Must provide non-null XML input to parse!");

		return parse(new StringReader(xml));
	}


	public static Document parse(InputStream xml)
	{
		if (xml == null)
			throw new IllegalArgumentException("Must provide non-null XML input to parse!");

		return parse(new InputSource(xml));
	}


	public static Document parse(Reader xml)
	{
		if (xml == null)
			throw new IllegalArgumentException("Must provide non-null XML input to parse!");

		return parse(new InputSource(xml));
	}


	public static Document parse(InputSource src)
	{
		if (src == null)
			throw new IllegalArgumentException("Must provide non-null XML input to parse!");

		DocumentBuilder documentBuilder = createDocumentBuilder();
		try
		{
			return documentBuilder.parse(src);
		}
		catch (SAXException e)
		{
			throw new RuntimeException("Error parsing xml: " + e.getMessage(), e);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error parsing xml: " + e.getMessage(), e);
		}
	}


	public static String serialise(Node n)
	{
		StringWriter writer = new StringWriter(1024);

		serialise(n, writer);

		return writer.toString();
	}


	public static byte[] serialiseBytes(Node n)
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);

		serialise(n, bos);

		return bos.toByteArray();
	}


	public static void serialise(Node n, StreamResult result)
	{
		if (n == null)
			throw new IllegalArgumentException("Must provide non-null XML node to serialise!");
		if (result == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		try
		{
			Transformer transform = createTransformerFactory().newTransformer();
			transform.transform(new DOMSource(n), result);
		}
		catch (TransformerConfigurationException e)
		{
			throw new RuntimeException("Error serialising node: " + e.getMessage(), e);
		}
		catch (TransformerException e)
		{
			throw new RuntimeException("Error serialising node: " + e.getMessage(), e);
		}
	}


	public static void serialise(Node n, OutputStream os)
	{
		if (n == null)
			throw new IllegalArgumentException("Must provide non-null XML node to serialise!");
		if (os == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		serialise(n, new StreamResult(os));
	}


	public static void serialise(Node n, Writer writer)
	{
		if (n == null)
			throw new IllegalArgumentException("Must provide non-null XML node to serialise!");
		if (writer == null)
			throw new IllegalArgumentException("Must provide non-null output to serialise to!");

		serialise(n, new StreamResult(writer));
	}


	public static void serialise(Node n, File file)
	{
		if (n == null)
			throw new IllegalArgumentException("Must provide non-null XML node to serialise!");
		if (file == null)
			throw new IllegalArgumentException("Must provide non-null File to serialise to!");

		serialise(n, new StreamResult(file));
	}


	/**
	 * Serialise the provided Node to a pretty-printed String with the default indent settings<br />
	 * N.B. this method also trims any leading/trailing whitespace from TEXT nodes
	 *
	 * @param source the input Node
	 * @return
	 */
	public static String pretty(final Node source)
	{
		if (source.getNodeType() == Node.DOCUMENT_NODE)
			trim(((Document) source).getDocumentElement());
		else if (source.getNodeType() == Node.ELEMENT_NODE)
			trim((Element) source);

		return pretty(new DOMSource(source));
	}

	/**
	 * Serialise the provided source to a pretty-printed String with the default indent settings
	 *
	 * @param source the input Source
	 */
	public static String pretty(final Source source)
	{
		StreamResult result = new StreamResult(new StringWriter());

		pretty(source, result);

		return result.getWriter().toString();
	}


	/**
	 * Trim all TEXT Nodes under the provided Element
	 *
	 * @param e
	 * @return
	 */
	public static Node trim(final Element e)
	{
		final List<Text> toRemove = new ArrayList<>(0);
		final NodeList children = e.getChildNodes();
		for (int i = 0; i < children.getLength(); i++)
		{
			final Node child = children.item(i);

			if (child.getNodeType() == Node.TEXT_NODE)
			{
				final Text t = (Text) child;
				final String input = t.getTextContent();
				final String trimmed = StringUtils.trimToNull(input);

				if (trimmed == null)
				{
					toRemove.add(t);
				}
				else if (input.length() != trimmed.length())
				{
					t.setTextContent(trimmed);
				}
			}
			else if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				trim((Element) child);
			}
		}

		// Remove any empty text nodes
		for (Text t : toRemove)
			e.removeChild(t);

		return e;
	}

	/**
	 * Serialise the provided source to the provided destination, pretty-printing with the default indent settings
	 *
	 * @param input the source
	 * @param output the destination
	 */
	public static void pretty(final Source input, final StreamResult output)
	{
		try
		{
			// Configure transformer
			Transformer transformer = createTransformerFactory().newTransformer();

			transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(DEFAULT_INDENT));

			transformer.transform(input, output);
		}
		catch (Throwable t)
		{
			throw new RuntimeException("Error during pretty-print operation", t);
		}
	}
}
