package com.peterphi.std.guice.web.servlet;

import com.peterphi.std.guice.apploader.GuiceApplication;
import com.peterphi.std.guice.apploader.impl.GuiceBuilder;
import com.peterphi.std.guice.apploader.impl.GuiceRegistry;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.setup.WebappGuiceRole;
import com.peterphi.std.util.tracing.Tracing;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basic Guice-aware servlet that is configured at invocation time and restarted using {@link GuiceRegistry}<br />
 * Feeds Servlet lifecycle events back to {@link GuiceRegistry} to perform orderly shutdown of services<br />
 * Overrides {@link HttpServlet#service} to perform lazy configuration. See the <code>doService</code> method if you need to
 * override <code>service</code>
 */
public abstract class GuiceServlet extends HttpServlet implements GuiceApplication
{
	private static final long serialVersionUID = 1L;

	private AtomicBoolean ready = new AtomicBoolean(false);
	private GuiceRegistry registry;


	@Override
	protected final void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
	{
		final HttpCallContext ctx = HttpCallContext.set(req, resp, getServletContext());

		try
		{
			// Share the call id to log4j
			Tracing.start(ctx.getLogId(), ctx.isVerbose());

			// If necessary set up Guice
			if (!ready.get())
			{
				if (registry == null)
					registry = new GuiceRegistry(new GuiceBuilder().withRole(new WebappGuiceRole(getServletConfig())));

				registry.register(this, true);
			}

			// Make the call
			doService(req, resp);
		}
		finally
		{
			HttpCallContext.clear();

			Tracing.clear();
		}
	}


	/**
	 * Calls {@link HttpServlet#service} should a subclass need to implement that method
	 *
	 * @param req
	 * @param resp
	 *
	 * @throws ServletException
	 * @throws IOException
	 */
	protected void doService(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException
	{
		super.service(req, resp);
	}


	@Override
	public final void configured()
	{
		ready.set(true);
	}


	@Override
	public final void destroy()
	{
		ready.set(false);

		super.destroy();

		// Feed shutdown to the GuiceRegistry
		if (registry != null)
			registry.stop();
	}


	@Override
	public abstract void stopping();
}
