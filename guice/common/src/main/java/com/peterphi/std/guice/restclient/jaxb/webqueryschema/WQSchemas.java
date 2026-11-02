package com.peterphi.std.guice.restclient.jaxb.webqueryschema;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "Schemas")
public class WQSchemas
{
	@XmlElement(name = "entity")
	public List<WQEntitySchema> entities = new ArrayList<>();
}
