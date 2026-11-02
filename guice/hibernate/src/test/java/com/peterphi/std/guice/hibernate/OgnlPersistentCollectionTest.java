package com.peterphi.std.guice.hibernate;

import com.peterphi.std.guice.common.ognl.OgnlEvaluator;
import org.hibernate.collection.spi.PersistentMap;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

public class OgnlPersistentCollectionTest
{
	@Test
	public void supportsDefaultMapMethodsOnPersistentCollections()
	{
		final var session = mock(SharedSessionContractImplementor.class, RETURNS_DEEP_STUBS);
		final var map = new PersistentMap<String, String>(session, new HashMap<>());
		final var expression = new OgnlEvaluator("getOrDefault('Territories', '')");
		assertEquals("", expression.evaluate(map));
		map.put("Territories", "GB");
		assertEquals("GB", expression.evaluate(map));
	}
}
