package com.peterphi.usermanager.db.entity;

import com.google.common.base.MoreObjects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity(name = "role_definition")
public class RoleEntity
{
	private String id;
	private String caption;

	private List<UserEntity> members = new ArrayList<>();
	private Set<OAuthServiceEntity> serviceMembers = new HashSet<>();

	@Id
	@Column(name = "id", length = 255, nullable = false)
	public String getId()
	{
		return id;
	}


	public void setId(final String id)
	{
		this.id = id;
	}


	@Column(name = "caption", length = 1024, nullable = false)
	public String getCaption()
	{
		return caption;
	}


	public void setCaption(final String caption)
	{
		this.caption = caption;
	}


	@OrderBy("id DESC")
	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "user_has_role", //
			joinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", nullable = false)}, inverseJoinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)}, uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id",
			                                                                                                                                                                                                                                                                                               "role_id"})})
	public List<UserEntity> getMembers()
	{
		return members;
	}


	public void setMembers(final List<UserEntity> members)
	{
		this.members = members;
	}


	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name = "service_has_role", //
			joinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", nullable = false)}, inverseJoinColumns = {
			@JoinColumn(name = "service_id", referencedColumnName = "id", nullable = false)}, uniqueConstraints = {
			@UniqueConstraint(columnNames = {"service_id", "role_id"})})
	public Set<OAuthServiceEntity> getServiceMembers()
	{
		return this.serviceMembers;
	}


	public void setServiceMembers(final Set<OAuthServiceEntity> serviceMembers)
	{
		this.serviceMembers = serviceMembers;
	}


	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this).add("id", id).add("caption", caption).toString();
	}
}
