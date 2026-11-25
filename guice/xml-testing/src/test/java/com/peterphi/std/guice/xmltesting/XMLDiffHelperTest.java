package com.peterphi.std.guice.xmltesting;

import com.peterphi.std.util.DOMUtils;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XMLDiffHelperTest
{
	@Test
	void includesExpectedAndActualXmlInComparisonFailure()
	{
		final var expected = DOMUtils.parse("<message>expected</message>");
		final var actual = DOMUtils.parse("<message>actual</message>");

		final var failure = assertThrows(AssertionFailedError.class,
		                                 () -> XMLDiffHelper.xmldiff(expected, actual, Set.of(), Set.of()));

		assertEquals(XMLDiffHelper.pretty(expected), failure.getExpected().getValue());
		assertEquals(XMLDiffHelper.pretty(actual), failure.getActual().getValue());
	}
}
