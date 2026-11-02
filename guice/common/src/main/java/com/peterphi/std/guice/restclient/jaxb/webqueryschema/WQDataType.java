package com.peterphi.std.guice.restclient.jaxb.webqueryschema;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlEnum
@XmlType(name = "DataType")
public enum WQDataType
{
	ENTITY,
	STRING,
	NUMERIC,
	BOOL,
	DATETIME,
	UUID,
	BLOB,
	ENUM,
	TIMECODE,
	SAMPLE_COUNT;
}
