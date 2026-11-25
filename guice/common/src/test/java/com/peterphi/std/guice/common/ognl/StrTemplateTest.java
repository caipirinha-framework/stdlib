package com.peterphi.std.guice.common.ognl;

import org.apache.commons.lang.text.StrLookup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StrTemplateTest
{
	/**
	 * Make sure we don't fail on empty template expressions - that's somebody elses problem
	 */
	@Test
	public void testNonsenseInputs()
	{

		eval("()", "${}");
	}


	@Test
	public void testTemplating()
	{
		eval("", "");
		eval("(x)", "${x}");
		eval("(x)b", "${x}b");
		eval("a(x)", "a${x}");
		eval("a(x)b", "a${x}b");
		eval("a(xxx)b", "a${xxx}b");
		eval("aaa(x)bbb", "aaa${x}bbb");
		eval("(x)(y)", "${x}${y}");
		eval("a(x)b(y)c", "a${x}b${y}c");
	}


	@Test
	public void testPrefixDepth()
	{
		eval("aaa(x)bbb", "aaa${{x}}bbb");
		eval("aaa(x)bbb", "aaa${{{x}}}bbb");
		eval("(x } y)", "${{x } y}}");
		eval("(x } y)", "${{{x } y}}}");
		eval("(x }} y)", "${{{x }} y}}}");
	}


	@Test
	public void testEscaping()
	{
		eval("z${x}(y)", "z$${x}${y}");
		eval("z${{x}}(y)", "z$${{x}}${y}");
	}


	@Test
	public void testExtremeDepth()
	{
		eval("(x }}}}}}}}}}}}}}}} )", "${{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{{x }}}}}}}}}}}}}}}} }}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}}");
	}


	@Test
	public void testRecursiveEvaluation()
	{
		eval("( (testing) )", "${{{ ${{testing}} }}}");
	}


	@Test
	public void testRecursiveEvaluationBlockedByLiteralPrefix()
	{
		eval("( ${{testing}} )", "${{{:literal: ${{testing}} }}}");
	}


	@Test
	public void testHtmlEscapedByHtmlPrefix()
	{
		eval("(&lt;script&gt;evil&lt;/script&gt; and data leak of (testing) )",
		     "${{{:html:<script>evil</script> and data leak of ${{testing}} }}}");
	}


	@Test
	public void testRecursiveEvaluationBlockedAndHtmlEscapedByHtmlPrefix()
	{
		eval("(&lt;script&gt;evil&lt;/script&gt; and data leak of ${{testing}} )",
		     "${{{:literal:html:<script>evil</script> and data leak of ${{testing}} }}}");
	}


	/**
	 * Test an unusual but important case - emitting content which must be XML-escaped, but which will be inserted into an XML doc which is itself inside a JSON string
	 */
	@Test
	public void testLiteralEvaluationEscapedByXmlInJsonString()
	{
		final String expect = """
				<body>(&lt;hello\\n name=\\"${name}\\" \\/&gt;)</body>""";

		eval(expect, "<body>${{:literal:xmlbody:json:<hello\n name=\"${name}\" />}}</body>");
	}


	@Test
	public void testAlphanumEscape()
	{
		eval("stärt aBc_dEf_gh énd", "stärt ${:literal:alphanum:äBc dEf_g-h} énd");
		eval("stärt aBc_dEf_gh énd".replaceAll("_", ""), "stärt ${:literal:alphanum:tr:_::äBc dEf_g-h} énd");
	}


	@Test
	public void testTrEscape()
	{
		eval("abc (äBBCdEf_g-h) abc", "abc ${:literal:tr:abc:ABC:äBbcdEf_g-h} abc");
	}


	@Test
	public void testOpenNoCloseAtAll()
	{
		assertThrows(IllegalArgumentException.class, () -> eval(null, "${x"));
	}


	@Test
	public void testOpenNoCloseAtDepth3()
	{
		assertThrows(IllegalArgumentException.class, () -> eval(null, "${{{x"));
	}


	@Test
	public void testOpenNoCloseOfSameDepth()
	{
		assertThrows(IllegalArgumentException.class, () -> eval(null, "${{{x}"));
	}


	private static void eval(final String expect, final String template)
	{
		final StrLookup lookup = new StrLookup()
		{
			@Override
			public String lookup(final String key)
			{
				return "(" + key + ")";
			}
		};

		final String actual = StrTemplate.evaluate(template, lookup);

		assertEquals(expect, actual, "Eval of: " + template);
	}
}
