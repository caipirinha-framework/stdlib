package com.peterphi.std.guice.restclient.jaxb.webquery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebQueryParserTest
{
	@Test
	public void testSimpleFragments()
	{
		assertEquals("id = 1", WebQueryParser.parse("id=1", new WebQuery()).toQueryFragment());
		assertEquals("id = 1",
		             WebQueryParser.parse("/* some comment goes here */ id=1", new WebQuery()).toQueryFragment(),
		             "multiline comment");
		assertEquals("id = 1",
		             WebQueryParser.parse("id=1 --some comment", new WebQuery()).toQueryFragment(),
		             "single line comment");
		assertEquals("id = 1\nAND id = 2",
		             WebQueryParser.parse("id=1 //some comment\nand id=2", new WebQuery()).toQueryFragment(),
		             "single line comment");
		assertEquals("id = 1",
		             WebQueryParser.parse("id= -- some comment\n1 --some other comment", new WebQuery()).toQueryFragment(),
		             "single line comment in middle of expression");

		assertEquals("name NOT STARTS alice", WebQueryParser.parse("name not starts alice", new WebQuery()).toQueryFragment());
		assertEquals("id != 1", WebQueryParser.parse("id!=1", new WebQuery()).toQueryFragment());
		assertEquals("id ~= x", WebQueryParser.parse("id~=x", new WebQuery()).toQueryFragment());
		assertEquals("id IN(1, 2, 3)", WebQueryParser.parse("id in (1,2,3)", new WebQuery()).toQueryFragment());
		assertEquals("licenses EQREF copies:size",
		             WebQueryParser.parse("licenses eqref copies:size", new WebQuery()).toQueryFragment());
		assertEquals("licenses EQREF copies:size\nAND id = 2",
		             WebQueryParser.parse("licenses eqref copies:size and id = 2", new WebQuery()).toQueryFragment());
		assertEquals("id IN(1, 2)\nAND name STARTS alice",
		             WebQueryParser.parse("id in (1,2) and name starts alice", new WebQuery()).toQueryFragment());
		assertEquals("id NOT IN(1, 2)\nAND name STARTS alice",
		             WebQueryParser.parse("id not in (1,2) and name starts alice", new WebQuery()).toQueryFragment());
		assertEquals("speed BETWEEN 1 AND 30", WebQueryParser.parse("speed between 1 and 30", new WebQuery()).toQueryFragment());
		assertEquals("dob >= now-PT5M", WebQueryParser.parse("dob >= now-PT5M", new WebQuery()).toQueryFragment());
		assertEquals("dob >= 2000-01-01T00:00:00Z",
		             WebQueryParser.parse("dob >= '2000-01-01T00:00:00Z'", new WebQuery()).toQueryFragment());
		assertEquals("id > 1", WebQueryParser.parse("id GT 1", new WebQuery()).toQueryFragment(), "Allow GT in use of >");
	}


	@Test
	public void testNotGroups()
	{
		assertEquals("NOT(id = 1)", WebQueryParser.parse("NOT(id=1)", new WebQuery()).toQueryFragment());
		assertEquals("NOT(id = 1 OR id = 2)", WebQueryParser.parse("NOT(id=1 OR id=2)", new WebQuery()).toQueryFragment());

		// NOTting an AND group is against the native NONE representation, so NOT(a AND b) gets inverted into OR(NOT a, NOT b)
		assertEquals("(id != 1 OR name != alice)",
		             WebQueryParser.parse("NOT(id = 1 AND name = alice)", new WebQuery()).toQueryFragment());

		assertEquals("NOT((id != 1 OR name != alice))",
		             WebQueryParser.parse("NOT(NOT(id = 1 AND name = alice))", new WebQuery()).toQueryFragment());
	}


	@Test
	public void testWithOrder()
	{
		assertEquals("id = 1 AND (name = foo OR title = dr) ORDER BY id, name DESC, title, title2",
		             WebQuery
				             .parse("id=1 and (name=foo or title=dr)order by id asc, name desc, title, title2")
				             .toQueryFragment()
				             .replace("\t", "")
				             .replace("\n", " "));
	}


	@Test
	public void testWithSelect()
	{
		assertEquals("SELECT id WHERE name = Bob",
		             WebQuery.parse("SELECT id WHERE name = Bob").toQueryFragment().replace("\t", "").replace("\n", " "),
		             "select with constraints");
		assertEquals("SELECT id",
		             WebQuery.parse("SELECT id").toQueryFragment().replace("\t", "").replace("\n", " "),
		             "no constraints, no order");
		assertEquals("SELECT id ORDER BY id",
		             WebQuery.parse("SELECT id ORDER BY id ASC").toQueryFragment().replace("\t", "").replace("\n", " "),
		             "order and no constraints");
	}

	@Test
	public void testWithExpand()
	{
		assertEquals("EXPAND parents,children,not:idcollections WHERE name = Bob",
		             WebQuery.parse("EXPAND parents,children,'-idcollections' WHERE name = Bob").toQueryFragment().replace("\t", "").replace("\n", " "),
		             "expand with -idcollections");
		assertEquals("EXPAND parents,children,not:idcollections WHERE name = Bob",
		             WebQuery.parse("EXPAND parents,children,not:idcollections WHERE name = Bob").toQueryFragment().replace("\t", "").replace("\n", " "),
		             "expand with -idcollections expressed with not:");
	}


	@Test
	public void testEmptyQueryWithOrder()
	{
		assertEquals("", WebQueryParser.parse("", new WebQuery()).toQueryFragment(), "empty text query");
		assertEquals("ORDER BY id",
		             WebQueryParser.parse("ORDER BY id", new WebQuery()).toQueryFragment(),
		             "text query with only ordering");
	}
}
