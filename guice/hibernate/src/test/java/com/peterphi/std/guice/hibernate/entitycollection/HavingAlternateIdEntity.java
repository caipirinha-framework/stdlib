package com.peterphi.std.guice.hibernate.entitycollection;

import com.google.common.base.MoreObjects;
import com.peterphi.std.guice.database.annotation.EagerFetch;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import java.util.HashSet;
import java.util.Set;

@Entity(name = "having_altid")
public class HavingAlternateIdEntity
{
	private Long id;
	private Set<AlternateIdEmbeddedEntity> alternateIds = new HashSet<>();


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId()
	{
		return id;
	}


	public void setId(final Long id)
	{
		this.id = id;
	}


	@EagerFetch
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "asset_alternate_id", joinColumns = @JoinColumn(name = "asset_id"))
	public Set<AlternateIdEmbeddedEntity> getAlternateIds()
	{
		return alternateIds;
	}


	public void setAlternateIds(final Set<AlternateIdEmbeddedEntity> alternateIds)
	{
		this.alternateIds = alternateIds;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("id", id).add("alternateIds", alternateIds).toString();
	}
}
