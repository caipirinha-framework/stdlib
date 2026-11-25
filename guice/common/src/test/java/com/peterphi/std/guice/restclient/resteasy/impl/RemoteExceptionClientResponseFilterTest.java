package com.peterphi.std.guice.restclient.resteasy.impl;

import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import com.peterphi.std.util.jaxb.JAXBSerialiser;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RemoteExceptionClientResponseFilterTest
{
	@Test
	public void testParseExceptionXml() throws IOException
	{
		RestFailure f = new RestFailure();
		f.id = UUID.randomUUID().toString();

		final ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
		JAXBSerialiser.getInstance(RestFailure.class).serialise(f, bos);

		RestFailure ret = RemoteExceptionClientResponseFilter.parseResponse(new JAXBSerialiserFactory(true),
		                                                                    new ByteArrayInputStream(bos.toByteArray()));

		assertEquals(f.id, ret.id);
	}
}
