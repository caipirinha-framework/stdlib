package com.peterphi.std.guice.hibernate.webquery;

import com.peterphi.std.guice.database.annotation.EagerFetch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
class ChildEntity extends AbstractEntity<SomeStateEnum>
{
	private Long id;

	private String name;

	private ParentEntity parent;

	private SomeStateEnum state;


	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Long getId()
	{
		return id;
	}


	public void setId(final Long id)
	{
		this.id = id;
	}


	@Override
	public SomeStateEnum getState()
	{
		return this.state;
	}


	@Override
	public void setState(SomeStateEnum v)
	{
		this.state = v;
	}


	@Column(name = "obj_name")
	public String getName()
	{
		return name;
	}


	public void setName(final String name)
	{
		this.name = name;
	}


	@EagerFetch
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	public ParentEntity getParent()
	{
		return parent;
	}


	public void setParent(final ParentEntity parent)
	{
		this.parent = parent;
	}
}
