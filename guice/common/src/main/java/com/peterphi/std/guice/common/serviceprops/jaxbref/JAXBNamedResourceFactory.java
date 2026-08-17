package com.peterphi.std.guice.common.serviceprops.jaxbref;

import com.peterphi.std.crypto.digest.DigestHelper;
import com.peterphi.std.guice.common.lifecycle.GuiceLifecycleListener;
import com.peterphi.std.guice.common.serviceprops.composite.GuiceConfig;
import com.peterphi.std.util.jaxb.JAXBSerialiserFactory;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Supplier;

/**
 * The actual factory that constructs JAXB objects from configuration. Not user-facing.
 *
 * @param <T>
 */
class JAXBNamedResourceFactory<T>
{
	/**
	 * Don't reload any more often than once a minute
	 */
	private long maxReloadRate = 60 * 1000;

	private final GuiceConfig config;
	private final JAXBSerialiserFactory factory;

	private final String name;
	private final Class<T> clazz;

	/**
	 * Checksum of the raw content behind the cached value; used to avoid re-deserialising unchanged content
	 */
	private String lastChecksum = null;

	// Cache info for File value
	private File lastFile;
	private Long lastFileModified;

	/**
	 * Cache info for resolving the property value to a File / Resource.<br />
	 * If this is set and lastFile is set then an identically-named resource can be immediately mapped to lastFile.<br />
	 * If this is set and lastFile is null then this must be loaded as a classpath resource.
	 */
	private String lastResourceOrFile;

	// Reload rate-limiting
	private long lastLoaded = 0;

	private T cached = null;


	public JAXBNamedResourceFactory(final GuiceConfig config,
	                                final JAXBSerialiserFactory factory,
	                                final String name,
	                                final Class<T> clazz)
	{
		this.config = config;
		this.factory = factory;
		this.name = name;
		this.clazz = clazz;
	}


	/**
	 * Change the minimum period between attempts to reload a File/Resource backed value
	 *
	 * @param maxReloadRate the minimum number of milliseconds between reload attempts (use 0 to always attempt a reload)
	 */
	void setMaxReloadRate(final long maxReloadRate)
	{
		this.maxReloadRate = maxReloadRate;
	}


	public T get(T defaultValue)
	{
		String value = config.getRaw(name, null);

		if (value == null)
			return defaultValue;

		if (isLiteralXML(value))
		{
			return loadLiteralValue(value);
		}
		else
		{
			// If the value contains a variable reference then we should resolve them
			if (value.contains("${"))
			{
				value = config.get(name, null);

				// Now that the variables have been resolved the value might end up being a valid XML literal
				if (isLiteralXML(value))
					return loadLiteralValue(value);
			}

			// Try to load from disk (N.B. if we have a cached value, respect max reload rate)
			if (cached == null || maxReloadRate <= 0 || System.currentTimeMillis() > lastLoaded + maxReloadRate)
			{
				// The value must be a File or Resource reference!
				// Resolve the reference and check
				return loadFileOrResourceValue(value);
			}
			else
			{
				return cached;
			}
		}
	}


	/**
	 * Resolve this property reference to a deserialised JAXB value
	 *
	 * @return
	 */
	public T get()
	{
		T value = get(null);

		if (value == null)
			throw new RuntimeException("Missing property for JAXB resource: " + name);
		else
			return value;
	}


	/**
	 * Returns true if and only if the string is a literal XML value.<br />
	 * Since properties will not store a BOM the
	 *
	 * @param str
	 * @return
	 */
	private boolean isLiteralXML(final String str)
	{
		return (!str.isEmpty() && str.charAt(0) == '<');
	}


	/**
	 * Loads from a classpath resource or file. If the value can be resolved to a file then the file processing is cache-aware
	 * (based on the last modified time on the file)
	 *
	 * @param str
	 * @return
	 */
	private T loadFileOrResourceValue(final String str)
	{
		// Ideally we want to resolve str to a File so we can be smart about reloading it
		// N.B. Even if str is a classpath reference it's possible it could still be resolved to a file on disk
		// We can't be smart about reloading non-File classpath resources but we assume they will not change
		final File file;
		final URL resource;
		{
			// First consult the cache of str -> File (if present)
			if (lastFile != null && StringUtils.equals(str, lastResourceOrFile))
			{
				file = lastFile;
				resource = null;
			}
			else if (new File(str).exists())
			{
				file = new File(str);
				resource = null;
			}
			else
			{
				try
				{
					resource = getClass().getResource(str);

					if (resource == null)
						throw new IllegalArgumentException("JAXB config for " + name + ": no such file or resource: " + str);

					final URI uri = resource.toURI();

					if (StringUtils.equalsIgnoreCase(uri.getScheme(), "file"))
					{
						file = new File(uri);
					}
					else
					{
						file = null;
					}
				}
				catch (URISyntaxException e)
				{
					throw new IllegalArgumentException("Error processing classpath resource " +
					                                   str +
					                                   " from property " +
					                                   name +
					                                   ": URL to URI failed", e);
				}
			}
		}

		// Now resource the file or resource
		if (file == null)
		{
			assert (resource != null);

			// We reload the cached value ONLY if the resource path changes, we don't take into account the resolved URL
			if (cached == null || !StringUtils.equals(str, lastResourceOrFile))
			{
				lastResourceOrFile = str;

				return loadResourceValue(resource);
			}
			else
			{
				return cached;
			}
		}
		else
		{
			lastResourceOrFile = str;

			return loadFileValue(file);
		}
	}


	/**
	 * Load from a classpath resource.
	 * <p><b>This loader is cache-aware and will only reload if the resource's contents change</b></p>
	 *
	 * @param resource
	 * @return
	 */
	private T loadResourceValue(final URL resource)
	{
		final byte[] content;

		try (final InputStream is = resource.openStream())
		{
			content = is.readAllBytes();
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error loading JAXB resource " + name + " " + clazz + " from " + resource, e);
		}

		return load(checksum(content),
		            () -> clazz.cast(factory.getInstance(clazz).deserialise(new ByteArrayInputStream(content))));
	}


	/**
	 * Load from a file.
	 * <p><b>This loader is cache-aware: it will only re-read if the timestamp changes, and only re-deserialise if the contents
	 * change</b></p>
	 *
	 * @param file
	 * @return
	 */
	private T loadFileValue(final File file)
	{
		final boolean reload;

		// First time / first time with this file
		if (cached == null || lastFile == null || lastFileModified == null || !lastFile.equals(file))
		{
			lastFile = file;
			lastFileModified = file.lastModified();
			reload = true;
		}
		else // Same file, so check timestamp to decide on reload
		{
			final long modified = file.lastModified();

			reload = modified > lastFileModified;

			if (reload)
				lastFileModified = modified;
		}

		if (reload)
		{
			// N.B. the timestamp can change without the contents changing, so use a checksum to decide whether to re-deserialise
			final byte[] content;

			try
			{
				content = Files.readAllBytes(file.toPath());
			}
			catch (IOException e)
			{
				throw new RuntimeException("Error loading JAXB resource " + name + " " + clazz + " from " + file, e);
			}

			return load(checksum(content), () -> clazz.cast(factory.getInstance(clazz).deserialise(file)));
		}

		return cached;
	}


	/**
	 * Load a literal XML document stored in a config property.
	 * <p><b>This loader is cache-aware and will only reload if the literal XML value changes</b></p>
	 *
	 * @param str
	 * @return
	 */
	private T loadLiteralValue(final String str)
	{
		// Literal in-memory value
		return load(checksum(str.getBytes(StandardCharsets.UTF_8)),
		            () -> clazz.cast(factory.getInstance(clazz).deserialise(str)));
	}


	/**
	 * Shared logic for all the loaders: only re-deserialise if the content's checksum has changed since the last successful
	 * load.<br />
	 * This lets callers use reference equality to detect a config change
	 *
	 * @param checksum
	 * @param deserialiser
	 * @return
	 */
	private T load(final String checksum, final Supplier<T> deserialiser)
	{
		if (cached != null && StringUtils.equals(checksum, lastChecksum))
		{
			// Cached value is still valid!
			lastLoaded = System.currentTimeMillis();

			return cached;
		}

		cached = null; // Prevent the old value from being used
		lastChecksum = null; // N.B. only set again once the content has been successfully deserialised

		final T value = setCached(deserialiser.get());

		lastChecksum = checksum;

		return value;
	}


	private static String checksum(final byte[] content)
	{
		return DigestHelper.md5(content);
	}


	private T setCached(T value)
	{
		this.cached = value;
		lastLoaded = System.currentTimeMillis();

		// Probably a bit of a hack to reuse GuiceLifecycleListener here rather than a new JAXBLifecycle interface
		if (value instanceof GuiceLifecycleListener)
		{
			((GuiceLifecycleListener) value).postConstruct();
		}

		return value;
	}
}
