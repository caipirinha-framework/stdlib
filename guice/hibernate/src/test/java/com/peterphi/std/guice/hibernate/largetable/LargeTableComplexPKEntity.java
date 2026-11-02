package com.peterphi.std.guice.hibernate.largetable;

import com.peterphi.std.guice.database.annotation.LargeTable;
import com.peterphi.std.types.SimpleId;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@LargeTable
@Entity(name = "complex_pk_entity")
 class LargeTableComplexPKEntity
{
	@EmbeddedId
	public SomePrimaryKey id;

	@Column(nullable = false)
	public String name;


	public LargeTableComplexPKEntity()
	{
	}


	public LargeTableComplexPKEntity(final String name)
	{
		this.id = new SomePrimaryKey(SimpleId.alphanumeric(20), DateTime.now().getMillis());
		this.name = name;
	}


	@Override
	public String toString()
	{
		return "LargeTableComplexPKEntity{" +
		       "id=" + id +
		       ", name='" + name + '\'' +
		       '}';
	}
}
