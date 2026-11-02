package com.peterphi.usermanager.db.entity;

import com.peterphi.std.guice.database.annotation.EagerFetch;
import com.peterphi.std.types.SimpleId;
import org.joda.time.DateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity(name = "oauth_service")
public class OAuthServiceEntity
{
	private String id = SimpleId.alphanumeric(IDPrefix.OAUTH_SERVICE, 36);
	private UserEntity owner;
	private String name;
	private String endpoints;
	private String clientSecret = SimpleId.alphanumeric(IDPrefix.OAUTH_SERVICE_SECRET, 36);
	private String requiredRoleName;
	private Set<RoleEntity> roles = new HashSet<>();

	/**
	 * The primary access key (the key all API clients should use for this account). Optional.
	 */
	private String accessKey;

	/**
	 * The secondary access key (allows keys to be rotated out without a big-bang reconfiguration of API users). Optional.
	 */
	private String accessKeySecondary;

	private boolean enabled = true;
	private DateTime created = new DateTime();
	private DateTime updated = new DateTime();


	@Id
	@Column(name = "id", length = 36)
	public String getId()
	{
		return id;
	}


	@EagerFetch
	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	public UserEntity getOwner()
	{
		return owner;
	}


	@Column(name = "client_name", length = 1000, nullable = false)
	public String getName()
	{
		return name;
	}


	@Column(name = "client_endpoints", length = 2000, nullable = false)
	public String getEndpoints()
	{
		return endpoints;
	}


	@Column(name = "client_secret", length = 36, nullable = false)
	public String getClientSecret()
	{
		return clientSecret;
	}


	@Column(name = "required_role_id", nullable = true, length = 4096)
	public String getRequiredRoleName()
	{
		return requiredRoleName;
	}


	@ManyToMany(mappedBy = "serviceMembers", cascade = CascadeType.ALL)
	public Set<RoleEntity> getRoles()
	{
		return roles;
	}


	/**
	 * Access key, allows a User Manager Service to authenticate easily against another service.
	 *
	 * @return
	 */
	@Column(name = "access_key", nullable = true, length = 100)
	public String getAccessKey()
	{
		return accessKey;
	}


	/**
	 * Access key (secondary key - due for retirement, the next rotation will remove this key, put the primary key in its place and generate a new primary key), allows a User Manager Service to authenticate easily against another service.
	 *
	 * @return
	 */
	@Column(name = "access_key_alt", nullable = true, length = 100)
	public String getAccessKeySecondary()
	{
		return accessKeySecondary;
	}


	@Column(name = "is_enabled", nullable = false)
	public boolean isEnabled()
	{
		return enabled;
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


	public void setOwner(final UserEntity owner)
	{
		this.owner = owner;
	}


	public void setName(final String name)
	{
		this.name = name;
	}


	public void setEndpoints(final String endpoints)
	{
		this.endpoints = endpoints;
	}


	public void setClientSecret(final String clientSecret)
	{
		this.clientSecret = clientSecret;
	}


	public void setRequiredRoleName(final String requiredRoleName)
	{
		this.requiredRoleName = requiredRoleName;
	}


	public void setRoles(final Set<RoleEntity> roles)
	{
		this.roles = roles;
	}


	public void setAccessKey(final String accessKey)
	{
		this.accessKey = accessKey;
	}


	public void setAccessKeySecondary(final String accessKeySecondary)
	{
		this.accessKeySecondary = accessKeySecondary;
	}


	public void setEnabled(final boolean enabled)
	{
		this.enabled = enabled;
	}


	public void setCreated(final DateTime created)
	{
		this.created = created;
	}


	public void setUpdated(final DateTime updated)
	{
		this.updated = updated;
	}

	//
	// Helpers
	//


	public List<String> endpointsAsList()
	{
		return Arrays.asList(getEndpoints().split("\n"));
	}
}
