package com.peterphi.std.util.jaxb;

import com.peterphi.std.util.jaxb.type.MultiXSDSchemaFile;
import com.peterphi.std.util.jaxb.type.MultiXSDSchemaFiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jakarta.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.dom.DOMResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles the logic of creating a MultiXSD
 */
public class MultiXSDSchemaCollector extends SchemaOutputResolver
{
	private final Map<String, DOMResult> schemas = new HashMap<>();
	private boolean loosenXmlAnyConstraints = true;


	public Result createOutput(String namespaceURI, String suggestedFileName) throws IOException
	{
		final DOMResult result = new DOMResult();

		result.setSystemId(suggestedFileName);

		schemas.put(suggestedFileName, result);

		return result;
	}


	public MultiXSDSchemaCollector withLoosenXmlAnyConstraints(boolean value)
	{
		this.loosenXmlAnyConstraints = value;
		return this;
	}


	/**
	 * Produces an XML Schema or a Stdlib SchemaFiles document containing the XML Schemas
	 *
	 * @return
	 */
	public MultiXSDSchemaFiles encode()
	{
		MultiXSDSchemaFiles files = new MultiXSDSchemaFiles();

		for (Map.Entry<String, DOMResult> entry : schemas.entrySet())
		{
			MultiXSDSchemaFile file = new MultiXSDSchemaFile();

			file.name = entry.getKey();
			file.schema = getElement(entry.getValue().getNode());

			files.files.add(file);
		}

		// Now loosen xml:any namespace=##other to xml:any namespace=##any
		if (loosenXmlAnyConstraints)
			MultiXSDSchemaLoosener.loosenXmlAnyOtherNamespaceToXmlAnyAnyNamespace(files);

		return files;
	}


	private Element getElement(final Node node)
	{
		if (node instanceof Document)
			return ((Document) node).getDocumentElement();
		else if (node instanceof Element)
			return (Element) node;
		else
			throw new IllegalArgumentException("Cannot extract Element from node: " + node);
	}
}
