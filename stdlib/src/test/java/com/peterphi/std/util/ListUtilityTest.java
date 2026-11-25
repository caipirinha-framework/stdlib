package com.peterphi.std.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ListUtilityTest
{
	@Test
	public void testHeadWithEmptyList()
	{
		List<String> list = Arrays.asList();

		assertNull(ListUtility.head(list));
	}


	@Test
	public void testNoNPEWhenHeadWithNullList()
	{
		assertNull(ListUtility.head(null));
	}


	@Test
	public void testHeadWithList()
	{
		List<String> list = Arrays.asList("a", "b", "c");

		assertNotNull(ListUtility.head(list));
		assertEquals("a", ListUtility.head(list));
	}


	@Test
	public void testTailWithList()
	{
		List<String> list = Arrays.asList("a", "b", "c");

		assertNotNull(ListUtility.tail(list));
		assertEquals(Arrays.asList("b", "c"), ListUtility.tail(list));
	}


	@Test
	public void testTailWithOneItemList()
	{
		List<String> list = Arrays.asList("a");

		assertNotNull(ListUtility.tail(list));
		assertEquals(Collections.EMPTY_LIST, ListUtility.tail(list));
	}


	@Test
	public void testTailWithEmptyList()
	{
		List<String> list = Arrays.asList();

		assertNotNull(ListUtility.tail(list));
		assertEquals(list, ListUtility.tail(list));
	}


	@Test
	public void testListAppliedToListIsCopyWithSameContent()
	{
		List<String> list = Arrays.asList("a", "b");

		assertTrue(list != ListUtility.list(list));
		assertEquals(list, ListUtility.list(list));
	}


	@Test
	public void testListAppliedToIterableIsCorrect()
	{
		List<String> list = Arrays.asList("a", "b");

		assertEquals(list, ListUtility.list((Iterable<String>) list));
	}
}
