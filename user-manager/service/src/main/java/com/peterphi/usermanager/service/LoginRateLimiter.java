package com.peterphi.usermanager.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory per-account sliding window rate limiter for login attempts. After {@link #maxFailures} failed
 * attempts inside the trailing {@link #windowSeconds} window, further attempts for that account are
 * refused by throwing {@link LoginRateLimitedException} until enough time has passed for the oldest
 * recorded failures to age out of the window.
 *
 * <p>Successful logins immediately clear the record for that account. Account keys are normalised to
 * lower-case so that variations in letter-case cannot bypass the limit.</p>
 */
@Singleton
public class LoginRateLimiter
{
	private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

	@Inject(optional = true)
	@Named("auth.login.rate-limit.max-failures")
	@Doc("Maximum number of failed login attempts permitted per account inside the rolling window (default 5)")
	int maxFailures = 5;

	@Inject(optional = true)
	@Named("auth.login.rate-limit.window-seconds")
	@Doc("Rolling window, in seconds, over which failed login attempts are counted (default 60)")
	int windowSeconds = 60;

	private final Map<String, Deque<Long>> failures = new HashMap<>();


	/**
	 * Throws {@link LoginRateLimitedException} if the account has exceeded {@link #maxFailures} failures
	 * inside the trailing {@link #windowSeconds} window. Otherwise returns silently.
	 */
	public synchronized void checkAllowed(final String account)
	{
		if (StringUtils.isBlank(account))
			return;

		final String key = key(account);
		final Deque<Long> timestamps = failures.get(key);

		if (timestamps == null)
			return;

		expire(timestamps);

		if (timestamps.isEmpty())
		{
			failures.remove(key);
			return;
		}

		if (timestamps.size() >= maxFailures)
		{
			log.warn("Login rate limit exceeded for account '{}' ({} failures inside {}s window)",
			         key,
			         timestamps.size(),
			         windowSeconds);

			throw new LoginRateLimitedException("Too many failed login attempts for this account. Please wait a minute before trying again.");
		}
	}


	/**
	 * Record a failed login attempt for an account.
	 */
	public synchronized void recordFailure(final String account)
	{
		if (StringUtils.isBlank(account))
			return;

		final String key = key(account);
		final Deque<Long> timestamps = failures.computeIfAbsent(key, k -> new ArrayDeque<>());

		expire(timestamps);

		timestamps.addLast(System.currentTimeMillis());
	}


	/**
	 * Clear the failure record for an account (call after a successful login).
	 */
	public synchronized void recordSuccess(final String account)
	{
		if (StringUtils.isBlank(account))
			return;

		failures.remove(key(account));
	}


	private void expire(final Deque<Long> timestamps)
	{
		final long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);

		while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff)
			timestamps.removeFirst();
	}


	private static String key(final String account)
	{
		return StringUtils.lowerCase(StringUtils.trimToEmpty(account));
	}
}
