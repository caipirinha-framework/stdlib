package com.peterphi.usermanager.guice.authentication.ldap;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.common.serviceprops.annotations.Reconfigurable;
import com.peterphi.usermanager.guice.authentication.UserLogin;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

@Singleton
public class LDAPSearchService
{
	private static final Logger log = LoggerFactory.getLogger(LDAPSearchService.class);

	@Inject
	@Named("ldap.domain")
	@Reconfigurable
	public String domain;

	@Inject
	@Named("ldap.endpoint")
	@Reconfigurable
	public String ldapEndpoint;

	@Inject(optional = true)
	@Named("ldap.authentication")
	@Doc("The LDAP authentication type - sets java.naming.security.authentication (default 'simple')")
	@Reconfigurable
	public String ldapAuthenticationType = "simple";

	@Inject(optional = true)
	@Named("ldap.read-timeout")
	@Doc("The LDAP read timeout - sets com.sun.jndi.ldap.read.timeout (default Integer.MIN_VALUE which leaves at default)")
	@Reconfigurable
	public long ldapReadTimeout = Integer.MIN_VALUE;

	@Inject
	@Named("ldap.search-base")
	@Reconfigurable
	public String ldapSearchBase;

	/**
	 * Formatted with 1 argument, the username
	 */
	@Inject
	@Named("ldap.filter")
	@Doc("Java Format String that takes one string arg, the supplied username")
	@Reconfigurable
	public String ldapFilter;

	@Inject(optional = true)
	@Named("ldap.error.disclose-error-message")
	@Doc("If true, the underlying error message detail will be disclosed for NamingExceptions (default false)")
	@Reconfigurable
	public boolean reportLdapErrorMessage = false;

	/**
	 * Formatted with 1 argument, the user's DN. Searches for the groups a user is a member of (directly or indirectly)
	 */
	@Inject(optional = true)
	@Named("ldap.find-groups-filter")
	@Reconfigurable
	public String ldapGroupFilter = "(member:1.2.840.113556.1.4.1941:=%s)";

	/**
	 * The find pattern to use on group DNs. e.g. <code>(?i)^cn=([^,]+),</code>
	 */
	@Inject
	@Named("ldap.group-regex.find")
	@Reconfigurable
	public String ldapGroupFind;

	/**
	 * The replacement pattern to use after the find pattern is executed - e.g. <code>$1</code> for the first capture group
	 */
	@Inject
	@Named("ldap.group-regex.replace")
	@Reconfigurable
	public String ldapGroupReplace;

	@Inject(optional = true)
	@Named("ldap.group.user-manager-admin")
	@Doc("The group name (after group-regex execution) to treat as builtin group user-manager-admin (ignored if omitted)")
	@Reconfigurable
	public String groupUserManagerAdmin = null;


	@Inject(optional = true)
	@Named("ldap.group.admin")
	@Doc("The group name (after group-regex execution) to treat as builtin group admin (ignored if omitted)")
	@Reconfigurable
	public String groupAdmin = null;

	@Inject(optional = true)
	@Named("ldap.group.user")
	@Doc("The group name (after group-regex execution) to treat as builtin group user (ignored if omitted)")
	@Reconfigurable
	public String groupUser = null;

	@Inject(optional = true)
	@Named("ldap.group.framework-admin")
	@Doc("The group name (after group-regex execution) to treat as builtin group framework-admin (ignored if omitted)")
	@Reconfigurable
	public String groupFrameworkAdmin = null;

	@Inject(optional = true)
	@Named("ldap.group.framework-info")
	@Doc("The group name (after group-regex execution) to treat as builtin group framework-info (ignored if omitted)")
	@Reconfigurable
	public String groupFrameworkInfo = null;


	/**
	 * Get the groups that a user is a member of (either directly or indirectly)
	 *
	 * @param ldap
	 * @param dn
	 *
	 * @return
	 *
	 * @throws NamingException
	 */
	private List<LDAPGroup> getGroups(final DirContext ldap, final String dn) throws NamingException
	{
		// Get the DN of all grouos the user is directly/indirectly a member of
		final NamingEnumeration<SearchResult> answer;
		{
			SearchControls search = new SearchControls();

			search.setSearchScope(SearchControls.SUBTREE_SCOPE);
			search.setReturningAttributes(new String[]{"dn"});

			final String searchFilter = String.format(this.ldapGroupFilter, escapeLdapSearchFilterValue(dn));

			answer = executeLdapSearch(ldap, search, searchFilter);
		}

		List<LDAPGroup> groups = new ArrayList<>();

		while (answer.hasMoreElements())
		{
			SearchResult sr = answer.next();

			final String groupDN = sr.getNameInNamespace();

			groups.add(dnToLdapGroup(groupDN));
		}

		return groups;
	}


	public LDAPUser parseUser(final String inputUsername)
	{
		// Allow the user to provide domain-slash-user or user@domain in addition to bare "user" with an implied domain
		final String username;
		final String fullyQualifiedUsername;
		if (inputUsername.indexOf('\\') > 0)
		{
			final String[] segments = StringUtils.split(inputUsername, "\\", 2);

			final String domain = segments[0];
			username = segments[1]; // get bare username (discard everything before the slash)

			fullyQualifiedUsername = username + "@" + domain;
		}
		else if (inputUsername.indexOf('@') > 0)
		{
			fullyQualifiedUsername = inputUsername;

			username = StringUtils.split(inputUsername, '@')[0]; // get bare username (discard everything after the @)
		}
		else
		{
			// Implied domain
			fullyQualifiedUsername = inputUsername + "@" + domain;

			username = inputUsername;
		}

		return new LDAPUser(username, fullyQualifiedUsername);
	}


	/**
	 *
	 * @param authUser
	 * @param password
	 * @param searchFor
	 * @return the LDAP user record - or null if the user did not meet the ldap search criteria
	 */
	@Nullable
	public LDAPUserRecord search(LDAPUser authUser, final String password, LDAPUser searchFor)
	{
		final Map<LDAPUser, LDAPUserRecord> results = search(authUser, password, Collections.singletonList(searchFor));

		return results.get(searchFor);
	}


	public Map<LDAPUser, LDAPUserRecord> search(LDAPUser authUser, final String password, List<LDAPUser> searchForAll)
	{
		if (log.isTraceEnabled())
			log.trace("Querying LDAP as {} for {}", authUser, searchForAll);

		try
		{
			DirContext ldapContext = null;
			try
			{
				Hashtable<String, String> ldapEnv = new Hashtable<>();
				ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
				ldapEnv.put(Context.PROVIDER_URL, ldapEndpoint);
				ldapEnv.put(Context.SECURITY_AUTHENTICATION, ldapAuthenticationType);
				ldapEnv.put(Context.SECURITY_PRINCIPAL, authUser.fullyQualifiedUsername);
				ldapEnv.put(Context.SECURITY_CREDENTIALS, password);
				if (ldapReadTimeout != Integer.MIN_VALUE)
					ldapEnv.put("com.sun.jndi.ldap.read.timeout", Long.toString(ldapReadTimeout)); // 90 seconds

				ldapContext = new InitialDirContext(ldapEnv); // N.B. sometimes takes ~10 seconds

				Map<LDAPUser, LDAPUserRecord> results = new HashMap<>();

				// Run a separate search for each user
				for (LDAPUser searchFor : searchForAll)
				{
					final NamingEnumeration<SearchResult> answer;
					{
						SearchControls search = new SearchControls();

						search.setSearchScope(SearchControls.SUBTREE_SCOPE);
						search.setReturningAttributes(new String[]{"dn", "name", "samAccountName"});

						final String searchFilter = String.format(this.ldapFilter, escapeLdapSearchFilterValue(searchFor.username));

						answer = executeLdapSearch(ldapContext, search, searchFilter);
					}

					LDAPUserRecord record = null;

					while (answer.hasMoreElements())
					{
						SearchResult sr = answer.next();
						Attributes attrs = sr.getAttributes();

						final String dn = sr.getNameInNamespace();
						final String name = attrs.get("name").get().toString();
						final String actualUsername = attrs.get("samAccountName").get().toString();

						// Get the direct & indirect group membership data
						List<LDAPGroup> groups = getGroups(ldapContext, dn);

						record = new LDAPUserRecord(actualUsername, name, groups);
					}

					results.put(searchFor, record);
				}

				return results;
			}
			finally
			{
				if (ldapContext != null)
					ldapContext.close();
			}
		}
		catch (NamingException e)
		{
			if (this.reportLdapErrorMessage)
			{
				throw new RuntimeException(
						"Error accessing LDAP server (incorrect username/password or server connection issue, please try again). " +
						e.getClass().getSimpleName() +
						": " +
						e.getMessage(),
						e);
			}
			else
			{
				throw new RuntimeException(
						"Error accessing LDAP server (incorrect username/password or server connection issue, please try again). Error Type: " +
						e.getClass().getSimpleName(),
						e);
			}
		}
	}


	public NamingEnumeration<SearchResult> executeLdapSearch(final DirContext ldapContext,
	                                                         final SearchControls search,
	                                                         final String searchFilter) throws LDAPSearchException
	{
		try
		{
			log.trace("LDAP Search: base='{}', filter='{}'", ldapSearchBase, searchFilter);
			final NamingEnumeration<SearchResult> answer = ldapContext.search(ldapSearchBase, searchFilter, search);

			log.trace("LDAP Search returned results: {}", (answer == null) ? false : answer.hasMore());

			return answer;
		}
		catch (NamingException e)
		{
			throw new LDAPSearchException("Error performing LDAP search filter '" +
			                              searchFilter +
			                              "', base DN '" +
			                              ldapSearchBase +
			                              "'. (resolvedName '" +
			                              e.getResolvedName() +
			                              "'). Error: " +
			                              e.getMessage(), e);
		}
	}


	private LDAPGroup dnToLdapGroup(final String dn)
	{
		String id = dn.replaceAll(ldapGroupFind, ldapGroupReplace);

		// If configured, substitute known LDAP groups for well-known admin groups
		id = replaceGroupName(id, groupFrameworkInfo, "framework-info"); // able to see /guice pages
		id = replaceGroupName(id, groupFrameworkAdmin, "framework-admin"); // able to admin /guice pages
		id = replaceGroupName(id, groupAdmin, "admin"); // application admin
		id = replaceGroupName(id, groupUser, "user"); // application user
		id = replaceGroupName(id, groupUserManagerAdmin, UserLogin.ROLE_ADMIN); // User Manager admin

		return new LDAPGroup(id, dn);
	}


	private static String replaceGroupName(final String input, final String ifInput, final String replacement)
	{
		if (ifInput != null && StringUtils.equals(ifInput, input))
			return replacement;
		else
			return input;
	}


	/**
	 * Escape a value that is to be substituted into an LDAP search filter, per RFC 4515 section 3. This prevents LDAP filter
	 * injection by ensuring that filter metacharacters present in the value are treated as literal characters rather than as
	 * filter syntax.
	 *
	 * @param value
	 * 		the raw (untrusted) value to be placed into a filter assertion value
	 *
	 * @return the escaped value (a normal value with no metacharacters is returned unchanged)
	 */
	static String escapeLdapSearchFilterValue(final String value)
	{
		if (value == null)
			return null;

		final StringBuilder sb = new StringBuilder(value.length());

		for (int i = 0; i < value.length(); i++)
		{
			final char c = value.charAt(i);

			switch (c)
			{
				case '\\':
					sb.append("\\5c");
					break;
				case '*':
					sb.append("\\2a");
					break;
				case '(':
					sb.append("\\28");
					break;
				case ')':
					sb.append("\\29");
					break;
				case '\0':
					sb.append("\\00");
					break;
				default:
					sb.append(c);
					break;
			}
		}

		return sb.toString();
	}
}
