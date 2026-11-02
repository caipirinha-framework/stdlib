package com.peterphi.usermanager.db.dao.hibernate;

import com.google.inject.Singleton;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.usermanager.db.entity.OAuthServiceEntity;
import com.peterphi.usermanager.db.entity.OAuthSessionContextEntity;
import com.peterphi.usermanager.db.entity.UserEntity;
import org.apache.commons.lang.StringUtils;

@Singleton
public class OAuthSessionContextDaoImpl extends HibernateDao<OAuthSessionContextEntity, Integer>
{
	@Transactional(readOnly = true)
	public OAuthSessionContextEntity get(final int userId, final String serviceId, final String scope)
	{
		return uniqueResult(new WebQuery()
				                    .eq("user.id", userId)
				                    .eq("service.id", serviceId)
				                    .eq("scope", StringUtils.trimToEmpty(scope))
				                    .eq("active", true));
	}


	/**
	 * Create a context (or reuse an existing active context)
	 *
	 * @param user
	 * 		the user
	 * @param service
	 * 		the service
	 * @param scope
	 * 		the access scope the service is granted
	 *
	 * @return
	 */
	@Transactional
	public OAuthSessionContextEntity create(final UserEntity user, final OAuthServiceEntity service, final String scope)
	{
		// Create a new context
		OAuthSessionContextEntity entity = get(user.getId(), service.getId(), scope);

		// No active context exists, we must create one
		if (entity == null)
		{
			entity = new OAuthSessionContextEntity();

			entity.setUser(user);
			entity.setScope(StringUtils.trimToEmpty(scope));
			entity.setService(service);
			entity.setActive(true);

			entity = merge(entity);
		}

		return entity;
	}

	@Transactional
	public void deleteByUser(final UserEntity user)
	{
		for (OAuthSessionContextEntity entity : find(new WebQuery().limit(0).eq("user.id", user.getId())).getList())
		{
			delete(entity);
		}
	}
}
