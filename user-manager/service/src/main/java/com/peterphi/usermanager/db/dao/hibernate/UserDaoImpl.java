package com.peterphi.usermanager.db.dao.hibernate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.common.auth.iface.CurrentUser;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.guice.hibernate.dao.HibernateDao;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.types.SimpleId;
import com.peterphi.std.util.tracing.Tracing;
import com.peterphi.usermanager.db.BCrypt;
import com.peterphi.usermanager.db.entity.UserEntity;
import com.peterphi.usermanager.util.UserManagerBearerToken;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Singleton
public class UserDaoImpl extends HibernateDao<UserEntity, Integer>
{
	private static final Logger log = LoggerFactory.getLogger(UserDaoImpl.class);

	@Inject
	@Named("auth.ldap.allow-session-reconnect")
	@Doc("If true, logins created using LDAP credentials will be allowed to use the Session Reconnect Key system to stay logged in longer without re-authenticating")
	public boolean allowRemoteAccountsToUseSessionReconnect;


	@Transactional
	public UserEntity login(String email, String password)
	{
		final UserEntity account = getUserByEmail(email);

		if (account != null && account.isLocal())
		{
			// Check if no password bcrypt hash
			if (StringUtils.isEmpty(account.getPassword()) || "NONE".equals(account.getPassword()))
				throw new IllegalArgumentException(
						"This account does not have a password set. You may have initiated but not completed a password reset, this may be a service account, or your account may have been disabled.");

			final boolean correct;
			try
			{
				correct = BCrypt.verify(account.getPassword(), password.toCharArray());
			}
			catch (Throwable t)
			{
				final String id = Tracing.getTraceId();
				log.warn("BCrypt.verify failed while verifying user's login attempt, trace={}", id, t);
				throw new RuntimeException(
						"An error occurred while checking your password. Please check server logs for more detail. Log Correlation Trace ID: " +
						id);
			}

			if (correct)
			{
				account.setLastLogin(new DateTime());
				update(account);
				return account;
			}
		}

		return null; // User doesn't exist (or password is wrong)
	}


	/**
	 * Rotate the primary access key -> secondary access key, dropping the old secondary access key and generating a new primary access key
	 *
	 * @param id
	 */
	@Transactional
	public void rotateUserAccessKey(final int id)
	{
		final UserEntity account = getById(id);

		if (account != null)
		{
			// Set the secondary token to the old primary token
			account.setAccessKeySecondary(account.getAccessKey());

			// Now regenerate the primary token
			account.setAccessKey(SimpleId.alphanumeric(UserManagerBearerToken.PREFIX, 100));

			update(account);
		}
		else
		{
			throw new IllegalArgumentException("No such user: " + id);
		}
	}


	public boolean isUserLocal(final String email)
	{
		UserEntity record = getUserByEmail(email);

		return (record != null && record.isLocal());
	}


	public UserEntity getUserByEmail(String email)
	{
		return uniqueResult(new WebQuery().eq("email", email));
	}


	public void changePassword(final int id, final String newPassword)
	{
		final UserEntity account = getById(id);

		if (account != null)
		{
			if (!account.isLocal())
				throw new IllegalArgumentException("Cannot change password: user is authenticated by remote service!");

			account.setPassword(hashPassword(newPassword));

			update(account);
		}
		else
		{
			throw new IllegalArgumentException("No such user: " + id);
		}
	}


	/**
	 * Creates a BCrypted hash for a password
	 *
	 * @param password
	 * @return
	 */
	private String hashPassword(String password)
	{
		return BCrypt.hash(password.toCharArray(), BCrypt.DEFAULT_COST);
	}


	@Transactional
	public int register(String name, String email, String password, final String dateFormat, final String timeZone)
	{
		if (userExists(email))
			throw new IllegalArgumentException("User '" + email + "' already exists!");
		if (password.isEmpty())
			throw new IllegalArgumentException("Must supply a password!");

		final UserEntity account = new UserEntity();

		account.setLocal(true);
		account.setName(name);
		account.setEmail(email);
		account.setPassword(hashPassword(password));
		account.setDateFormat(dateFormat);
		account.setTimeZone(timeZone);
		account.setSessionReconnectKey(UUID.randomUUID().toString());

		return merge(account).getId();
	}


	@Transactional
	public int registerRemote(final String username, final String fullName)
	{
		if (userExists(username))
			throw new IllegalArgumentException("User '" + username + "' already exists!");

		final UserEntity account = new UserEntity();
		account.setLocal(false);
		account.setEmail(username);
		account.setName(fullName);
		account.setPassword(""); // Won't allow password logins anyway, but set just in case

		if (allowRemoteAccountsToUseSessionReconnect)
			account.setSessionReconnectKey(UUID.randomUUID().toString());
		else
			account.setSessionReconnectKey(null);

		account.setTimeZone(CurrentUser.DEFAULT_TIMEZONE);
		account.setDateFormat(CurrentUser.DEFAULT_DATE_FORMAT_STRING);

		return merge(account).getId();
	}


	@Transactional
	public boolean userExists(String email)
	{
		final UserEntity account = getUserByEmail(email);

		return (account != null);
	}


	@Transactional
	public UserEntity loginBySessionReconnectKey(String key)
	{
		final WebQuery search = new WebQuery().eq("sessionReconnectKey", key);

		if (!allowRemoteAccountsToUseSessionReconnect)
			search.eq("local", true);

		final UserEntity account = uniqueResult(search);

		if (account != null)
		{
			log.info("Allowed login by session reconnect key for user: {}", account.getEmail());

			account.setLastLogin(new DateTime());
			update(account);
		}

		return account;
	}


	@Transactional
	public UserEntity loginByAccessKey(final String key)
	{
		final UserEntity account = uniqueResult(new WebQuery().or(or -> or.eq("accessKey", key).eq("accessKeySecondary", key)));

		// N.B. for Access Key logins we don't update the last login timestamp
		if (account != null)
			log.debug("Allowed login by Access Key for user: {}", account.getEmail());

		return account;
	}


	@Transactional
	public void changeSessionReconnectKey(Integer id)
	{
		final UserEntity account = getById(id);

		if (account != null)
		{
			if (account.isLocal() || allowRemoteAccountsToUseSessionReconnect)
				account.setSessionReconnectKey(UUID.randomUUID().toString());
			else
				account.setSessionReconnectKey(null);

			update(account);
		}
	}


	@Transactional
	public UserEntity changeProfile(final int id,
	                                final String name,
	                                final String email,
	                                final String dateFormat,
	                                final String timeZone)
	{
		final UserEntity account = getById(id);

		if (account != null)
		{
			if (dateFormat == null || timeZone == null)
				throw new IllegalArgumentException("Must specify dateFormat and timeZone!");

			account.setName(name);
			account.setEmail(email);
			account.setDateFormat(dateFormat);
			account.setTimeZone(timeZone);

			update(account);

			return account;
		}
		else
		{
			throw new IllegalArgumentException("No such user: " + id);
		}
	}
}
