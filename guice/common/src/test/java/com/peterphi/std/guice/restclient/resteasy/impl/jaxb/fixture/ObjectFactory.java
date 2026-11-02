package com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fixture;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.annotation.XmlRegistry;

import javax.xml.namespace.QName;

@XmlRegistry
public class ObjectFactory
{
	@XmlElementDecl(namespace = "urn:stdlib:transport-test", name = "payload")
	public JAXBElement<TypePayload> createPayload(TypePayload value)
	{
		return new JAXBElement<>(new QName("urn:stdlib:transport-test", "payload"), TypePayload.class, value);
	}
}
