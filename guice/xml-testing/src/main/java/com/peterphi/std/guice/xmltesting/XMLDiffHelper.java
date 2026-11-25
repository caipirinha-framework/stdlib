package com.peterphi.std.guice.xmltesting;

import com.peterphi.std.util.DOMUtils;
import com.peterphi.std.util.JDOMUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.opentest4j.AssertionFailedError;
import org.w3c.dom.Document;

import java.util.Set;

public class XMLDiffHelper
{
	public static void xmldiff(final String expectedResource,
	                           final Document actual,
	                           final Set<String> attributesToRemove,
	                           final Set<String> elementsToRemove)
	{
		final Document expected = DOMUtils.parse(XMLDiffHelper.class.getResourceAsStream(expectedResource));

		xmldiff(expected, actual, attributesToRemove, elementsToRemove);
	}


	public static void xmldiff(final Document expected,
	                           final Document actual,
	                           final Set<String> attributesToRemove,
	                           final Set<String> elementsToRemove)
	{
		Diff diff = doXmlDiff(expected, actual, attributesToRemove, elementsToRemove);

		if (!diff.similar())
			throw new AssertionFailedError(diff.toString(), pretty(expected), pretty(actual));
	}


	public static Diff doXmlDiff(final Document expected,
	                             final Document actual,
	                             final Set<String> attributesToRemove,
	                             final Set<String> elementsToRemove)
	{
		XMLUnit.setIgnoreWhitespace(true);

		filterDocument(expected, actual, attributesToRemove, elementsToRemove);

		return new Diff(expected, actual);
	}


	public static void filterDocument(final Document expected,
	                                  final Document actual,
	                                  final Set<String> attributesToRemove,
	                                  final Set<String> elementsToRemove)
	{
		XMLTestInputFilter docFilterer = new XMLTestInputFilter();

		if (attributesToRemove != null)
			docFilterer.setAttributesToRemove(attributesToRemove);
		if (elementsToRemove != null)
			docFilterer.setElementsToRemove(elementsToRemove);

		docFilterer.setRemoveComments(true);

		docFilterer.apply(expected);
		docFilterer.apply(actual);
	}


	/**
	 * Pretty-print some XML
	 *
	 * @param doc
	 *
	 * @return
	 */
	public static String pretty(Document doc)
	{
		final XMLOutputter formatter = new XMLOutputter();
		formatter.setFormat(Format.getPrettyFormat());

		return formatter.outputString(JDOMUtils.convert(doc));
	}
}

