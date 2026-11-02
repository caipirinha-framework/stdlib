package com.peterphi.std.guice.restclient.jaxb.webquery;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Defines a group constraint combining approach
 */
@XmlEnum
@XmlType(name = "CombiningOperatorType")
public enum WQGroupType
{
	AND,
	OR,
	/**
	 * NOT implemented as an OR group with a NOT around it
	 */
	NONE;
}
