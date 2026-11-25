package com.peterphi.std.guice.common.ognl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class OgnlFactoryTest
{
	@Test
	public void testParsedExpression()
	{
		final OgnlEvaluator evaluator = OgnlFactory.getInstance(new Object(), "'xyz'");

		assertEquals("xyz", evaluator.evaluate(new Object()));
	}


	@Test
	public void testStaticMethodCallParseIntAndToStringBase36()
	{
		assertEquals("z", OgnlFactory.template("${ @Integer@toString(@Integer@valueOf(get(0)), 36) }", List.of("35")));
	}

	@Test
	public void testCompiledExpression()
	{
		final OgnlEvaluator evaluator = OgnlFactory.getInstance(new Object(), "'xyz'");
		evaluator.compile(new Object());

		assertEquals("xyz", evaluator.evaluate(new Object()));
	}


	/**
	 * Test that getInstance returns the same object for the same root class, and a different object for a different root class
	 */
	@Test
	public void testGetInstanceReturnsInstancesVariedOnRootClass()
	{
		final OgnlEvaluator eval1a = OgnlFactory.getInstance(new Object(), "1");
		final OgnlEvaluator eval1b = OgnlFactory.getInstance(new Object(), "1");
		final OgnlEvaluator eval2a = OgnlFactory.getInstance(new ArrayList<>(), "1");
		final OgnlEvaluator eval2b = OgnlFactory.getInstance(new ArrayList<>(), "1");

		assertEquals(eval1a, eval1b);
		assertEquals(eval2a, eval2b);
		assertNotEquals(eval1a, eval2a);
	}


	/**
	 * Test that the template method works as expected
	 */
	@Test
	public void testTemplate()
	{
		final List<String> input = Collections.singletonList("hello world");
		assertEquals("hello world xyz - 1", OgnlFactory.template("${get(0).toString()} ${'xyz'} - ${1}", input));
	}

	/**
	 * Test that the template method works as expected
	 */
	@Test
	public void testTemplateWithEscapeDirective()
	{
		final List<String> input = Collections.singletonList("hello \"alice <bob /> and ${name}\"");
		assertEquals("hello \\\"alice &lt;bob \\/&gt; and ${name}\\\" xyz - 1", OgnlFactory.template("${get(0).toString()} ${'xyz'} - ${1}", input, ":literal:xmlbody:json:"));
	}


	@Test
	public void testStringUtilss()
	{
		assertEquals(" x", OgnlFactory.template("${#StringUtils.leftPad('x', 2)}", new Object()));
	}
}
