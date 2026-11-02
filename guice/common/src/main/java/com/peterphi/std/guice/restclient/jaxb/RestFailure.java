package com.peterphi.std.guice.restclient.jaxb;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAnyElement;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Wire type for the common REST exception representation
 */
@XmlRootElement(name = "failure")
public class RestFailure
{
	/**
	 * The unique id assigned to this exception
	 */
	@XmlAttribute(required = true, name = "id")
	public String id;

	@XmlAttribute(required = true, name = "date")
	public Date date;

	/**
	 * The throwing server and/or application
	 */
	@XmlAttribute(required = false, name = "source")
	public String source = "unknown";

	/**
	 * The numeric code associated with this exception
	 */
	@XmlElement(required = false, name = "errorCode")
	public long errorCode = 0;

	/**
	 * The HTTP code associated with this exception
	 */
	@XmlElement(required = false, name = "httpCode")
	public int httpCode = 0;

	@XmlElement(required = true, name = "exception")
	public ExceptionInfo exception;

	@XmlAnyElement
	public List<JAXBElement<?>> any = new ArrayList<JAXBElement<?>>();
}
