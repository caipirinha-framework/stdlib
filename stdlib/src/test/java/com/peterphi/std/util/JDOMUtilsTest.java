package com.peterphi.std.util;

import org.jdom2.Element;
import org.jdom2.JDOMConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JDOMUtilsTest
{
	/**
	 * The XML document to test - this document
	 */
	private static final String XML = "<el xmlns:test=\"urn:test\" />";


	/**
	 * Parse the XML using DOM
	 *
	 * @throws Exception
	 */
	@Test
	public void testDocumentBuilderSource() throws Exception
	{
		org.w3c.dom.Element element = parseDOM(XML);

		final String namespace = element.getAttributeNode("xmlns:test").getNamespaceURI();

		assertEquals(JDOMConstants.NS_URI_XMLNS, namespace, "DocumentBuilder output");
	}


	/**
	 * Parse the XML using JDOM, convert to DOM
	 *
	 * @throws Exception
	 */
	@Test
	public void testJdomToDom() throws Exception
	{
		org.w3c.dom.Element element = jdomToDom(parseJDOM(XML)); // load JDOM and convert to DOM

		final String namespace = element.getAttributeNode("xmlns:test").getNamespaceURI();

		assertEquals(JDOMConstants.NS_URI_XMLNS, namespace, "SAXBuilder->DOMOutputter output");
	}


	/**
	 * Parse the XML using DOM, then convert to JDOM and then finally back to DOM
	 *
	 * @throws Exception
	 */

	@Test
	public void testDomToJdomToDom() throws Exception
	{
		org.w3c.dom.Element element = jdomToDom(domToJdom(parseDOM(XML))); // load DOM, convert to JDOM and back to DOM

		final String namespace = element.getAttributeNode("xmlns:test").getNamespaceURI();

		assertEquals(JDOMConstants.NS_URI_XMLNS, namespace, "DocumentBuilder->DOMBuilder->DOMOutputter output");
	}


	private Element parseJDOM(String xml) throws Exception
	{
		return JDOMUtils.parse(xml).getRootElement();
	}


	private org.w3c.dom.Element parseDOM(String xml) throws Exception
	{
		return DOMUtils.parse(xml).getDocumentElement();
	}


	/**
	 * Converts a JDOM Element to a DOM Element
	 *
	 * @param element
	 *
	 * @return
	 *
	 * @throws Exception
	 */
	private org.w3c.dom.Element jdomToDom(org.jdom2.Element element) throws Exception
	{
		return JDOMUtils.convert(element);
	}


	/**
	 * Converts a DOM Element to a JDOM Element
	 *
	 * @param element
	 *
	 * @return
	 *
	 * @throws Exception
	 */
	private org.jdom2.Element domToJdom(org.w3c.dom.Element element) throws Exception
	{
		return JDOMUtils.convert(element);
	}
}
