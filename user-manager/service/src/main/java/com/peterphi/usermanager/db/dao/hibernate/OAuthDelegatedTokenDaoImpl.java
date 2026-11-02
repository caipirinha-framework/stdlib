package com.peterphi.usermanager.db.dao.hibernate;

import com.google.inject.Singleton;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.usermanager.db.entity.OAuthDelegatedTokenEntity;
import com.peterphi.usermanager.db.entity.OAuthSessionEntity;
import org.joda.time.DateTime;

@Singleton
public class OAuthDelegatedTokenDaoImpl extends HibernateDao<OAuthDelegatedTokenEntity, String>
{
	public OAuthDelegatedTokenEntity create(final OAuthSessionEntity session, final DateTime expires)
	{
		OAuthDelegatedTokenEntity entity = new OAuthDelegatedTokenEntity();

		entity.setSession(session);
		entity.setExpires(expires);

		return merge(entity);
	}


	public OAuthDelegatedTokenEntity getByIdUnlessExpired(final String tokenId)
	{
		return find(new WebQuery()
				            .limit(0)
				            .eq("id", tokenId)
				            .ge("expires", DateTime.now())
				            .eq("session.alive", true)
				            .ge("session.expires", DateTime.now())).uniqueResult();
	}
}
