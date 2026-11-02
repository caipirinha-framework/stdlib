package com.peterphi.usermanager.ui.impl;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.webquery.ConstrainedResultSet;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.guice.web.rest.templating.TemplateCall;
import com.peterphi.std.guice.web.rest.templating.Templater;
import com.peterphi.usermanager.db.dao.hibernate.RoleDaoImpl;
import com.peterphi.usermanager.db.dao.hibernate.UserDaoImpl;
import com.peterphi.usermanager.db.entity.RoleEntity;
import com.peterphi.usermanager.db.entity.UserEntity;
import com.peterphi.usermanager.guice.authentication.UserLogin;
import com.peterphi.usermanager.guice.token.LowSecurityCSRFTokenStore;
import com.peterphi.usermanager.ui.api.RoleUIService;
import org.apache.commons.lang.StringUtils;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@AuthConstraint(role = UserLogin.ROLE_ADMIN, comment = "Must be user manager admin to view/modify roles")
public class RoleUIServiceImpl implements RoleUIService
{
	private static final String TOKEN_USE = "configui";

	@Inject
	Templater templater;

	@Inject
	RoleDaoImpl dao;

	@Inject
	UserDaoImpl userDao;

	@Inject
	LowSecurityCSRFTokenStore tokenStore;


	@Override
	@Transactional(readOnly = true)
	public String getRoles(UriInfo query)
	{
		ConstrainedResultSet<RoleEntity> resultset = dao.findByUriQuery(new WebQuery().orderAsc("id").decode(query));

		TemplateCall call = templater.template("roles");

		call.set("resultset", resultset);
		call.set("roles", resultset.getList());
		call.set("token", tokenStore.getValue(TOKEN_USE));

		return call.process();
	}


	@Override
	@Transactional(readOnly = true)
	public String get(final String id)
	{
		TemplateCall call = templater.template("role");

		final RoleEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such Role: " + id);

		call.set("entity", entity);
		call.set("allUsers", userDao.getAll());
		call.set("users", userDao.findByUriQuery(new WebQuery().eq("roles.id", id)).getList());
		call.set("token", tokenStore.getValue(TOKEN_USE));

		return call.process();
	}


	@Override
	@Transactional
	public Response create(final String id, final String token, final String caption)
	{
		tokenStore.validate(TOKEN_USE, token);

		if (dao.getById(id) != null)
			throw new IllegalArgumentException("Role with name already exists: " + id);

		RoleEntity entity = new RoleEntity();

		entity.setId(id);
		entity.setCaption(caption);

		dao.persist(entity);

		return Response.seeOther(URI.create("/role/" + id)).build();
	}


	@Override
	@Transactional
	public Response delete(final String id, final String token)
	{
		tokenStore.validate(TOKEN_USE, token);

		if (StringUtils.equalsIgnoreCase(id, UserLogin.ROLE_ADMIN))
			throw new IllegalArgumentException("Cannot delete the user manager admin role!");

		final RoleEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such Role: " + id);

		dao.delete(entity);

		return Response.seeOther(URI.create("/roles")).build();
	}


	@Override
	@Transactional
	public Response changeCaption(final String id, final String token, final String caption)
	{
		tokenStore.validate(TOKEN_USE, token);

		final RoleEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such Role: " + id);

		entity.setCaption(caption);

		dao.update(entity);

		return Response.seeOther(URI.create("/role/" + id)).build();
	}


	@Override
	@Transactional
	public Response changeMembers(final String id, final String token, final List<Integer> members)
	{
		tokenStore.validate(TOKEN_USE, token);

		final RoleEntity entity = dao.getById(id);

		if (entity == null)
			throw new IllegalArgumentException("No such Role: " + id);

		final List<UserEntity> users = userDao.getListById(members);

		if (users.size() != members.size())
			throw new IllegalArgumentException("One or more members provided did not exist! " + members);

		{
			final List<Integer> existing = entity.getMembers().stream().map(UserEntity:: getId).collect(Collectors.toList());

			final List<Integer> added = members.stream().filter(i -> !existing.contains(i)).collect(Collectors.toList());
			final List<Integer> removed = members.stream().filter(i -> existing.contains(i)).collect(Collectors.toList());
		}

		entity.getMembers().clear();
		;
		entity.getMembers().addAll(users);

		dao.update(entity);

		return Response.seeOther(URI.create("/role/" + id)).build();
	}
}
