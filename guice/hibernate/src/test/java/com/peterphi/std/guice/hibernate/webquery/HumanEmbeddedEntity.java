package com.peterphi.std.guice.hibernate.webquery;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class HumanEmbeddedEntity
{
	private String sortName;
	private String firstName;
	private String lastName;
	private String fullName;


	public HumanEmbeddedEntity()
	{
	}


	public HumanEmbeddedEntity(final String sortName, final String firstName, final String lastName, final String fullName)
	{
		this.sortName = sortName;
		this.firstName = firstName;
		this.lastName = lastName;
		this.fullName = fullName;
	}


	@Column(name = "sort_name")
	public String getSortName()
	{
		return sortName;
	}


	public void setSortName(final String sortName)
	{
		this.sortName = sortName;
	}


	@Column(name = "first_name")
	public String getFirstName()
	{
		return firstName;
	}


	public void setFirstName(final String firstName)
	{
		this.firstName = firstName;
	}


	@Column(name = "last_name")
	public String getLastName()
	{
		return lastName;
	}


	public void setLastName(final String lastName)
	{
		this.lastName = lastName;
	}


	@Column(name = "full_name")
	public String getFullName()
	{
		return fullName;
	}


	public void setFullName(final String fullName)
	{
		this.fullName = fullName;
	}
}
