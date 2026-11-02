package com.peterphi.std.guice.hibernate.module;

		/*
		 * Based on warp-persist HibernateLocalTxnInterceptor which is originally
		 *
		 * Copyright (C) 2008 Wideplay Interactive.
		 *
		 * Licensed under the Apache License, Version 2.0 (the "License");
		 * you may not use this file except in compliance with the License.
		 * You may obtain a copy of the License at
		 *
		 * http://www.apache.org/licenses/LICENSE-2.0
		 *
		 * Unless required by applicable law or agreed to in writing, software
		 * distributed under the License is distributed on an "AS IS" BASIS,
		 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		 * See the License for the specific language governing permissions and
		 * limitations under the License.
		 */

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.Provider;
import com.peterphi.std.guice.common.metrics.GuiceMetricNames;
import com.peterphi.std.guice.database.annotation.Transactional;
import com.peterphi.std.util.tracing.Tracing;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang.StringUtils;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.StaleStateException;
import org.hibernate.Transaction;
import org.hibernate.TransactionException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.exception.SQLGrammarException;
import org.hibernate.resource.transaction.spi.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import static com.peterphi.std.guice.database.annotation.Transactional.DEFAULT_ISOLATION_LEVEL;
import static com.peterphi.std.guice.database.annotation.Transactional.IGNORE_ISOLATION_LEVEL;

/**
 * A Guice AOP interceptor for methods annotated with the {@link Transactional} annotation to transparently start and
 * commit/rollback a Hibernate transaction
 */
class TransactionMethodInterceptor implements MethodInterceptor
{
	private static final Logger log = LoggerFactory.getLogger(TransactionMethodInterceptor.class);

	private final Provider<Session> sessionProvider;

	private final boolean forceReadOnly;
	private final int defaultIsolationLevel;
	private final Timer calls;
	private final Timer transactionStartedCalls;
	private final Meter errorRollbacks;
	private final Meter commitFailures;

	private final boolean logStacktraceOnRetryableException;


	public TransactionMethodInterceptor(Provider<Session> sessionProvider, MetricRegistry metrics, boolean forceReadOnly, int defaultIsolationLevel, final boolean logStacktraceOnRetryableException)
	{
		this.sessionProvider = sessionProvider;

		this.calls = metrics.timer(GuiceMetricNames.TRANSACTION_CALLS_TIMER);
		this.transactionStartedCalls = metrics.timer(GuiceMetricNames.TRANSACTION_OWNER_CALLS_TIMER);
		this.errorRollbacks = metrics.meter(GuiceMetricNames.TRANSACTION_ERROR_ROLLBACK_METER);
		this.commitFailures = metrics.meter(GuiceMetricNames.TRANSACTION_COMMIT_FAILURE_METER);
		this.forceReadOnly = forceReadOnly;
		this.defaultIsolationLevel = defaultIsolationLevel;
		this.logStacktraceOnRetryableException = logStacktraceOnRetryableException;
	}


	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable
	{
		Session session = sessionProvider.get();
		final TransactionStatus initialStatus = session.getTransaction().getStatus();

		if (initialStatus == TransactionStatus.ACTIVE)
		{
			// allow silent joining of enclosing transactional methods (NOTE: this ignores the current method's txn-al settings)
			if (log.isTraceEnabled())
				log.trace("Joining existing transaction to call {}", invocation.getMethod().toGenericString());

			return invocation.proceed();
		}
		else
		{
			Timer.Context callTimer = calls.time();

			final String tracingId = Tracing.newOperationId("TX:begin", invocation.getMethod());
			Tracing.logOngoing(tracingId, "TX:initialStatus", initialStatus);

			try
			{
				final Transactional annotation = readAnnotation(invocation);

				// If we are allowed to auto-retry, first run the method with exception-catching retry behaviour
				// After the max attempts for auto retry are exhausted we'll fall back on the non-retrying default behaviour
				if (annotation.autoRetry())
				{
					// Try all but the last attempt
					final int retries = Math.max(0, annotation.autoRetryCount() - 1);

					// N.B. more aggressive than the @Retry annotation implements
					final long baseBackoff = 1000;
					long backoff = baseBackoff;
					final double multiplier = 1.5;

					for (int attempt = 0; attempt < retries; attempt++)
					{
						try
						{
							return createTransactionAndExecuteMethod(invocation, annotation, tracingId);
						}
						catch (LockAcquisitionException | StaleStateException | GenericJDBCException | OptimisticLockException e)
						{
							if (logStacktraceOnRetryableException || log.isTraceEnabled())
								log.warn("@Transactional caught exception {}; retrying...", e.getClass().getSimpleName(), e);
							else
								log.warn("@Transactional on {} caught exception {}; retrying...", invocation.getMethod().toString(), e.getClass().getSimpleName());

							Tracing.logOngoing(tracingId, "TX:exception:retryable", (Supplier) () -> e.getClass().getSimpleName());

							try
							{
								Thread.sleep(backoff + (long) (baseBackoff * Math.random()));
							}
							catch (InterruptedException ie)
							{
								throw new RuntimeException("Interrupted while attempting a @Transactional retry!", ie);
							}

							// Increase backoff for the next exception
							backoff *= multiplier;
						}
						catch (PersistenceException e)
						{
							// Handle generic exception (usually one that wraps another exception)
							if (e.getCause() != null && (isSqlServerSnapshotConflictError(e) || isDeadlockError(e)))
							{
								if (log.isTraceEnabled())
									log.warn("@Transactional caught exception PersistenceException wrapping {}; retrying...",
									         e.getCause().getClass().getSimpleName(),
									         e);
								else
									log.warn("@Transactional caught exception PersistenceException wrapping {}; retrying...",
									         e.getCause().getClass().getSimpleName());

								Tracing.logOngoing(tracingId,
								                   "TX:exception:retryable:wrapped",
								                   (Supplier) () -> e.getCause().getClass().getSimpleName());

								try
								{
									Thread.sleep(backoff + (long) (baseBackoff * Math.random()));
								}
								catch (InterruptedException ie)
								{
									throw new RuntimeException("Interrupted while attempting a @Transactional retry!", ie);
								}

								// Increase backoff for the next exception
								backoff *= multiplier;
							}
							else
							{
								Tracing.logOngoing(tracingId, "TX:exception:fatal", (Supplier)() -> e.getClass().getSimpleName());
								throw e; // rethrow because we won't handle this
							}
						}
					}
				}

				Tracing.logOngoing(tracingId, "TX:last-try");
				// Run without further retries
				return createTransactionAndExecuteMethod(invocation, annotation, tracingId);
			}
			finally
			{
				Tracing.logOngoing(tracingId, "TX:quit");
				callTimer.stop();
			}
		}
	}


	private Object createTransactionAndExecuteMethod(final MethodInvocation invocation,
	                                                 final Transactional annotation, final String tracingId) throws Throwable
	{
		if (log.isTraceEnabled())
			Tracing.logOngoing(tracingId,
			                   "TX:createAndExecute",
			                   "Creating new transaction to call ",
			                   (Supplier) () -> invocation.getMethod().toGenericString());

		final boolean readOnly = forceReadOnly || annotation.readOnly();

		// We are responsible for creating+closing the connection
		final Timer.Context ownerTimer = transactionStartedCalls.time();

		final Session session = sessionProvider.get();

		final boolean logVerbose = Tracing.isVerbose();

		if (logVerbose)
			Tracing.logOngoing(tracingId,
			                   "TX:create",
			                   "Creating new transaction, current status: ",
			                   session.getTransaction().getStatus());

		// Isolation level to be restored in finally block
		// N.B. only changed from this default if we actually change the Connection's transaction isolation
		int originalIsolationLevel = IGNORE_ISOLATION_LEVEL;

		try
		{
			// no transaction already started, so start one and enforce its semantics
			final Transaction tx = session.beginTransaction();

			// Try to signal read-only nature of TX to database and Hibernate
			if (readOnly)
				makeReadOnly(session, tracingId);
			else
				makeReadWrite(session);


			// if an isolation level has been specified, ensure it is set
			{
				final int desiredIsolation = getDesiredIsolation(annotation.isolationLevel());

				if (desiredIsolation > 0)
				{
					if (logVerbose)
						Tracing.logOngoing(tracingId, "TX:create Isolation ", desiredIsolation);

					originalIsolationLevel = session.doReturningWork(conn -> {
						final int originalIsolation = conn.getTransactionIsolation();

						if (logVerbose)
							Tracing.logOngoing(tracingId, "TX:create Isolation level was: ", originalIsolation);

						if (originalIsolation == desiredIsolation)
							return IGNORE_ISOLATION_LEVEL; // isolation level was not changed, no need to set it, or to change it back

						conn.setTransactionIsolation(desiredIsolation);
						return originalIsolation;
					});
				}
			}


			// Execute the method
			final Object result;
			try
			{
				result = invocation.proceed();
			}
			catch (Exception e)
			{
				try
				{
					if (!readOnly && shouldRollback(annotation, e))
					{
						errorRollbacks.mark();

						rollback(tx, e);
					}
					else
					{
						complete(tx, readOnly);
					}
				}
				catch (Throwable txre)
				{
					// Don't throw the rollback error, since the user really only cares about the actual original error
					// But we should notify them that a rollback error did occur
					if (!readOnly)
					{
						log.warn("TX encountered error and then failed during rollback! Original Error: {}", e.getMessage(), e);
						log.warn("TX encountered error and then failed during rollback! Rollback Error: ", txre);

						throw new TransactionException(
								"Encountered Exception while rolling back transaction! Cause contains original error causing rollback. Rollback error was:" +
								e.getMessage() +
								", original cause of rollback was: " +
								e.getMessage(),
								e);
					}
					else
					{
						log.warn("Read-Only TX encountered error and then failed during rollback! Original Error: {}",
						         e.getMessage(),
						         e);
						log.warn("Read-Only TX encountered error and then failed during rollback! Rollback Error: ", txre);
					}
				}

				// propagate the exception
				throw e;
			}
			catch (Error e)
			{
				errorRollbacks.mark();


				// propagate the error
				throw e;
			}

			// The method completed successfully, we can complete the the transaction
			// we can't move into the above try block because it'll interfere with the do not move into try block as it interferes with the advised method's throwing semantics
			RuntimeException commitException = null;
			try
			{
				complete(tx, readOnly);
			}
			catch (RuntimeException e)
			{
				if (readOnly)
				{
					log.warn(
							"Read-Only TX encountered error during rollback! Will not share this with user (since actual read-only TX method completed normally). Error is: {}",
							e.getMessage(),
							e);
				}
				else
				{
					commitFailures.mark();

					rollback(tx);

					commitException = e;
				}
			}

			// propagate anyway
			if (commitException != null)
				throw commitException;

			// or return result
			return result;
		}
		finally
		{
			ownerTimer.stop();

			if (session.isOpen())
			{
				// If we changed the isolation level, try to restore it
				restoreIsolationLevel(session, originalIsolationLevel, tracingId);

				// Close the session
				session.close();
			}
		}
	}


	private int getDesiredIsolation(final int isolationLevelAnnotation)
	{
		if (isolationLevelAnnotation == IGNORE_ISOLATION_LEVEL)
			return IGNORE_ISOLATION_LEVEL; // Don't touch Connection's isolation level
		else if (isolationLevelAnnotation == DEFAULT_ISOLATION_LEVEL)
			return defaultIsolationLevel; // Use the default isolation level
		else
			return isolationLevelAnnotation; // Use a specific isolation level
	}


	/**
	 * Make the {@link java.sql.Connection} underlying this {@link Session} read/write
	 *
	 * @param session
	 */
	private void makeReadWrite(final Session session)
	{
		session.setHibernateFlushMode(FlushMode.AUTO);
		session.setDefaultReadOnly(false);

		try {
			session.doWork(SetJDBCConnectionReadOnlyWork.READ_WRITE);
		}
		catch (Throwable t)
		{
			log.debug("Error signalling to DB that session is read/write", t);
		}

	}


	/**
	 * Make the session (and the underlying {@link java.sql.Connection} read only
	 *
	 * @param session
	 * @param tracingId
	 */
	private void makeReadOnly(final Session session, final String tracingId)
	{
		Tracing.logOngoing(tracingId, "TX:makeReadonly Set Default ReadOnly");

		session.setDefaultReadOnly(true);

		Tracing.logOngoing(tracingId, "TX:makeReadonly Set Hibernate Flush Mode to MANUAL");

		session.setHibernateFlushMode(FlushMode.MANUAL);

		try
		{
			Tracing.logOngoing(tracingId, "TX:makeReadonly Make Connection Read Only");

			// Make the Connection read only
			session.doWork(SetJDBCConnectionReadOnlyWork.READ_ONLY);
		}
		catch (Throwable t)
		{
			log.debug("Error signalling to DB that session is read-only", t);
		}

		Tracing.logOngoing(tracingId, "TX:makeReadonly Complete");
	}


	/**
	 * Read the Transactional annotation for a given method invocation
	 *
	 * @param invocation
	 *
	 * @return
	 */
	private Transactional readAnnotation(MethodInvocation invocation)
	{
		final Method method = invocation.getMethod();

		if (method.isAnnotationPresent(Transactional.class))
		{
			return method.getAnnotation(Transactional.class);
		}
		else
		{
			throw new RuntimeException("Could not find Transactional annotation");
		}
	}


	/**
	 * @param annotation
	 * 		The metadata annotation of the method
	 * @param e
	 * 		The exception to test for rollback
	 *
	 * @return returns true if the transaction should be rolled back, otherwise false
	 */
	private boolean shouldRollback(Transactional annotation, Exception e)
	{
		return isInstanceOf(e, annotation.rollbackOn()) && !isInstanceOf(e, annotation.exceptOn());
	}


	/**
	 * Special-case for SQL Server SNAPSHOT isolation level: transaction conflicts result in an exception that hibernate is not properly aware of and cannot catch.<br />
	 * It looks like this:
	 * <pre>jakarta.persistence.PersistenceException: org.hibernate.exception.SQLGrammarException: could not execute statement
	 * at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:149)
	 * at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:157)
	 * at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:164)
	 * at org.hibernate.internal.SessionImpl.doFlush(SessionImpl.java:1443)
	 * at org.hibernate.internal.SessionImpl.managedFlush(SessionImpl.java:493)
	 * at org.hibernate.internal.SessionImpl.flushBeforeTransactionCompletion(SessionImpl.java:3207)
	 * at org.hibernate.internal.SessionImpl.beforeTransactionCompletion(SessionImpl.java:2413)
	 * at org.hibernate.engine.jdbc.internal.JdbcCoordinatorImpl.beforeTransactionCompletion(JdbcCoordinatorImpl.java:467)
	 * at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl.beforeCompletionCallback(JdbcResourceLocalTransactionCoordinatorImpl.java:156)
	 * at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl.access$100(JdbcResourceLocalTransactionCoordinatorImpl.java:38)
	 * at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl$TransactionDriverControlImpl.commit(JdbcResourceLocalTransactionCoordinatorImpl.java:231)
	 * at org.hibernate.engine.transaction.internal.TransactionImpl.commit(TransactionImpl.java:68)
	 * at com.peterphi.std.guice.hibernate.module.TransactionMethodInterceptor.complete(TransactionMethodInterceptor.java:316)
	 * at com.peterphi.std.guice.hibernate.module.TransactionMethodInterceptor.createTransactionAndExecuteMethod(TransactionMethodInterceptor.java:196)
	 * at com.peterphi.std.guice.hibernate.module.TransactionMethodInterceptor.invoke(TransactionMethodInterceptor.java:102)
	 * Caused by: org.hibernate.exception.SQLGrammarException: could not execute statement
	 * at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:106)
	 * at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:42)
	 * at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:111)
	 * at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:97)
	 * at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:178)
	 * at org.hibernate.engine.jdbc.batch.internal.NonBatchingBatch.addToBatch(NonBatchingBatch.java:45)
	 * at org.hibernate.persister.entity.AbstractEntityPersister.update(AbstractEntityPersister.java:3198)
	 * at org.hibernate.persister.entity.AbstractEntityPersister.updateOrInsert(AbstractEntityPersister.java:3077)
	 * at org.hibernate.persister.entity.AbstractEntityPersister.update(AbstractEntityPersister.java:3457)
	 * at org.hibernate.action.internal.EntityUpdateAction.execute(EntityUpdateAction.java:145)
	 * at org.hibernate.engine.spi.ActionQueue.executeActions(ActionQueue.java:589)
	 * at org.hibernate.engine.spi.ActionQueue.executeActions(ActionQueue.java:463)
	 * at org.hibernate.event.internal.AbstractFlushingEventListener.performExecutions(AbstractFlushingEventListener.java:337)
	 * at org.hibernate.event.internal.DefaultFlushEventListener.onFlush(DefaultFlushEventListener.java:39)
	 * at org.hibernate.internal.SessionImpl.doFlush(SessionImpl.java:1437)
	 * ... 14 more
	 * Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: Snapshot isolation transaction aborted due to update conflict. You cannot use snapshot isolation to access table 'table_name' directly or indirectly in database 'database_name' to update, delete, or insert the row that has been modified or deleted by another transaction. Retry the transaction or change the isolation level for the update/delete statement.
	 * at com.microsoft.sqlserver.jdbc.SQLServerException.makeFromDatabaseError(SQLServerException.java:217)
	 * at com.microsoft.sqlserver.jdbc.SQLServerStatement.getNextResult(SQLServerStatement.java:1655)
	 * at com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement.doExecutePreparedStatement(SQLServerPreparedStatement.java:440)
	 * at com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement$PrepStmtExecCmd.doExecute(SQLServerPreparedStatement.java:385)
	 * at com.microsoft.sqlserver.jdbc.TDSCommand.execute(IOBuffer.java:7505)</pre>
	 *
	 * @param e
	 *
	 * @return
	 */
	private boolean isSqlServerSnapshotConflictError(Throwable e)
	{
		// Looking for PersistenceException, wrapping a SQLGrammarException, wrapping a SQLServerException with text "Snapshot isolation transaction aborted due to update conflict"
		if (e != null && e instanceof PersistenceException)
		{
			e = e.getCause();

			if (e != null && e instanceof SQLGrammarException)
			{
				e = e.getCause();

				if (e != null && StringUtils.equals(e.getClass().getName(), "com.microsoft.sqlserver.jdbc.SQLServerException"))
				{
					return (StringUtils.startsWith(e.getMessage(),
					                               "Snapshot isolation transaction aborted due to update conflict"));
				}
			}
		}

		// Does not appear to match
		return false;
	}


	/**
	 * Special-case for SQL Server deadlock errors, that look like:
	 * <pre>2017-12-01 07:49:48,504 ERROR Transaction (Process ID 55) was deadlocked on lock resources with another process and has been chosen as the deadlock victim. Rerun the transaction.
	 * 2017-12-01 07:49:48,539 ERROR z9ti8f0eai POST /automation/work-orders threw exception:
	 * jakarta.persistence.PersistenceException: org.hibernate.exception.LockAcquisitionException: could not execute query
	 * at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:149)
	 * at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:157)
	 * at org.hibernate.query.internal.AbstractProducedQuery.list(AbstractProducedQuery.java:1423)
	 * at org.hibernate.query.Query.getResultList(Query.java:146)
	 * at org.hibernate.query.criteria.internal.compile.CriteriaQueryTypeQueryAdapter.getResultList(CriteriaQueryTypeQueryAdapter.java:72)
	 * at com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPAQueryBuilder.selectEntity(JPAQueryBuilder.java:512)
	 * at com.peterphi.std.guice.hibernate.webquery.impl.jpa.JPASearchExecutor.find(JPASearchExecutor.java:118)
	 * at com.peterphi.std.guice.hibernate.dao.HibernateDao.find(HibernateDao.java:552)
	 * at com.peterphi.std.guice.hibernate.module.TransactionMethodInterceptor.invoke(TransactionMethodInterceptor.java:84)
	 * at com.peterphi.std.guice.hibernate.dao.HibernateDao.find(HibernateDao.java:532)
	 * at com.peterphi.std.guice.hibernate.dao.HibernateDao.find(HibernateDao.java:525)
	 * at com.peterphi.std.guice.hibernate.dao.HibernateDao.findByUriQuery(HibernateDao.java:518)
	 * ...
	 * at com.peterphi.std.guice.hibernate.module.TransactionMethodInterceptor.createTransactionAndExecuteMethod(TransactionMethodInterceptor.java:164)
	 * at com.peterphi.std.guice.hibernate.module.TransactionMethodInterceptor.invoke(TransactionMethodInterceptor.java:105)
	 * ...
	 * Caused by: org.hibernate.exception.LockAcquisitionException: could not execute query
	 * at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:123)
	 * at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:42)
	 * at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:111)
	 * at org.hibernate.loader.Loader.doList(Loader.java:2705)
	 * at org.hibernate.loader.Loader.doList(Loader.java:2685)
	 * at org.hibernate.loader.Loader.listIgnoreQueryCache(Loader.java:2517)
	 * at org.hibernate.loader.Loader.list(Loader.java:2512)
	 * at org.hibernate.loader.hql.QueryLoader.list(QueryLoader.java:502)
	 * at org.hibernate.hql.internal.ast.QueryTranslatorImpl.list(QueryTranslatorImpl.java:384)
	 * at org.hibernate.engine.query.spi.HQLQueryPlan.performList(HQLQueryPlan.java:216)
	 * at org.hibernate.internal.SessionImpl.list(SessionImpl.java:1490)
	 * at org.hibernate.query.internal.AbstractProducedQuery.doList(AbstractProducedQuery.java:1445)
	 * at org.hibernate.query.internal.AbstractProducedQuery.list(AbstractProducedQuery.java:1414)
	 * ... 44 more
	 * Caused by: com.microsoft.sqlserver.jdbc.SQLServerException: Transaction (Process ID 55) was deadlocked on lock resources with another process and has been chosen as the deadlock victim. Rerun the transaction.
	 * at com.microsoft.sqlserver.jdbc.SQLServerException.makeFromDatabaseError(SQLServerException.java:217)
	 * at com.microsoft.sqlserver.jdbc.SQLServerResultSet$FetchBuffer.nextRow(SQLServerResultSet.java:6357)
	 * at com.microsoft.sqlserver.jdbc.SQLServerResultSet.fetchBufferNext(SQLServerResultSet.java:1798)
	 * at com.microsoft.sqlserver.jdbc.SQLServerResultSet.next(SQLServerResultSet.java:1049)
	 * at org.hibernate.loader.Loader.processResultSet(Loader.java:997)
	 * at org.hibernate.loader.Loader.doQuery(Loader.java:959)
	 * at org.hibernate.loader.Loader.doQueryAndInitializeNonLazyCollections(Loader.java:351)
	 * at org.hibernate.loader.Loader.doList(Loader.java:2702)
	 * ... 53 more</pre>
	 *
	 * @param e
	 *
	 * @return
	 */
	private boolean isDeadlockError(Throwable e)
	{
		// Looking for PersistenceException, wrapping a LockAcquisitionException
		if (e != null && e instanceof PersistenceException)
		{
			e = e.getCause();

			if (e != null && e instanceof LockAcquisitionException)
			{
				return true;
			}
		}

		// Does not match
		return false;
	}


	private static boolean isInstanceOf(Exception e, Class<? extends Exception>[] classes)
	{
		for (Class<? extends Exception> type : classes)
		{
			if (type.isInstance(e))
				return true;
		}

		return false;
	}


	/**
	 * Complete the transaction
	 *  @param tx
	 * @param readOnly
	 * 		the read-only flag on the transaction (if true, the transaction will be rolled back, otherwise the transaction will be
	 */
	private final void complete(Transaction tx, boolean readOnly)
	{
		if (log.isTraceEnabled())
			log.trace("Complete {}", tx);

		if (!readOnly)
			tx.commit();
		else
			tx.rollback();
	}


	private final void rollback(Transaction tx)
	{
		if (log.isTraceEnabled())
			log.trace("Rollback {}", tx);

		tx.rollback();
	}


	private void rollback(Transaction tx, Exception e)
	{
		if (log.isDebugEnabled())
			log.debug("{} causes rollback", e.getClass().getSimpleName());

		rollback(tx);
	}

	private void restoreIsolationLevel(final Session session, final int isolationLevel, final String tracingId)
	{
		if (isolationLevel != IGNORE_ISOLATION_LEVEL)
		{
			session.doWork(conn -> {
				Tracing.logOngoing(tracingId, "TX:create", "Restore isolation level: ", isolationLevel);

				conn.setTransactionIsolation(isolationLevel);
			});
		}
	}
}
