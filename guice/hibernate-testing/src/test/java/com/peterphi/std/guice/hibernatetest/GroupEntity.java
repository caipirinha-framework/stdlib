package com.peterphi.std.guice.hibernatetest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class GroupEntity
{
	@Id
	public long id;


	public GroupEntity(final long id)
	{
		this.id = id;
	}
}
