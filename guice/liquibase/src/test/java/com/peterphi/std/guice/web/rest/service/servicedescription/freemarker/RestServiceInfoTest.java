package com.peterphi.std.guice.web.rest.service.servicedescription.freemarker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestServiceInfoTest
{
	@Test
	public void testGetCommonPaths()
	{
		assertEquals(Arrays.asList("/xyz/activity", "/xyz/activities"),
		             RestServiceInfo.getCommonPaths("/xyz", "/activity/{id}", "/activity/{id}/xyz", "/activities"));
	}
}
