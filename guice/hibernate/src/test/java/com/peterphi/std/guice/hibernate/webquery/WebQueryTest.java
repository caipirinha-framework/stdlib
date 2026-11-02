package com.peterphi.std.guice.hibernate.webquery;

import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WQFunctionType;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQueryParser;
import com.peterphi.std.guice.restclient.jaxb.webquery.plugin.WebQueryDecodePlugin;
import com.peterphi.std.guice.restclient.jaxb.webquery.plugin.WebQueryPresetPlugin;
import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.junit.Test;

import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WebQueryTest
{
	@Test(expected = IllegalArgumentException.class)
	public void testPluginBanning()
	{
		new WebQueryDecodePlugin.Builder().ban("password").build().handles("password");
	}


	@Test(expected = IllegalArgumentException.class)
	public void testPluginValidation()
	{
		new WebQueryDecodePlugin.Builder()
				.validate("_fetch", s -> !StringUtils.containsIgnoreCase(s, "password"))
				.build()
				.handles("_fetch", Arrays.asList("id,username,password"));
	}


	@Test
	public void testInversionIsConsistent()
	{
		for (WQFunctionType val : WQFunctionType.values())
		{
			final WQFunctionType inverted = val.invert();

			// If there is an inversion, the inversion of that should be this
			if (inverted != null)
				assertEquals("Expect inversion of " + inverted + " to be " + val, val, inverted.invert());
		}
	}


	@Test
	public void testPluginPreset()
	{
		Map<String, List<String>> qs = new LinkedHashMap<>();

		qs.put("preset", Arrays.asList("important", "terminated"));
		qs.put("created", Arrays.asList("_f_ge_today"));

		final WebQuery wq = new WebQuery().decode(qs,
		                                          new WebQueryDecodePlugin.Builder()
				                                          .with(new WebQueryPresetPlugin("preset")
						                                                .withAllowMultiple(true)
						                                                .withOption("running",
						                                                            q -> q.eq("state",
						                                                                      "QUEUED",
						                                                                      "RUNNING",
						                                                                      "PAUSED"))
						                                                .withOption("important", q -> q.ge("priority", "10"))
						                                                .withOption("terminated",
						                                                            q -> q.eq("state",
						                                                                      "FAILED",
						                                                                      "SUCCESS",
						                                                                      "CANCELLED")))
				                                          .build());

		assertEquals("priority >= 10\nAND (state = FAILED OR state = SUCCESS OR state = CANCELLED)\nAND created >= today",
		             wq.toQueryFragment());
	}


	@Test
	public void testEncodeResultStable()
	{
		HibernateDao dao;

		WebQuery query = new WebQuery()
				                 .orderDesc("id")
				                 .orderAsc("name")
				                 .orderAsc("name2")
				                 .limit(99)
				                 .computeSize(true)
				                 .or(g -> g.eq("id", 1).contains("id", "some_value"))
				                 .eq("name", "x");

		assertEquals(
				"{_compute_size=[true], _fetch=[entity], _order=[id desc, name asc, name2 asc], _limit=[99], name=[x], id=[1, _f_contains_some_value]}",
				query.encode().toString());
	}


	@Test
	public void testParseQueryStringToWebQuery()
	{
		WebQuery expected = new WebQuery().logSQL(true).eq("id", "123").offset(0).limit(200);
		WebQuery actual = new WebQuery().decode(new ResteasyUriInfo(URI.create("http://example.com?_log_sql=true&id=123")));

		assertEquals(expected.encode().toString(), actual.encode().toString());
	}


	@Test
	public void testParseComplexQueryStringToWebQuery()
	{
		WebQuery actual = new WebQuery().decode(new ResteasyUriInfo(URI.create("http://example.com/search?q=id+%3C+100+AND+id+%3E+200+AND+%28id+%3D+1+OR+id+%3D+2+OR+%28id+%3D+3+AND+id+%3D+4%29%29")));

		assertEquals("id < 100\nAND id > 200\nAND (id = 1 OR id = 2 OR (id = 3 AND id = 4))", actual.constraints.toQueryFragment());
	}


	/**
	 * Tests that a complex query that cannot easily be represented using the expanded Query String form is converted to a text
	 * query using q=...
	 */
	@Test
	public void testEncodeOfComplexQuery()
	{
		final UriBuilder ub = UriBuilder.fromUri(URI.create("/search"));

		for (Map.Entry<String, List<String>> kvp : WebQueryParser
				                                           .parse("id < 100 and id > 200 and (id=1 or id=2 or (id=3 and id=4))", new WebQuery())
				                                           .encode()
				                                           .entrySet())
		{
			if (!kvp.getKey().startsWith("_"))
			{
				ub.queryParam(kvp.getKey(), kvp.getValue().toArray(new String[0]));
			}
		}

		assertEquals("/search?q=id+%3C+100%0AAND+id+%3E+200%0AAND+%28id+%3D+1+OR+id+%3D+2+OR+%28id+%3D+3+AND+id+%3D+4%29%29", ub.build().toASCIIString());
	}
}
