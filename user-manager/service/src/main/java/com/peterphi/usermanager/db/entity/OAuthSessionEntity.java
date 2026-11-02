package com.peterphi.usermanager.db.entity;

import com.peterphi.std.guice.database.annotation.EagerFetch;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

@Entity(name = "oauth_session")
public class OAuthSessionEntity
{
	private String id;
	private OAuthSessionContextEntity context;

	// Details (e.g. IP address) for the initiator of this session
	private String initiator;

	private boolean alive = true;

	private String authorisationCode;
	private String token;

	private DateTime created = new DateTime();
	private DateTime updated = new DateTime();

	// When the current token expires
	private DateTime expires;


	@Id
	@Column(name = "id", length = 36)
	public String getId()
	{
		return id;
	}


	@EagerFetch
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "context_id", nullable = false)
	public OAuthSessionContextEntity getContext()
	{
		return context;
	}


	@Column(name = "initiator_detail", length = 8192, nullable = true)
	public String getInitiator()
	{
		return initiator;
	}


	@Column(name = "is_active", nullable = false)
	public boolean isAlive()
	{
		return alive;
	}


	@Column(name = "authorisation_code", length = 36, nullable = true)
	public String getAuthorisationCode()
	{
		return authorisationCode;
	}


	@Column(name = "current_token", length = 36, nullable = true)
	public String getToken()
	{
		return token;
	}


	@Column(name = "expires_ts", nullable = false)
	public DateTime getExpires()
	{
		return expires;
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


	public void setId(final String id)
	{
		this.id = id;
	}


	public void setContext(final OAuthSessionContextEntity context)
	{
		this.context = context;
	}


	public void setInitiator(final String initiator)
	{
		this.initiator = initiator;
	}


	public void setAlive(final boolean alive)
	{
		this.alive = alive;
	}


	public void setAuthorisationCode(final String authorisationCode)
	{
		this.authorisationCode = authorisationCode;
	}


	public void setToken(final String token)
	{
		this.token = token;
	}


	public void setExpires(final DateTime expires)
	{
		this.expires = expires;
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
