package com.peterphi.std.guice.hibernatetest;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Entity
class SimpleEntity
{
	@Id
	public long id;
	@Column
	public String name;

	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "simple_entity_join_table",
	           joinColumns = @JoinColumn(name = "simple_id", referencedColumnName = "id", nullable = false),
	           inverseJoinColumns = @JoinColumn(name = "group_id",
	                                            referencedColumnName = "id",
	                                            nullable = false))
	private Set<GroupEntity> events = new HashSet<>();


	public SimpleEntity()
	{
	}


	SimpleEntity(final long id, final String name, GroupEntity... groups)
	{
		this.id = id;
		this.name = name;

		this.events.addAll(Arrays.asList(groups));
	}
}
