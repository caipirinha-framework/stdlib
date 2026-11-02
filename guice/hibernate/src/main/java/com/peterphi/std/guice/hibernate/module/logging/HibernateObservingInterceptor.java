package com.peterphi.std.guice.hibernate.module.logging;

import com.google.inject.Singleton;
import com.peterphi.std.util.tracing.Tracing;
import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Observes SQL through Hibernate's {@link StatementInspector} without altering statements.
 */
@Singleton
public class HibernateObservingInterceptor implements StatementInspector
{
	private final ThreadLocal<List<Consumer<String>>> onPrepareStatementWatchers = new ThreadLocal<>();


	@Override
	public String inspect(final String sql)
	{
		// Notify the observers
		onPrepareStatement(sql);

		// Return unmodified
		return sql;
	}


	private void onPrepareStatement(String sql)
	{
		final List<Consumer<String>> consumers = onPrepareStatementWatchers.get();

		if (consumers != null && !consumers.isEmpty())
		{
			for (Consumer<String> consumer : consumers)
			{
				consumer.accept(sql);
			}
		}
		else if (Tracing.isVerbose())
		{
			// Nobody else is watching SQL, so we're responsible for letting the Tracer know about it
			Tracing.log("sql:prepare", sql);
		}
	}


	/**
	 * Start a new logger which records SQL Prepared Statements created by Hibernate in this Thread. Must be closed (or treated as
	 * autoclose)
	 *
	 * @return
	 */
	public HibernateSQLLogger startSQLLogger()
	{
		return startSQLLogger(null);
	}


	/**
	 * Start a new logger which records SQL Prepared Statements created by Hibernate in this Thread. Must be closed (or treated as
	 * autoclose)
	 *
	 * @param tracingOperationId the tracing operation this sql logger should be associated with
	 * @return
	 */
	public HibernateSQLLogger startSQLLogger(final String tracingOperationId)
	{
		final HibernateSQLLogger logger = new HibernateSQLLogger(this, tracingOperationId);

		logger.start();

		return logger;
	}


	/**
	 * Add an observer to <code>onPrepareStatement</code> calls made by the current Thread until {@link
	 * #clearThreadLocalObservers()} is
	 * called
	 *
	 * @param observer
	 */
	public void addThreadLocalSQLAuditor(Consumer<String> observer)
	{
		List<Consumer<String>> observers = onPrepareStatementWatchers.get();

		if (observers == null)
		{
			observers = new CopyOnWriteArrayList<>();
			onPrepareStatementWatchers.set(observers);
		}

		observers.add(observer);
	}


	/**
	 * Remove a previously added observer for <code>onPrepareStatement</code> calls
	 *
	 * @param observer
	 */
	public void removeThreadLocalSQLAuditor(Consumer<String> observer)
	{
		List<Consumer<String>> observers = onPrepareStatementWatchers.get();

		if (observers == null)
			return;

		observers.remove(observer);

		if (observers.isEmpty())
			onPrepareStatementWatchers.remove();
	}


	/**
	 * Clear any Thread Local observers
	 */
	public void clearThreadLocalObservers()
	{
		onPrepareStatementWatchers.remove();
	}
}
