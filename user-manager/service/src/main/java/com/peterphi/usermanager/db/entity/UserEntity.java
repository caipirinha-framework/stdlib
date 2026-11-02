package com.peterphi.usermanager.db.entity;

import org.hibernate.annotations.NaturalId;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "user_account")
public class UserEntity
{
	private Integer id;

	private boolean local = true;

	/**
	 * User's real name
	 */
	private String name;

	/**
	 * User's e-mail address
	 */
	private String email;

	/**
	 * BCrypted password
	 */
	private String password;

	/**
	 * The primary access key (the key all API clients should use for this account). Optional.
	 */
	private String accessKey;

	/**
	 * The secondary access key (allows keys to be rotated out without a big-bang reconfiguration of API users). Optional.
	 */
	private String accessKeySecondary;

	/**
	 * The user's desired date format
	 */
	private String dateFormat;

	/**
	 * The user's desired timezone
	 */
	private String timeZone;

	/**
	 * A key that, if present in a cookie, will allow a login session to be automatically re-established
	 */
	private String sessionReconnectKey;

	private DateTime created = DateTime.now();

	private DateTime lastLogin;

	private List<RoleEntity> roles = new ArrayList<>(0);


	@Id
	@GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
	public Integer getId()
	{
		return id;
	}


	public void setId(Integer id)
	{
		this.id = id;
	}


	@Column(name = "is_local", nullable = false)
	public boolean isLocal()
	{
		return local;
	}


	public void setLocal(final boolean local)
	{
		this.local = local;
	}


	@Column(length = 100, nullable = false)
	public String getName()
	{
		return name;
	}


	public void setName(String name)
	{
		this.name = name;
	}


	@NaturalId(mutable = true)
	@Column(length = 200, nullable = false)
	public String getEmail()
	{
		return email;
	}


	public void setEmail(String email)
	{
		this.email = email;
	}


	@Column(length = 60, nullable = false)
	public String getPassword()
	{
		return password;
	}


	public void setPassword(String password)
	{
		this.password = password;
	}


	/**
	 * Access key, allows API clients more easy use of a User Manager User.
	 *
	 * @return
	 */
	@Column(name = "access_key", nullable = true, length = 100)
	public String getAccessKey()
	{
		return accessKey;
	}


	public void setAccessKey(final String accessKey)
	{
		this.accessKey = accessKey;
	}


	/**
	 * Access key, allows API clients more easy use of a User Manager User.
	 *
	 * @return
	 */
	@Column(name = "access_key_alt", nullable = true, length = 100)
	public String getAccessKeySecondary()
	{
		return accessKeySecondary;
	}


	public void setAccessKeySecondary(final String accessKeySecondary)
	{
		this.accessKeySecondary = accessKeySecondary;
	}


	@Column(name = "local_session_id", nullable = true, length = 100)
	public String getSessionReconnectKey()
	{
		return sessionReconnectKey;
	}


	public void setSessionReconnectKey(String sessionReconnectKey)
	{
		this.sessionReconnectKey = sessionReconnectKey;
	}


	@Column(name = "created_ts", nullable = false)
	public DateTime getCreated()
	{
		return created;
	}


	public void setCreated(DateTime created)
	{
		this.created = created;
	}


	@Column(name = "last_login_ts", nullable = true)
	public DateTime getLastLogin()
	{
		return lastLogin;
	}


	public void setLastLogin(DateTime lastLogin)
	{
		this.lastLogin = lastLogin;
	}


	@Column(name = "date_format", nullable = false, length = 50)
	public String getDateFormat()
	{
		return dateFormat;
	}


	public void setDateFormat(String dateFormat)
	{
		this.dateFormat = dateFormat;
	}


	@Column(name = "date_timezone", nullable = false, length = 50)
	public String getTimeZone()
	{
		return timeZone;
	}


	public void setTimeZone(String timeZone)
	{
		this.timeZone = timeZone;
	}


	@ManyToMany(mappedBy = "members")
	public List<RoleEntity> getRoles()
	{
		return roles;
	}


	public void setRoles(final List<RoleEntity> roles)
	{
		this.roles = roles;
	}
}
