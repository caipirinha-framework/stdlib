package com.peterphi.std.guice.hibernate.entitycollection;

import com.google.common.base.MoreObjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class AlternateIdEmbeddedEntity
{
	private String identifierSystem;
	private String value;


	public AlternateIdEmbeddedEntity()
	{
	}


	public AlternateIdEmbeddedEntity(final String identifierSystem, final String value)
	{
		this.identifierSystem = identifierSystem;
		this.value = value;
	}


	@Column(name = "id_system", nullable = false)
	public String getIdentifierSystem()
	{
		return identifierSystem;
	}


	public void setIdentifierSystem(final String identifierSystem)
	{
		this.identifierSystem = identifierSystem;
	}


	@Column(name = "id_value", nullable = false)
	public String getValue()
	{
		return value;
	}


	public void setValue(final String value)
	{
		this.value = value;
	}


	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		AlternateIdEmbeddedEntity that = (AlternateIdEmbeddedEntity) o;

		if (!identifierSystem.equals(that.identifierSystem))
			return false;
		return value.equals(that.value);
	}


	@Override
	public int hashCode()
	{
		int result = identifierSystem.hashCode();
		result = 31 * result + value.hashCode();
		return result;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("identifierSystem", identifierSystem).add("value", value).toString();
	}
}
