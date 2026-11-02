package com.peterphi.std.util.jaxb.pkg1;


import com.peterphi.std.util.jaxb.pkg2.SomeOtherXml;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="SomeXml")
public class SomeXml
{
	@XmlElement(required=true)
	public String name;

	@XmlElement(required=true)
	public SomeOtherXml other1;
	@XmlElement(required=true)
	public SomeOtherXml other2;
}
