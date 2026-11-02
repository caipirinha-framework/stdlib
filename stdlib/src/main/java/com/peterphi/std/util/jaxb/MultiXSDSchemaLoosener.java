package com.peterphi.std.util.jaxb;

import com.peterphi.std.util.jaxb.type.MultiXSDSchemaFile;
import com.peterphi.std.util.jaxb.type.MultiXSDSchemaFiles;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Utility class that loosens any xsd:any constraint where namespace="##other" to namespace="##any" (because JAXB cannot
 * represent namespace="##any" with {@link jakarta.xml.bind.annotation.XmlAnyElement}, so the generated schemas are often
 * over-constrained as a result)
 */
public class MultiXSDSchemaLoosener
{
	private static final String XML_SCHEMA_NAMESPACE = "http://www.w3.org/2001/XMLSchema";
	private static final String XML_SCHEMA_ANY_ELEMENT_NAME = "any";


	public static void loosenXmlAnyOtherNamespaceToXmlAnyAnyNamespace(MultiXSDSchemaFiles files)
	{
		for (MultiXSDSchemaFile file : files.files)
		{
			loosenXmlAnyOtherNamespaceToXmlAnyAnyNamespace(file.schemaElement());
		}
	}


	/**
	 * Try to loosen any xsd:any constraint where namespace="##other" to namespace="##any" (because JAXB cannot represent
	 * namespace="##any", the generated schemas are often over-constrained as a result)
	 *
	 * @param node
	 */
	public static void loosenXmlAnyOtherNamespaceToXmlAnyAnyNamespace(final Node node)
	{
		if (node instanceof Element)
		{
			final Element element = (Element) node;

			if (XML_SCHEMA_NAMESPACE.equals(node.getNamespaceURI()) && XML_SCHEMA_ANY_ELEMENT_NAME.equals(node.getLocalName()))
			{
				if ("##other".equals(element.getAttribute("namespace")))
				{
					element.setAttribute("namespace", "##any");
				}
			}
			else
			{
				final NodeList nodes = node.getChildNodes();

				for (int i = 0; i < nodes.getLength(); i++)
				{
					loosenXmlAnyOtherNamespaceToXmlAnyAnyNamespace(nodes.item(i));
				}
			}
		}
	}
}
