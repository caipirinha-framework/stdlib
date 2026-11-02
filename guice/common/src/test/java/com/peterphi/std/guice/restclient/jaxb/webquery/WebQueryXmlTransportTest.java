package com.peterphi.std.guice.restclient.jaxb.webquery;

import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlRootElementProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset.FastInfosetXmlRootElementProvider;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;

import static org.junit.Assert.assertEquals;

public class WebQueryXmlTransportTest
{
	@Test
	public void nestedConstraintsStillRoundTripThroughXml() throws Exception
	{
		roundTrip(false);
	}

	@Test
	public void nestedConstraintsStillRoundTripThroughFastInfoset() throws Exception
	{
		roundTrip(true);
	}

	private void roundTrip(boolean fastInfoset) throws Exception
	{
		WebQuery query = new WebQuery().eq("name", "test")
		                               .or(group -> group.in("id", 1, 2).add(WQGroup.newNone().eq("deleted", true)))
		                               .limit(3).offset(2).computeSize(true).orderAsc("name");
		query.constraints.add(WQGroup.newAnd());
		JAXBSerialiserFactory factory = new JAXBSerialiserFactory(true);
		JAXBXmlRootElementProvider<WebQuery> provider = fastInfoset ?
				new FastInfosetXmlRootElementProvider<>(factory) : new JAXBXmlRootElementProvider<>(factory);
		MediaType mediaType = fastInfoset ? MediaType.valueOf("application/fastinfoset") : MediaType.APPLICATION_XML_TYPE;
		Annotation[] annotations = new Annotation[0];
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		provider.writeTo(query, WebQuery.class, null, annotations, mediaType, new MultivaluedHashMap<>(), bytes);
		WebQuery decoded = provider.readFrom(WebQuery.class, null, annotations, mediaType,
		                                    new MultivaluedHashMap<>(), new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(query.toQueryFragment(), decoded.toQueryFragment());
		assertEquals(query.getLimit(), decoded.getLimit());
		assertEquals(query.getOffset(), decoded.getOffset());
		assertEquals(query.isComputeSize(), decoded.isComputeSize());
	}
}
