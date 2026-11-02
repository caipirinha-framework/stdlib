package com.peterphi.std.guice.liquibase.hibernate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "something")
public class SomeEntity
{
	@Id
	public long id;

	@Column
	public String name;
}
