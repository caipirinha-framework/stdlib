package com.peterphi.usermanager.db.dao.hibernate;

import com.google.inject.Singleton;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.types.SimpleId;
import com.peterphi.usermanager.db.entity.IDPrefix;
import com.peterphi.usermanager.db.entity.OAuthServiceEntity;
import com.peterphi.usermanager.db.entity.OAuthSessionContextEntity;
import com.peterphi.usermanager.db.entity.OAuthSessionEntity;
import org.joda.time.DateTime;

@Singleton
public class OAuthSessionDaoImpl extends HibernateDao<OAuthSessionEntity, String>
{
	@Transactional
	public OAuthSessionEntity create(final OAuthSessionContextEntity context, final String initiator, final DateTime expires)
	{
		OAuthSessionEntity session = new OAuthSessionEntity();
		session.setId(SimpleId.alphanumeric(IDPrefix.OAUTH_SESSION, 36));
		session.setContext(context);
		session.setInitiator(initiator);

		session.setAuthorisationCode(SimpleId.alphanumeric(IDPrefix.OAUTH_AUTHORISATION_CODE, 36));

		session.setExpires(expires);

		session = merge(session); // todo can we just return this?

		return getById(session.getId());
	}


	@Transactional
	public OAuthSessionEntity exchangeRefreshTokenForNewToken(final OAuthServiceEntity service,
	                                                          final String refreshToken,
	                                                          final DateTime newExpires)
	{
		final OAuthSessionEntity session = uniqueResult(new WebQuery()
				                                                .eq("id", refreshToken)
				                                                .isNotNull("token")
				                                                .eq("alive", true)
				                                                .eq("context.service.id", service.getId()));

		if (session == null)
			return null;

		session.setToken(SimpleId.alphanumeric(IDPrefix.OAUTH_SESSION_TOKEN, 36));
		session.setExpires(newExpires);

		update(session);

		return session;
	}


	@Transactional
	public OAuthSessionEntity getSessionToDelegateByRefreshToken(final OAuthServiceEntity service, final String refreshToken)
	{
		final OAuthSessionEntity session = uniqueResult(new WebQuery()
				                                                .eq("id", refreshToken)
				                                                .isNotNull("token")
				                                                .eq("alive", true)
				                                                .eq("context.service:id", service.getId()));

		return session;
	}


	@Transactional
	public OAuthSessionEntity exchangeCodeForToken(final OAuthServiceEntity service, final String authorisationCode)
	{
		final OAuthSessionEntity session = uniqueResult(new WebQuery()
				                                                .eq("authorisationCode", authorisationCode)
				                                                .isNull("token")
				                                                .eq("alive", true)
				                                                .eq("context.service.id", service.getId()));

		if (session == null)
			return null;

		session.setToken(SimpleId.alphanumeric(IDPrefix.OAUTH_SESSION_TOKEN, 36));
		session.setAuthorisationCode(null);

		update(session);

		return session;
	}


	@Transactional
	public OAuthSessionEntity extend(final String refreshToken, final DateTime newExpires)
	{
		OAuthSessionEntity session = getById(refreshToken);

		if (refreshToken == null)
			throw new IllegalArgumentException("Invalid refresh token: no live session by id  " + refreshToken);
		else if (!session.isAlive())
			throw new IllegalArgumentException("Invalid refresh token: no live session by id  " + refreshToken);

		session.setExpires(newExpires);
		session.setToken(SimpleId.alphanumeric(IDPrefix.OAUTH_SESSION_TOKEN, 36));

		update(session);

		return session;
	}


	@Transactional(readOnly = true)
	public OAuthSessionEntity getByToken(final String token)
	{
		return uniqueResult(new WebQuery().eq("token", token).eq("alive", true).gt("expires", DateTime.now()));
	}
}
