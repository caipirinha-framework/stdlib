package com.peterphi.std.guice.hibernate.webquery;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity(name = "mapped_superclass_test_entity")
@BatchSize(size = 250)
public class MappedSuperclassEntity extends AbstractEntity<SomeStateEnum>
{
	private Long id;

	private String name;

	private ChildEntity child;

	private SomeStateEnum state;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	public Long getId()
	{
		return id;
	}


	@Column(name = "obj_name")
	public String getName()
	{
		return name;
	}


	public void setId(final Long id)
	{
		this.id = id;
	}


	public void setName(final String name)
	{
		this.name = name;
	}


	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "child_id")
	public ChildEntity getChild()
	{
		return child;
	}


	public void setChild(final ChildEntity child)
	{
		this.child = child;
	}


	@Override
	SomeStateEnum getState()
	{
		return this.state;
	}


	@Override
	void setState(final SomeStateEnum state)
	{
		this.state = state;
	}
}
