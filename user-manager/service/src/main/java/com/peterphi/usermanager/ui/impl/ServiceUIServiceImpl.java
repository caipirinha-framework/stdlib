package com.peterphi.usermanager.ui.impl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.webquery.ConstrainedResultSet;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.Templater;
import com.peterphi.usermanager.db.dao.hibernate.OAuthServiceDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.RoleDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.UserDaoImpl;
import com.peterphi.usermanager.db.entity.OAuthServiceEntity;
import com.peterphi.usermanager.db.entity.RoleEntity;
import com.peterphi.usermanager.db.entity.UserEntity;
import com.peterphi.usermanager.guice.authentication.UserLogin;
import com.peterphi.usermanager.guice.token.LowSecurityCSRFTokenStore;
import com.peterphi.usermanager.ui.api.ServiceUIService;
import org.apache.commons.lang.StringUtils;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@AuthConstraint(role = UserLogin.ROLE_ADMIN, comment = "Must be user manager admin to view/modify services")
public class ServiceUIServiceImpl implements ServiceUIService
{
	private static final String TOKEN_USE = "configui";

	@Inject
	Templater templater;

	@Inject
	OAuthServiceDaoImpl dao;

	@Inject
	RoleDaoImpl roleDao;

	@Inject
	UserDaoImpl userDao;

	@Inject
	Provider<UserLogin> userProvider;

	@Inject
	@Named(GuiceProperties.LOCAL_REST_SERVICES_ENDPOINT)
	URI localEndpoint;

	@Inject
	LowSecurityCSRFTokenStore tokenStore;


	@Override
	@Transactional(readOnly = true)
	public String getList(final UriInfo query)
	{
		final ConstrainedResultSet<OAuthServiceEntity> resultset = dao.findByUriQuery(new WebQuery().decode(query.getQueryParameters()));

		final TemplateCall call = templater.template("services");

		call.set("token", tokenStore.getValue(TOKEN_USE));
		call.set("resultset", resultset);
		call.set("entities", resultset.getList());

		return call.process();
	}


	@Override
	@Transactional(readOnly = true)
	public String get(final String id)
	{
		final OAuthServiceEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such service with client_id: " + id);

		final TemplateCall call = templater.template("service");

		final List<RoleEntity> roles = roleDao.find(new WebQuery().limit(0).orderAsc("id")).getList();

		call.set("token", tokenStore.getValue(TOKEN_USE));
		call.set("entity", entity);
		call.set("localEndpoint", localEndpoint);
		call.set("roles", roles);
		call.set("entityRoleIds", entity.getRoles().stream().map(r -> r.getId()).collect(Collectors.toSet()));

		return call.process();
	}


	@Override
	@Transactional
	public Response create(final String token,
	                       final String name,
	                       String requiredRole,
	                       final String endpoints,
	                       final List<String> roles)
	{
		tokenStore.validate(TOKEN_USE, token);

		if (!userProvider.get().isAdmin())
			throw new IllegalArgumentException("Only an admin can create a service!");

		final int userId = userProvider.get().getId();

		final UserEntity user = userDao.getById(userId);

		OAuthServiceEntity entity = new OAuthServiceEntity();
		entity.setOwner(user);
		entity.setName(name);
		entity.setRequiredRoleName(StringUtils.trimToNull(requiredRole));
		entity.setEndpoints(StringUtils.trimToNull(endpoints));
		entity.setEnabled(true);
		entity.setRoles(new HashSet<>(roleDao.getListById(roles)));

		entity = dao.merge(entity);

		return Response.seeOther(URI.create("/service/" + entity.getId())).build();
	}


	@Override
	@Transactional
	public Response disable(final String id, final String token)
	{
		tokenStore.validate(TOKEN_USE, token);

		final OAuthServiceEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such service with client_id: " + id);
		else if (!entity.isEnabled())
			throw new IllegalArgumentException("Cannot disable an already-disabled service: " + id);
		else if (!userProvider.get().isAdmin())
			throw new IllegalArgumentException("Only an admin can disable a service!");

		entity.setEnabled(false);

		dao.update(entity);

		return Response.seeOther(URI.create("/service/" + id)).build();
	}


	@Override
	@Transactional
	public Response edit(final String token,
	                     final String id,
	                     final String requiredRole,
	                     final String endpoints,
	                     final List<String> roles)
	{
		tokenStore.validate(TOKEN_USE, token);

		final OAuthServiceEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such service with client_id: " + id);
		else if (!entity.isEnabled())
			throw new IllegalArgumentException("Cannot set endpoints on disabled service: " + id);
		else if (!userProvider.get().isAdmin())
			throw new IllegalArgumentException("Only an admin can edit a service!");

		entity.setRequiredRoleName(StringUtils.trimToNull(requiredRole));
		entity.setEndpoints(endpoints);

		setRoles(entity, roles);

		dao.update(entity);

		return Response.seeOther(URI.create("/service/" + id)).build();
	}


	@Override
	@Transactional
	public Response rotateAccessKey(final String id, final String token)
	{
		tokenStore.validate(TOKEN_USE, token);

		final OAuthServiceEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such service with client_id: " + id);
		else if (!entity.isEnabled())
			throw new IllegalArgumentException("Cannot set endpoints on disabled service: " + id);
		else if (entity.getOwner().getId() != userProvider.get().getId() && !userProvider.get().isAdmin())
			throw new IllegalArgumentException("Only the owner or an admin can change access keys for a service!");

		// Change regular account settings
		dao.rotateUserAccessKey(entity);

		return Response.seeOther(URI.create("/service/" + entity.getId())).build();
	}


	private void setRoles(final OAuthServiceEntity service, final List<String> roles)
	{
		final Set<String> currentRoles = service.getRoles().stream().map(r -> r.getId()).collect(Collectors.toSet());

		// Role IDs to add
		final Set<String> addRoles = new HashSet<>(roles);
		addRoles.removeAll(currentRoles);

		// Role IDs to remove
		final Set<String> delRoles = new HashSet<>(currentRoles);
		delRoles.removeAll(roles);

		// Add roles as necessary
		if (addRoles.size() > 0)
		{
			for (String role : addRoles)
			{
				RoleEntity roleEntity = roleDao.getById(role);
				roleEntity.getServiceMembers().add(service);

				roleDao.update(roleEntity);
			}
		}

		// Remove roles as necessary
		if (delRoles.size() > 0)
		{
			for (String role : delRoles)
			{
				RoleEntity roleEntity = roleDao.getById(role);

				roleEntity.getServiceMembers().removeIf(u -> u.getId() == service.getId());

				roleDao.update(roleEntity);
			}
		}
	}
}
