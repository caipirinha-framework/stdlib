package com.peterphi.usermanager.rest.type;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@XmlRootElement(name = "user")
public class UserManagerUser
{
	@XmlAttribute(required = true)
	public Integer id;

	@XmlAttribute
	public boolean delegated = false;

	/**
	 * True if this is a service user
	 */
	@XmlAttribute
	public boolean service = false;

	/**
	 * User's real name
	 */
	@XmlElement
	public String name;

	/**
	 * User's e-mail address
	 */
	@XmlElement
	public String email;

	/**
	 * The user's desired date format
	 */
	@XmlElement
	public String dateFormat;

	/**
	 * The user's desired timezone
	 */
	@XmlElement
	public String timeZone;

	@XmlElementWrapper(name = "roles")
	@XmlElement(name = "role")
	public Set<String> roles = new HashSet<>();

	@XmlElement
	public Date created;

	/**
	 * If delegated, the origin service who is acting on behalf of the user
	 */
	@XmlElement
	public String originService;

	/**
	 * Convert the dateFormat and timeZone to a DateTimeFormatter
	 *
	 * @return
	 */
	public DateTimeFormatter toDateTimeFormatter()
	{
		return DateTimeFormat.forPattern(dateFormat).withZone(DateTimeZone.forID(timeZone));
	}
}
