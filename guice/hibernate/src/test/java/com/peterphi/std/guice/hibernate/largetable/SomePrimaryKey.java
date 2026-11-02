package com.peterphi.std.guice.hibernate.largetable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
class SomePrimaryKey implements Serializable
{
	private static final long serialVersionUID = -1;

	@Column(length = 20, name = "id")
	public String id;

	@Column(name = "ts")
	public long timestamp;


	public SomePrimaryKey()
	{
	}


	public SomePrimaryKey(String id, long timestamp)
	{
		this.id = id;
		this.timestamp = timestamp;
	}


	public String getId()
	{
		return id;
	}


	public void setId(final String id)
	{
		this.id = id;
	}


	public long getTimestamp()
	{
		return timestamp;
	}


	public void setTimestamp(final long timestamp)
	{
		this.timestamp = timestamp;
	}


	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		SomePrimaryKey that = (SomePrimaryKey) o;

		if (timestamp != that.timestamp)
			return false;
		return !(id != null ? !id.equals(that.id) : that.id != null);
	}


	@Override
	public int hashCode()
	{
		int result = id != null ? id.hashCode() : 0;
		result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
		return result;
	}


	@Override
	public String toString()
	{
		return "SomePrimaryKey{" +
		       "id='" + id + '\'' +
		       ", timestamp=" + timestamp +
		       '}';
	}
}
