package com.peterphi.std.util.jaxb.pkg2;

import org.w3c.dom.Element;

import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
@XmlRootElement
public class SomeOtherXml
{
	@XmlAnyElement(lax = false)
	public Element someXmlBlock;
}
