package com.peterphi.usermanager.db.entity;

import com.peterphi.std.guice.database.annotation.EagerFetch;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

/**
 * Indicates a user has used a Service with a given Scope (and allows a user to revoke access to a Service at any time)
 */
@Entity(name = "oauth_session_context")
public class OAuthSessionContextEntity
{
	private Integer id;
	private UserEntity user;
	private OAuthServiceEntity service;
	private String scope;
	private boolean active = true;
	private DateTime created = new DateTime();
	private DateTime updated = new DateTime();


	@Id
	@GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
	public Integer getId()
	{
		return id;
	}


	@EagerFetch
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	public UserEntity getUser()
	{
		return user;
	}


	@EagerFetch
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "service_id", nullable = false)
	public OAuthServiceEntity getService()
	{
		return service;
	}


	@Column(name = "approved_scope", length = 1000, nullable = false)
	public String getScope()
	{
		return scope;
	}


	@Column(name = "is_active", nullable = false)
	public boolean isActive()
	{
		return active;
	}


	@Column(name = "created_ts", nullable = false)
	public DateTime getCreated()
	{
		return created;
	}


	@Version
	@Column(name = "updated_ts", nullable = false)
	public DateTime getUpdated()
	{
		return updated;
	}


	public void setId(final Integer id)
	{
		this.id = id;
	}


	public void setUser(final UserEntity user)
	{
		this.user = user;
	}


	public void setService(final OAuthServiceEntity service)
	{
		this.service = service;
	}


	public void setScope(final String scope)
	{
		this.scope = scope;
	}


	public void setActive(final boolean active)
	{
		this.active = active;
	}


	public void setCreated(final DateTime created)
	{
		this.created = created;
	}


	public void setUpdated(final DateTime updated)
	{
		this.updated = updated;
	}
}
