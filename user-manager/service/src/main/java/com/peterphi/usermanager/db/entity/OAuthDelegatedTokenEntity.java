package com.peterphi.usermanager.db.entity;

import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.types.SimpleId;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity(name = "delegated_token")
public class OAuthDelegatedTokenEntity
{
	private String id = SimpleId.alphanumeric(IDPrefix.OAUTH_DELEGATED_TOKEN, 36);
	private OAuthSessionEntity session;
	private DateTime created = DateTime.now();
	private DateTime expires;


	@Id
	@Column(name = "id", length = 36)
	public String getId()
	{
		return id;
	}


	public void setId(final String id)
	{
		this.id = id;
	}


	@EagerFetch
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "session_id", nullable = false)
	public OAuthSessionEntity getSession()
	{
		return session;
	}


	public void setSession(final OAuthSessionEntity session)
	{
		this.session = session;
	}


	@Column(name = "created_ts", nullable = false)
	public DateTime getCreated()
	{
		return created;
	}


	public void setCreated(final DateTime created)
	{
		this.created = created;
	}


	@Column(name = "expires_ts", nullable = false)
	public DateTime getExpires()
	{
		return expires;
	}


	public void setExpires(final DateTime expires)
	{
		this.expires = expires;
	}
}
