package com.peterphi.usermanager.guice.authentication.ldap;

import org.junit.Test;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.BasicControl;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class LDAPSearchServiceTest
{
	@Test
	public void fetchesAllGroupsBeyondDefaultADLimitAndResetsCookies() throws Exception
	{
		FakeLDAP ldap = new FakeLDAP(500, 500, 50);
		LDAPSearchService service = new LDAPSearchService();
		List<SearchResult> results = service.executePagedLdapSearch(ldap.context, new SearchControls(), "(member=user)");

		assertEquals(1050, results.size());
		assertEquals("group1049", results.get(1049).getName());
		assertEquals(3, ldap.requests.size());
		assertNull(ldap.requests.get(0).getCookie());
		assertArrayEquals(new byte[]{1}, ldap.requests.get(1).getCookie());
		assertArrayEquals(new byte[]{2}, ldap.requests.get(2).getCookie());
		for (PagedResultsResponseControl request : ldap.requests)
		{
			assertEquals(500, request.getResultSize());
			assertTrue(request.isCritical());
		}
		assertEquals(3, ldap.closed);
		assertArrayEquals(ldap.originalControls, ldap.controls);

		// The next lookup on the same connection must start a new paging sequence.
		ldap.page = 0;
		service.executePagedLdapSearch(ldap.context, new SearchControls(), "(sAMAccountName=user)");
		assertNull(ldap.requests.get(3).getCookie());
		assertArrayEquals(ldap.originalControls, ldap.controls);
	}

	@Test
	public void deferredErrorAfterPartialPageFailsWholeSearch()
	{
		FakeLDAP ldap = new FakeLDAP(2);
		ldap.failure = new PartialResultException("incomplete groups");
		LDAPSearchException error = assertThrows(LDAPSearchException.class,
		                                        () -> new LDAPSearchService().executePagedLdapSearch(ldap.context,
		                                                                                           new SearchControls(),
		                                                                                           "(member=user)"));
		assertSame(ldap.failure, error.getCause());
		assertEquals(1, ldap.closed);
		assertArrayEquals(ldap.originalControls, ldap.controls);
	}

	@Test
	public void laterPageFailureDoesNotReturnEarlierPages()
	{
		FakeLDAP ldap = new FakeLDAP(2, 1);
		ldap.failurePage = 1;
		ldap.failure = new SizeLimitExceededException("limit exceeded");
		LDAPSearchException error = assertThrows(LDAPSearchException.class,
		                                        () -> new LDAPSearchService().executePagedLdapSearch(ldap.context,
		                                                                                           new SearchControls(),
		                                                                                           "(member=user)"));
		assertSame(ldap.failure, error.getCause());
		assertEquals(2, ldap.closed);
		assertArrayEquals(ldap.originalControls, ldap.controls);
	}

	@Test
	public void missingPagingResponseFailsInsteadOfAcceptingResults()
	{
		FakeLDAP ldap = new FakeLDAP(2);
		ldap.omitResponse = true;
		assertThrows(LDAPSearchException.class,
		             () -> new LDAPSearchService().executePagedLdapSearch(ldap.context, new SearchControls(), "(member=user)"));
		assertArrayEquals(ldap.originalControls, ldap.controls);
	}

	@Test
	public void unpagedSearchStillReportsDeferredLimitErrors()
	{
		FakeLDAP ldap = new FakeLDAP(2);
		ldap.failure = new SizeLimitExceededException("limit exceeded");
		LDAPSearchService service = new LDAPSearchService();
		service.ldapPageSize = 0;
		LDAPSearchException error = assertThrows(LDAPSearchException.class,
		                                        () -> service.executePagedLdapSearch(ldap.context, new SearchControls(), "(member=user)"));
		assertSame(ldap.failure, error.getCause());
		assertTrue(ldap.requests.isEmpty());
		assertEquals(1, ldap.closed);
	}

	@Test
	public void allowsUnpagedServerWithEmptyOrNonemptyResults()
	{
		LDAPSearchService service = new LDAPSearchService();
		service.ldapPageSize = 0;
		for (int count : new int[]{0, 3})
		{
			FakeLDAP ldap = new FakeLDAP(count);
			ldap.omitResponse = true;
			assertEquals(count, service.executePagedLdapSearch(ldap.context, new SearchControls(), "(member=user)").size());
			assertTrue(ldap.requests.isEmpty());
			assertArrayEquals(ldap.originalControls, ldap.controls);
		}
	}

	@Test
	public void configurablePageSizeAndEmptyFinalPage() throws Exception
	{
		FakeLDAP ldap = new FakeLDAP(2, 0);
		LDAPSearchService service = new LDAPSearchService();
		service.ldapPageSize = 2;
		assertEquals(2, service.executePagedLdapSearch(ldap.context, new SearchControls(), "(member=user)").size());
		assertEquals(2, ldap.requests.size());
		assertEquals(2, ldap.requests.get(0).getResultSize());
		assertEquals(2, ldap.requests.get(1).getResultSize());
		assertEquals(2, ldap.closed);
	}

	/** Simulates LDAP's deferred errors: hasMoreElements() would silently end the enumeration. */
	private static class FakeLDAP
	{
		final Control[] originalControls = {new BasicControl("test-control")};
		Control[] controls = originalControls;
		final List<PagedResultsResponseControl> requests = new ArrayList<>();
		final int[] pageSizes;
		int page;
		int closed;
		int failurePage;
		NamingException failure;
		boolean omitResponse;
		final LdapContext context;

		FakeLDAP(int... pageSizes)
		{
			this.pageSizes = pageSizes;
			context = (LdapContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LdapContext.class},
			                                             (proxy, method, args) -> invoke(method.getName(), args));
		}

		private Object invoke(final String method, final Object[] args) throws Exception
		{
			switch (method)
			{
				case "getRequestControls":
					return controls;
				case "setRequestControls":
					controls = (Control[]) args[0];
					return null;
				case "search":
					assertSame(originalControls[0], controls[0]);
					if (controls.length > 1)
					{
						Control request = controls[1];
						requests.add(new PagedResultsResponseControl(request.getID(), request.isCritical(), request.getEncodedValue()));
					}
					return enumeration(page++);
				case "getResponseControls":
					if (omitResponse)
						return null;
					byte[] cookie = page < pageSizes.length ? new byte[]{(byte) page} : new byte[0];
					return new Control[]{new PagedResultsResponseControl(PagedResultsResponseControl.OID, false,
					                                                    new PagedResultsControl(0, cookie, false).getEncodedValue())};
				default:
					throw new AssertionError("Unexpected LDAP method: " + method);
			}
		}

		private NamingEnumeration<SearchResult> enumeration(final int currentPage)
		{
			return new NamingEnumeration<SearchResult>()
			{
				int index;

				@Override
				public boolean hasMore() throws NamingException
				{
					if (index < pageSizes[currentPage])
						return true;
					if (failure != null && currentPage == failurePage)
						throw failure;
					return false;
				}

				@Override
				public SearchResult next()
				{
					int offset = Arrays.stream(pageSizes).limit(currentPage).sum();
					return new SearchResult("group" + (offset + index++), null, new BasicAttributes());
				}

				@Override
				public void close()
				{
					closed++;
				}

				@Override
				public boolean hasMoreElements()
				{
					throw new AssertionError("hasMoreElements suppresses LDAP errors; use hasMore");
				}

				@Override
				public SearchResult nextElement()
				{
					throw new AssertionError("Use next()");
				}
			};
		}
	}
}
