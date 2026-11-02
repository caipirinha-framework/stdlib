package com.peterphi.std.guice.hibernate.webquery;

import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.guice.database.annotation.WebQueryPrivate;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
public class ParentEntity
{
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@Column(name = "obj_name")
	private String name;

	@Column(name = "deprecated")
	private boolean deprecated = false;

	@WebQueryPrivate
	@Column(name = "secret_col")
	private String somePrivateString = UUID.randomUUID().toString();

	@Column(name = "someBytes")
	@Lob
	private byte[] someBytes = "some bytes".getBytes();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "other_object_id")
	private ChildEntity otherObject;

	@OneToMany(mappedBy = "parent")
	private Set<ChildEntity> children;

	@EagerFetch
	@ElementCollection
	@CollectionTable(name = "parent_friends", joinColumns = @JoinColumn(name = "parent_id"))
	private Set<HumanEmbeddedEntity> friends = new HashSet<>();


	public Long getId()
	{
		return id;
	}


	public void setId(final Long id)
	{
		this.id = id;
	}


	public String getName()
	{
		return name;
	}


	public void setName(final String name)
	{
		this.name = name;
	}


	boolean isDeprecated()
	{
		return deprecated;
	}


	void setDeprecated(final boolean deprecated)
	{
		this.deprecated = deprecated;
	}


	public String getSomePrivateString()
	{
		return somePrivateString;
	}


	public void setSomePrivateString(final String somePrivateString)
	{
		this.somePrivateString = somePrivateString;
	}


	public byte[] getSomeBytes()
	{
		return someBytes;
	}


	public void setSomeBytes(final byte[] someBytes)
	{
		this.someBytes = someBytes;
	}


	public ChildEntity getOtherObject()
	{
		return otherObject;
	}


	public void setOtherObject(final ChildEntity otherObject)
	{
		this.otherObject = otherObject;
	}


	public Set<ChildEntity> getChildren()
	{
		return children;
	}


	public void setChildren(final Set<ChildEntity> children)
	{
		this.children = children;
	}


	public Set<HumanEmbeddedEntity> getFriends()
	{
		return friends;
	}


	public void setFriends(final Set<HumanEmbeddedEntity> friends)
	{
		this.friends = friends;
	}
}
