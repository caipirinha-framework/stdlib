package com.peterphi.std.guice.hibernate.webquery.impl.jpa;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.peterphi.std.NotImplementedException;
import com.peterphi.std.guice.hibernate.module.logging.HibernateObservingInterceptor;
import com.peterphi.std.guice.hibernate.module.logging.HibernateSQLLogger;
import com.peterphi.std.guice.hibernate.webquery.ConstrainedResultSet;
import com.peterphi.std.guice.hibernate.webquery.impl.QEntity;
import com.peterphi.std.guice.restclient.jaxb.webquery.WebQuery;
import com.peterphi.std.util.tracing.Tracing;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.SessionFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class JPASearchExecutor
{
	private static final Logger log = LoggerFactory.getLogger(JPASearchExecutor.class);

	/**
	 * If true, will treat all WebQuery instances as if they had set computeSize to true<br />
	 * This is mainly present to allow unit tests to check that computeSize works with various different WebQuery calls without having to duplicate
	 */
	public static boolean ALWAYS_COMPUTE_SIZE = false;

	@Inject
	HibernateObservingInterceptor hibernateObserver;

	@Inject
	SessionFactory sessionFactory;


	/**
	 * Execute a search, returning a ConstrainedResultSet populated with the desired data (ID or Entity) with each piece of data
	 * optionally serialised using the supplied serialiser
	 *
	 * @param query
	 * 		the query to execute (including options like offset/limit, )
	 * @param strategy
	 * @param serialiser
	 * @param <T>
	 *
	 * @return
	 */
	public <T> ConstrainedResultSet<T> find(final QEntity entity,
	                                        final WebQuery query,
	                                        JPASearchStrategy strategy,
	                                        Function<?, ?> serialiser,
	                                        final boolean permitSchemaPrivate)
	{
		final String traceOperationId = Tracing.newOperationId("WebQuery:exec", query);

		final HibernateSQLLogger statementLog;

		if (query.isLogSQL())
			statementLog = hibernateObserver.startSQLLogger(traceOperationId);
		else
			statementLog = null;

		try
		{

			// Build a view of the query based on
			JPAQueryBuilder builder = new JPAQueryBuilder(sessionFactory.getCurrentSession(), entity, permitSchemaPrivate);

			builder.forWebQuery(query);

			// First, compute the total size if requested
			final Long total;
			if (ALWAYS_COMPUTE_SIZE || query.isComputeSize() || strategy == JPASearchStrategy.COUNT_ONLY)
			{
				JPAQueryBuilder countBuilder = new JPAQueryBuilder(sessionFactory.getCurrentSession(), entity, permitSchemaPrivate);
				countBuilder.forWebQuery(query);

				total = countBuilder.selectCount();
			}
			else
			{
				total = null;
			}


			List list;
			if (strategy == JPASearchStrategy.COUNT_ONLY || query.getLimit() == WebQuery.LIMIT_RETURN_ZERO)
			{
				// Special limit value of -1 means do not fetch any result data (used when just wanting to compute totals)
				list = Collections.emptyList();
			}
			else if (!ALWAYS_COMPUTE_SIZE && total != null && total.longValue() == 0)
			{
				// Count ran and indicated there were no results, so no need to re-query
				list = Collections.emptyList();
			}
			else
			{
				// If the auto strategy is in play, take into account what's being fetched back as well as whether there are any explicit collection joins or fetches
				if (strategy == null || strategy == JPASearchStrategy.AUTO)
				{
					if (StringUtils.equals(query.getFetch(), "id"))
					{
						strategy = JPASearchStrategy.ENTITY_WRAPPED_ID;
					}
					else if (query.getFetch() == null || StringUtils.equals(query.getFetch(), "entity"))
					{
						// Select one ordered ID per entity before fetching collection results.
						// Collection ordering also uses this path without pagination so its min/max semantics stay consistent.
						// This is necessary for correct pagination because the SQL resultset will have more than one row per entity, and our offset/limit is based on entity
						if (builder.hasCollectionOrder() || (builder.hasCollectionJoin() && builder.hasListOrCollectionFetch()) ||
						    ((query.getLimit() > 0 || query.getOffset() > 0) &&
						                                      (builder.hasCollectionJoin() || builder.hasCollectionFetch())))
						{
							strategy = JPASearchStrategy.ID_THEN_QUERY_ENTITY;
						}
						else
						{
							strategy = JPASearchStrategy.ENTITY;
						}
					}
					else
					{
						// Multiple values specified for fetch, so we ask for exactly what the user has requested
						strategy = JPASearchStrategy.CUSTOM_PROJECTION;
					}
				}

				switch (strategy)
				{
					case ID:
					{
						list = builder.selectIDs();

						break;
					}
					case CUSTOM_PROJECTION:
					case CUSTOM_PROJECTION_NODISTINCT:
					{
						if (StringUtils.equals(query.getFetch(), "id"))
						{
							list = builder.selectIDs();
						}
						else
						{
							// N.B. the returned array will also contain any order by elements if distinct=true
							final String[] fields = StringUtils.split(query.getFetch(), ',');

							// Generally, CUSTOM_PROJECTION must be DISTINCT for backwards compatibility
							// However we can guarantee that the PK will be distinct already, so PK+a single other field doesn't need DISTINCT
							// We try to avoid distinct where possible because it's harder for the DB to implement (and may require pulling back more cols in SELECT to boot)
							final boolean distinct;
							{
								final boolean wantDistinct = (strategy == JPASearchStrategy.CUSTOM_PROJECTION);

								// Loosen DISTINCT requirement if fetching PK+single field (since we know this does not need distinct)
								if (wantDistinct && fields.length <= 2 && "id".equals(fields[0]))
									distinct = false;
								else
									distinct = wantDistinct;
							}

							list = builder.selectCustomProjection(distinct, fields);
						}

						// N.B. if the user has only asked for a single fetch then we won't have a list of arrays
						// We make sure it's an Array for consistency (user can't rely that asking for a single field will
						// result in only that field, since the returned array may contain other fields the system needed to fetch in order to generate valid SQL)
						if (!list.isEmpty() && !hasArray(list))
						{
							list = (List<Object[]>) list.stream().map((Object v) -> new Object[]{v}).collect(Collectors.toList());
						}

						break;
					}
					case ENTITY_WRAPPED_ID:
					{
						list = builder.selectIDs();

						// Transform the IDs into entity objects with the ID field populated
						list = (List) list.stream().map(entity :: newInstanceWithId).collect(Collectors.toList());

						break;
					}
					case ENTITY:
					{
						// TODO could we use ScrollableResults if there are collection joins? pagination would be tricky

						list = builder.selectEntity();

						break;
					}
					case ID_THEN_QUERY_ENTITY:
					{
						// IDs are internal to entity pagination, not exposed query results.
						list = builder.selectIDsForEntityPage();

						// Now re-query to retrieve the entities
						if (!list.isEmpty())
						{
							final List<?> orderedIds = list;
							builder = new JPAQueryBuilder(sessionFactory.getCurrentSession(), entity, permitSchemaPrivate);
							builder.forIDs(query, orderedIds);

							final Map<Object, Object> entitiesById = (Map) builder.selectEntity().stream()
									.collect(Collectors.toMap(sessionFactory.getPersistenceUnitUtil()::getIdentifier, Function.identity()));
							list = orderedIds.stream().filter(entitiesById::containsKey).map(entitiesById::get).toList();
						}


						break;
					}
					default:
						throw new NotImplementedException("Search Strategy " + strategy + " not yet implemented");
				}

				// If a serialiser has been supplied, run it
				if (serialiser != null)
					list = (List) list.stream().map(serialiser).collect(Collectors.toList());
			}

			ConstrainedResultSet resultset = new ConstrainedResultSet<>(query, list);

			if (statementLog != null && query.isLogSQL())
				resultset.setSql(statementLog.getAllStatements());

			if (total != null)
				resultset.setTotal(total);

			Tracing.logOngoing(traceOperationId, "WebQuery:exec:result", "size=", resultset.getList().size(), ", total=", total);

			return (ConstrainedResultSet<T>) resultset;
		}
		finally
		{
			if (statementLog != null)
				statementLog.close();
		}
	}


	/**
	 * Tests if a hibernate result list is a list of arrays
	 *
	 * @param list
	 * @return
	 */
	private boolean hasArray(final List<?> list)
	{
		final Object o = list.get(0);

		return o != null && o.getClass().isArray();
	}
}
