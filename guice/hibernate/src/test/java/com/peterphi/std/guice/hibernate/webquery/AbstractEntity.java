package com.peterphi.std.guice.hibernate.webquery;


import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.joda.time.DateTime;

@MappedSuperclass
abstract class AbstractEntity<E extends Enum>
{
	private DateTime created = DateTime.now();
	private DateTime updated = DateTime.now();

	@Column(name="state_val")
	abstract E getState();


	abstract void setState(final E state);


	@Column(name = "created_ts", nullable = false)
	public DateTime getCreated()
	{
		return created;
	}


	public void setCreated(final DateTime created)
	{
		this.created = created;
	}


	@Version
	@Column(name = "updated_ts", nullable = false)
	public DateTime getUpdated()
	{
		return updated;
	}


	public void setUpdated(final DateTime updated)
	{
		this.updated = updated;
	}
}
