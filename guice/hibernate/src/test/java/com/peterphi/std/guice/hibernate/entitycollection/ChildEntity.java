package com.peterphi.std.guice.hibernate.entitycollection;

import com.google.common.base.MoreObjects;
import com.peterphi.std.guice.database.annotation.EagerFetch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity(name = "child_entity")
class ChildEntity
{
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(nullable = false)
	private ParentEntity parent;

	@EagerFetch
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn
	private ParentEntity friend;

	@Column(nullable = false)
	private boolean flag;


	public Long getId()
	{
		return id;
	}


	public void setId(final Long id)
	{
		this.id = id;
	}


	public ParentEntity getParent()
	{
		return parent;
	}


	public void setParent(final ParentEntity parent)
	{
		this.parent = parent;
	}


	public ParentEntity getFriend()
	{
		return friend;
	}


	public void setFriend(final ParentEntity friend)
	{
		this.friend = friend;
	}


	public boolean isFlag()
	{
		return flag;
	}


	public void setFlag(final boolean flag)
	{
		this.flag = flag;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("id", id).toString();
	}
}
