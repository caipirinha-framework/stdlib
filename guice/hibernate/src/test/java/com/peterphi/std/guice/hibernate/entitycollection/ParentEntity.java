package com.peterphi.std.guice.hibernate.entitycollection;

import com.google.common.base.MoreObjects;
import com.peterphi.std.guice.database.annotation.EagerFetch;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.HashSet;
import java.util.Set;

@Entity(name = "parent_entity")
class ParentEntity
{
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@Column(nullable = true)
	private Integer capacity;

	@EagerFetch
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parent")
	private Set<ChildEntity> children = new HashSet<>();


	public Long getId()
	{
		return id;
	}


	public void setId(final Long id)
	{
		this.id = id;
	}


	public Integer getCapacity()
	{
		return capacity;
	}


	public void setCapacity(final Integer capacity)
	{
		this.capacity = capacity;
	}


	public Set<ChildEntity> getChildren()
	{
		return children;
	}


	public void setChildren(final Set<ChildEntity> children)
	{
		this.children = children;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("id", id).toString();
	}
}
