package com.peterphi.std.guice.web.rest.resteasy;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.peterphi.std.guice.apploader.GuiceApplication;
import com.peterphi.std.guice.apploader.GuiceProperties;
import com.peterphi.std.guice.apploader.impl.GuiceRegistry;
import com.peterphi.std.guice.common.jackson.JacksonFactory;
import com.peterphi.std.guice.common.metrics.GuiceMetricNames;
import com.peterphi.std.guice.restclient.jaxb.RestFailure;
import com.peterphi.std.guice.restclient.resteasy.impl.jackson.Jackson2Provider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlRootElementProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.JAXBXmlTypeProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset.FastInfosetXmlRootElementProvider;
import com.peterphi.std.guice.restclient.resteasy.impl.jaxb.fastinfoset.FastInfosetXmlTypeProvider;
import com.peterphi.std.guice.serviceregistry.rest.RestResource;
import com.peterphi.std.guice.serviceregistry.rest.RestResourceRegistry;
import com.peterphi.std.guice.web.HttpCallContext;
import com.peterphi.std.guice.web.rest.jaxrs.exception.JAXRSExceptionMapper;
import com.peterphi.std.guice.web.rest.jaxrs.exception.RestFailureMarshaller;
import com.peterphi.std.guice.web.rest.pagewriter.BootstrapRestFailurePageRenderer;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import com.peterphi.std.util.tracing.Tracing;
import com.peterphi.std.util.tracing.TracingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.jboss.resteasy.plugins.server.servlet.ListenerBootstrap;
import org.jboss.resteasy.plugins.server.servlet.ServletContainerDispatcher;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.UnhandledException;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A resteasy dispatcher (that can be brought up/down) that brings a Guice-enabled environment to Resteasy
 */
class GuicedResteasy implements GuiceApplication
{
	private static final Logger log = LoggerFactory.getLogger(GuicedResteasy.class);

	private final GuiceRegistry registry;
	private final AtomicBoolean registered = new AtomicBoolean(false);
	private final FilterConfig filterConfig;
	private final ServletConfig servletConfig;
	private final ServletContext context;
	private final ListenerBootstrap bootstrap;
	private final boolean handleNotFoundException;

	// Lazy-created on first call
	private ServletContainerDispatcher dispatcher;

	@Inject
	private JAXRSExceptionMapper exceptionMapper;

	@Inject
	private Injector injector;

	private Timer httpCalls;
	private Meter httpExceptions;
	private Meter httpNotFoundExceptions;
	private Meter ignoredAborts;

	@Inject(optional = true)
	@Named(GuiceProperties.SUPPRESS_CLIENT_ABORT_EXCEPTIONS)
	boolean suppressClientAbortExceptions = true;

	@Inject(optional = true)
	@Named(GuiceProperties.HTTP_REQUESTS_DEFAULT_TO_UTF_8)
	boolean forceUTF8DefaultCharset = true;

	@Inject(optional = true)
	@Named(GuiceProperties.HTTP_RENAME_THREADS)
	boolean renameThreads = false;

	/**
	 * Allow to be overridden but have a default implementation
	 */
	@Inject(optional = true)
	DefaultHttpRequestCharsetHelper requestCharsetHelper = new DefaultHttpRequestCharsetHelper();

	@Inject
	JAXBSerialiserFactory serialiserFactory;

	@Inject
	JacksonFactory jacksonFactory;


	public GuicedResteasy(final GuiceRegistry registry,
	                      final ServletConfig config,
	                      final ListenerBootstrap bootstrap,
	                      final boolean handleNotFoundException)
	{
		this.registry = registry;
		this.filterConfig = null;
		this.servletConfig = config;
		this.context = config.getServletContext();
		this.bootstrap = bootstrap;
		this.handleNotFoundException = handleNotFoundException;
	}


	public GuicedResteasy(final GuiceRegistry registry,
	                      final FilterConfig config,
	                      final ListenerBootstrap bootstrap,
	                      final boolean handleNotFoundException)
	{
		this.registry = registry;
		this.filterConfig = config;
		this.servletConfig = null;
		this.context = config.getServletContext();
		this.bootstrap = bootstrap;
		this.handleNotFoundException = handleNotFoundException;
	}


	/**
	 * Return the path of the local webapp (if known)
	 *
	 * @return
	 */
	public String getWebappPath()
	{
		return context.getContextPath();
	}


	public void initialise() throws ServletException
	{
		// Try to start guice (unless already started)
		getDispatcher();
	}


	private synchronized ServletContainerDispatcher getDispatcher() throws ServletException
	{
		if (!registered.get())
		{
			dispatcher = new ServletContainerDispatcher();

			configure(dispatcher);

			registered.set(true);
		}

		return dispatcher;
	}


	public void call(HttpServletRequest request,
	                 HttpServletResponse response) throws ServletException, IOException, NotFoundException
	{
		final HttpCallContext ctx = HttpCallContext.set(request, response, context);

		if (forceUTF8DefaultCharset && requestCharsetHelper != null)
			requestCharsetHelper.applyDefaultCharset(request);

		Timer.Context timer = null;

		if (httpCalls != null)
			timer = httpCalls.time();

		final String oldThreadName;
		final Thread thread;

		if (ctx.isVerbose() || renameThreads)
		{
			thread = Thread.currentThread();
			oldThreadName = thread.getName();
		}
		else {
			thread = null;
			oldThreadName = null;
		}

		try
		{
			if (thread != null && oldThreadName != null)
				thread.setName(ctx.getLogId() + " " + ctx.getRequestInfo());

			// Share the call id to log4j
			Tracing.start(ctx.getLogId(), ctx.isVerbose());

			// Share the call id to log4j
			MDC.put(TracingConstants.MDC_HTTP_REMOTE_ADDR, ctx.getRequest().getRemoteAddr());
			MDC.put(TracingConstants.MDC_SERVLET_CONTEXT_PATH, ctx.getServletContext().getContextPath());
			MDC.put(TracingConstants.MDC_HTTP_REQUEST_URI, ctx.getRequest().getRequestURI());

			// Add the session id (if present)
			final HttpSession session = ctx.getRequest().getSession(false);
			if (session != null)
			{
				MDC.put(TracingConstants.MDC_HTTP_SESSION_ID, session.getId());
			}

			try
			{
				// Optionally log the request
				if (log.isDebugEnabled())
					log.debug(ctx.getRequestInfo());

				// Get or create the dispatcher
				ServletContainerDispatcher dispatcher = getDispatcher();

				dispatcher.service(request.getMethod(), request, response, handleNotFoundException);
			}
			catch (NotFoundException e)
			{
				if (httpNotFoundExceptions != null)
					httpNotFoundExceptions.mark();

				throw e; // let the caller handle this
			}
			catch (ServletException | IOException | RuntimeException | Error e)
			{
				if (httpExceptions != null)
					httpExceptions.mark();

				tryHandleException(ctx, response, e); // try to pretty print, otherwise rethrow
			}
		}
		finally
		{
			if (timer != null)
				timer.stop();

			HttpCallContext.clear();
			Tracing.stop(ctx.getLogId());
			MDC.clear();

			if (oldThreadName != null && thread != null)
				thread.setName(oldThreadName);
		}
	}


	/**
	 * @param ctx
	 * @param response
	 * @param t
	 *
	 * @throws ServletException
	 * @throws IOException
	 * @throws RuntimeException
	 * @throws Error
	 */
	private void tryHandleException(HttpCallContext ctx,
	                                HttpServletResponse response,
	                                Throwable t) throws ServletException, IOException, RuntimeException, Error
	{
		if (suppressClientAbortExceptions && t instanceof UnhandledException && t.getCause() != null)
		{
			// Only look 20 exceptions deep
			int maxDepth = 20;
			Throwable cause = t.getCause();

			while (cause != null && --maxDepth > 0)
			{
				if (cause.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException"))
				{
					ignoredAborts.mark();

					if (log.isTraceEnabled())
						log.trace("Client aborted during request. Ignoring. Detail: {}", ctx.getRequestInfo(), t);

					return;
				}
				else
				{
					cause = cause.getCause();
				}
			}
		}

		log.warn("Failure during {}", ctx.getRequestInfo(), t);

		// If the response is already committed we can't render the exception elegantly
		if (response.isCommitted())
			rethrow(t);

		try
		{
			RestFailureMarshaller marshaller = new RestFailureMarshaller();
			RestFailure failure = marshaller.renderFailure(t);

			BootstrapRestFailurePageRenderer renderer = new BootstrapRestFailurePageRenderer(failure);

			response.setStatus(500); // internal error

			// Render the HTML
			final StringBuilder sb = new StringBuilder(4096);
			renderer.writeHTML(sb);

			response.getWriter().print(sb);
		}
		catch (Throwable newException)
		{
			log.warn("Error trying to present exception elegantly: ", newException);

			// Rethrow the original exception, it's not our problem anymore
			rethrow(t);
		}
	}


	private void rethrow(Throwable t) throws ServletException, IOException, RuntimeException, Error
	{
		// Rethrow (ugly code, unfortunately)
		if (t instanceof RuntimeException)
			throw (RuntimeException) t;
		else if (t instanceof Error)
			throw (Error) t;
		else if (t instanceof IOException)
			throw (IOException) t;
		else if (t instanceof ServletException)
			throw (ServletException) t;
		else
			throw new RuntimeException(t);
	}


	/**
	 * Try to initialise a ServletContainerDispatcher with the connection to the Guice REST services
	 */
	protected void configure(ServletContainerDispatcher dispatcher) throws ServletException
	{
		// Make sure we are registered with the Guice registry
		registry.register(this, true);

		// Configure the dispatcher
		final Registry resteasyRegistry;
		final ResteasyProviderFactory providerFactory;
		{
			final ResteasyRequestResponseFactory converter = new ResteasyRequestResponseFactory(dispatcher);

			dispatcher.init(context, bootstrap, converter, converter);

			if (filterConfig != null)
				dispatcher.getDispatcher().getDefaultContextObjects().put(FilterConfig.class, filterConfig);
			if (servletConfig != null)
				dispatcher.getDispatcher().getDefaultContextObjects().put(ServletConfig.class, servletConfig);

			resteasyRegistry = dispatcher.getDispatcher().getRegistry();
			providerFactory = dispatcher.getDispatcher().getProviderFactory();
		}


		// Register the REST provider classes
		for (Class<?> providerClass : ResteasyProviderRegistry.getClasses())
		{
			log.debug("Registering REST providers: {}", providerClass.getName());
			providerFactory.registerProvider(providerClass);
		}

		// Register the REST provider singletons
		for (Object provider : ResteasyProviderRegistry.getSingletons())
		{
			log.debug("Registering REST provider singleton: {}", provider);
			providerFactory.registerProviderInstance(provider);
		}

		// Set up JAXB providers
		{
			providerFactory.registerProviderInstance(new FastInfosetXmlRootElementProvider<>(serialiserFactory));
			providerFactory.registerProviderInstance(new JAXBXmlRootElementProvider<>(serialiserFactory));

			// Set up providers for XmlType-annotated and JAXBElement entities
			// These providers will share the same underlying serialiser map
			final JAXBXmlTypeProvider<Object> xmlTypeProvider = new JAXBXmlTypeProvider<>(serialiserFactory);
			providerFactory.registerProviderInstance(new FastInfosetXmlTypeProvider<>(xmlTypeProvider));
			providerFactory.registerProviderInstance(xmlTypeProvider);
		}

		// Set up Jackson
		{
			providerFactory.registerProviderInstance(new Jackson2Provider(jacksonFactory));
		}

		// Register the exception mapper
		{
			// Register the ExceptionMapper for ApplicationException
			providerFactory.register(this.exceptionMapper);

			log.trace("ExceptionMapper registered for ApplicationException");
		}

		// Register the REST resources
		for (RestResource resource : RestResourceRegistry.getResources())
		{
			log.debug("Registering REST resource: {}", resource.getResourceClass().getName());

			resteasyRegistry.addResourceFactory(new ResteasyGuiceResource(injector, resource.getResourceClass()));
		}
	}


	@Override
	public void configured()
	{
		// Called when guice config is complete
		log.trace("Guice injector online");

		final MetricRegistry registry = injector.getInstance(MetricRegistry.class);
		this.httpNotFoundExceptions = registry.meter(GuiceMetricNames.HTTP_404_EXCEPTIONS_METER);
		this.httpExceptions = registry.meter(GuiceMetricNames.HTTP_EXCEPTIONS_METER);
		this.httpCalls = registry.timer(GuiceMetricNames.HTTP_CALLS_TIMER);
		this.ignoredAborts = registry.meter(GuiceMetricNames.HTTP_IGNORED_CLIENT_ABORTS);
	}


	@Override
	public void stopping()
	{
		log.trace("Guice injector stopping...");
		registered.set(false);

		if (dispatcher != null)
		{
			dispatcher.destroy();
			dispatcher = null;
		}
	}


	public void stop()
	{
		registry.stop();
	}
}
