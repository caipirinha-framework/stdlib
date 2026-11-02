package com.peterphi.std.guice.hibernate.module.logging;

import com.peterphi.std.util.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records SQL preparations from the current thread through Hibernate's
 * {@link org.hibernate.resource.jdbc.spi.StatementInspector}.
 */
public class HibernateSQLLogger implements Closeable, AutoCloseable, Consumer<String>
{
	private static final Logger log = LoggerFactory.getLogger(HibernateSQLLogger.class);

	private final List<String> statements = new ArrayList<>(0);
	private final HibernateObservingInterceptor interceptor;
	private final String tracingOperationId;

	public HibernateSQLLogger(HibernateObservingInterceptor interceptor, final String tracingOperationId)
	{
		this.interceptor = interceptor;
		this.tracingOperationId = tracingOperationId;
	}


	public void start()
	{
		interceptor.addThreadLocalSQLAuditor(this);
	}


	@Override
	public void accept(final String sql)
	{
		if (log.isTraceEnabled())
			log.trace("Hibernate executing SQL: {}", sql);

		if (tracingOperationId != null && Tracing.isVerbose())
			Tracing.logOngoing(tracingOperationId, "log:sql", sql);

		synchronized (statements)
		{
			statements.add(sql);
		}
	}


	@Override
	public void close()
	{
		interceptor.removeThreadLocalSQLAuditor(this);
	}


	/**
	 * Return a read-only copy of the statements prepared since this logger was started and before the logger was stopped; if the
	 * logger has not yet been stopped then it will return the statements prepared thus far
	 *
	 * @return
	 */
	public List<String> getAllStatements()
	{
		synchronized (statements)
		{
			return Collections.unmodifiableList(new ArrayList<>(statements));
		}
	}
}
