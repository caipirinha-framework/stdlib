package com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset;

import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlTypeProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fixture.ObjectFactory;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fixture.TypePayload;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.xml.bind.JAXBElement;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FastInfosetJAXBElementTest
{
	private static final Annotation[] ANNOTATIONS = new Annotation[0];
	private static final MediaType FAST_INFOSET = MediaType.valueOf("application/fastinfoset");
	private static JAXBElement<TypePayload> genericElement;
	private final JAXBSerialiserFactory factory = new JAXBSerialiserFactory(true);

	@Test
	public void preservesElementWrapperAndQNameLikeXml() throws Exception
	{
		TypePayload value = new TypePayload();
		value.value = "hello";
		JAXBElement<TypePayload> original = new ObjectFactory().createPayload(value);

		for (boolean fastInfoset : new boolean[]{false, true})
		{
			JAXBElement<?> decoded = roundTrip(original, fastInfoset);
			assertEquals(original.getName(), decoded.getName());
			assertEquals(TypePayload.class, decoded.getDeclaredType());
			assertEquals("hello", ((TypePayload) decoded.getValue()).value);
		}
	}

	@Test
	public void preservesNilElementWrapper() throws Exception
	{
		JAXBElement<TypePayload> original = new ObjectFactory().createPayload(null);
		for (boolean fastInfoset : new boolean[]{false, true})
		{
			JAXBElement<?> decoded = roundTrip(original, fastInfoset);
			assertTrue(decoded.isNil());
			assertEquals(original.getName(), decoded.getName());
			if (decoded.getValue() != null)
				assertNull(((TypePayload) decoded.getValue()).value);
		}
	}

	@Test
	public void stillUnwrapsWhenPayloadClassIsRequested() throws Exception
	{
		JAXBXmlTypeProvider<TypePayload> provider = new FastInfosetXmlTypeProvider<>(new JAXBXmlTypeProvider<>(factory));
		TypePayload value = new TypePayload();
		value.value = "payload";
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		provider.writeTo(value, TypePayload.class, null, ANNOTATIONS, FAST_INFOSET, new MultivaluedHashMap<>(), bytes);
		TypePayload decoded = provider.readFrom(TypePayload.class, null, ANNOTATIONS, FAST_INFOSET,
		                                       new MultivaluedHashMap<>(), new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals("payload", decoded.value);
	}

	private JAXBElement<?> roundTrip(JAXBElement<TypePayload> value, boolean fastInfoset) throws Exception
	{
		JAXBXmlTypeProvider<JAXBElement> xml = new JAXBXmlTypeProvider<>(factory);
		JAXBXmlTypeProvider<JAXBElement> provider = fastInfoset ? new FastInfosetXmlTypeProvider<>(xml) : xml;
		MediaType mediaType = fastInfoset ? FAST_INFOSET : MediaType.APPLICATION_XML_TYPE;
		Type type = FastInfosetJAXBElementTest.class.getDeclaredField("genericElement").getGenericType();
		assertTrue(provider.isReadable(JAXBElement.class, type, ANNOTATIONS, mediaType));
		assertTrue(provider.isWriteable(JAXBElement.class, type, ANNOTATIONS, mediaType));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		provider.writeTo(value, JAXBElement.class, type, ANNOTATIONS, mediaType, new MultivaluedHashMap<>(), bytes);
		return provider.readFrom(JAXBElement.class, type, ANNOTATIONS, mediaType,
		                         new MultivaluedHashMap<>(), new ByteArrayInputStream(bytes.toByteArray()));
	}
}
