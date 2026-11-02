package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peterphi.std.guice.common.jackson.JacksonFactory;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WebQueryJsonTest
{
	private final ObjectMapper mapper = JacksonFactory.newObjectMapper();

	@Test
	public void nestedAndNegatedConstraintsRoundTripWithoutTypeDiscriminators() throws Exception
	{
		WebQuery query = new WebQuery().eq("name", "test")
		                               .or(group -> group.in("id", 1, 2).add(WQGroup.newNone().eq("deleted", true)))
		                               .limit(3).offset(2).computeSize(true).orderAsc("name");
		query.constraints.add(WQGroup.newAnd());
		query.constraints.add(WQGroup.newOr());
		query.constraints.add(WQGroup.newNone());

		String json = mapper.writeValueAsString(query);
		WebQuery decoded = mapper.readValue(json, WebQuery.class);

		assertFalse("The established JSON shape has no mandatory type discriminator", json.contains("@type"));
		assertEquals(query.toQueryFragment(), decoded.toQueryFragment());
		assertEquals(3, decoded.getLimit());
		assertEquals(2, decoded.getOffset());
		assertTrue(decoded.isComputeSize());
		assertTrue(decoded.constraints.constraints.getFirst() instanceof WQConstraint);
		assertTrue(decoded.constraints.constraints.get(1) instanceof WQGroup);
	}

	@Test
	public void readsExistingJsonConstraintShape() throws Exception
	{
		String json = """
				{"constraints":{"constraints":[
				  {"field":"name","function":"EQ","value":"test"},
				  {"operator":"OR","constraints":[
				    {"field":"id","function":"IN","valuelist":["1","2"]},
				    {"operator":"NONE","constraints":[]}
				  ]}
				]}}
				""";

		WebQuery query = mapper.readValue(json, WebQuery.class);
		assertEquals("name = test\nAND (id IN(1, 2) OR NOT())", query.constraints.toQueryFragment());
	}

	@Test
	public void pathOptionsRoundTripInExistingGetterArrayForm() throws Exception
	{
		WebQuery query = new WebQuery().dbfetch("children,owner").expand("children", "owner");
		WebQuery decoded = mapper.readValue(mapper.writeValueAsString(query), WebQuery.class);

		assertEquals(Set.of("children", "owner"), decoded.getDBFetch());
		assertEquals(Set.of("children", "owner"), decoded.getExpand());
	}

	@Test
	public void pathOptionsAlsoAcceptCommaSeparatedFields() throws Exception
	{
		WebQuery query = mapper.readValue("{\"dbfetch\":\"children,owner\",\"expand\":\"children,owner\"}", WebQuery.class);
		assertEquals(Set.of("children", "owner"), query.getDBFetch());
		assertEquals(Set.of("children", "owner"), query.getExpand());
	}

	@Test
	public void emptyPathArraysExplicitlyDisableFetchingAndExpansion() throws Exception
	{
		WebQuery query = mapper.readValue("{\"dbfetch\":[],\"expand\":[]}", WebQuery.class);
		assertEquals(Set.of("none"), query.getDBFetch());
		assertEquals(Set.of("none"), query.getExpand());
		WebQuery roundTrip = mapper.readValue(mapper.writeValueAsString(query), WebQuery.class);
		assertEquals(query.getDBFetch(), roundTrip.getDBFetch());
		assertEquals(query.getExpand(), roundTrip.getExpand());
	}

	@Test
	public void nullPathOptionsRetainDefaultBehaviour() throws Exception
	{
		WebQuery query = mapper.readValue("{\"dbfetch\":null,\"expand\":null}", WebQuery.class);
		assertNull(query.getDBFetch());
		assertNull(query.getExpand());
	}

	@Test
	public void trimsPathsConsistentlyInStringAndArrayForms() throws Exception
	{
		WebQuery query = mapper.readValue("{\"dbfetch\":\" children , owner \",\"expand\":[\" children \",\" owner \"]}", WebQuery.class);
		assertEquals(Set.of("children", "owner"), query.getDBFetch());
		assertEquals(Set.of("children", "owner"), query.getExpand());
	}

	@Test
	public void rejectsBlankPathElementsInBothForms() throws Exception
	{
		for (String input : new String[]{"\"\"", "\" \"", "\"children,,owner\"", "\"children,\"", "[\"\"]", "[\"children\",\" \"]", "[\"children,\"]"})
		{
			for (String property : new String[]{"dbfetch", "expand"})
			{
				try
				{
					mapper.readValue("{\"" + property + "\":" + input + "}", WebQuery.class);
					fail("Expected rejection for " + property + "=" + input);
				}
				catch (JsonMappingException expected)
				{
					assertTrue(expected.getMessage().contains("blank elements"));
				}
			}
		}
	}

	@Test(expected = JsonMappingException.class)
	public void rejectsConstraintObjectsWithoutAnIdentifiableShape() throws Exception
	{
		mapper.readValue("{\"constraints\":{\"constraints\":[{}]}}", WebQuery.class);
	}

	@Test(expected = JsonMappingException.class)
	public void rejectsNonStringPathElements() throws Exception
	{
		mapper.readValue("{\"dbfetch\":[\"children\",42]}", WebQuery.class);
	}
}
