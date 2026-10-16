package com.peterphi.std.io;

import com.peterphi.std.util.ListUtility;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class PropertyFile
{
	private static final transient Logger log = LoggerFactory.getLogger(PropertyFile.class);
	private static final String NEWLINE = "\n";
	private static final String COMMENT_INST = "#-- ";
	private static final char COMMENT_CHAR = '#';

	// The file to use for load() and save() methods
	protected File f;
	protected boolean readOnly = false;
	protected boolean caseSensitive = true;
	protected boolean forceNameValueDelimiterWhitespace = false;

	// Subclasses shouldn't have access to these
	protected List<Entry> entries = new ArrayList<Entry>();
	protected Map<String, NameValuePair> vars = new HashMap<String, NameValuePair>();


	public static PropertyFile find()
	{
		return find("service.properties");
	}


	public static PropertyFile find(final String fileName)
	{
		// Loading exact files
		try
		{
			if (fileName.startsWith("/"))
			{
				return PropertyFile.readOnly(new File(fileName));
			}
		}
		catch (IOException e)
		{
			throw new IllegalArgumentException("Property file could not be loaded: " + fileName);
		}

		return find(PropertyFile.class.getClassLoader(), fileName);
	}


	/**
	 * Find a property file
	 *
	 * @param classloader
	 * @param fileNames
	 * @return
	 */
	public static PropertyFile find(final ClassLoader classloader, final String... fileNames)
	{
		URL resolvedResource = null;
		String resolvedFile = null;

		for (String fileName : fileNames)
		{
			if (fileName.charAt(0) == '/')
			{
				File file = new File(fileName);

				if (file.exists())
				{
					try
					{
						return PropertyFile.readOnly(file);
					}
					catch (IOException e)
					{
						throw new IllegalArgumentException("Error loading property file: " +
						                                   fileName +
						                                   ". Error: " +
						                                   e.getMessage(), e);
					}
				}
			}
			else
			{
				// Try to resolve the filename (for logging any errors)
				final URL resource = classloader.getResource(fileName);

				if (resource != null)
				{
					resolvedFile = fileName;
					resolvedResource = resource;
					break;
				}
			}
		}

		if (resolvedFile == null)
		{
			if (fileNames.length == 1)
				throw new IllegalArgumentException("Error finding property file in classpath: " + fileNames[0]);
			else
				throw new IllegalArgumentException("Error finding property files in classpath: " + Arrays.asList(fileNames));
		}
		else if (log.isInfoEnabled())
			log.info("{find} Loading properties from {}", resolvedFile);

		return openResource(classloader, resolvedResource, resolvedFile);
	}


	public static PropertyFile fromString(final String contents)
	{
		return fromString(contents, "unknown");
	}


	public static PropertyFile fromString(final String contents, final String filename)
	{
		PropertyFile props = new PropertyFile();
		try
		{
			props.load(new StringReader(contents));
		}
		catch (IOException e)
		{

			throw new IllegalArgumentException("Error loading property file from string. Error: " + e.getMessage(), e);
		}

		return props;
	}


	public static PropertyFile openResource(final ClassLoader classloader, final URL resource, final String fileName)
	{
		try
		{
			if (log.isTraceEnabled())
				log.trace("{find} Resource search results: {}", resource);

			if (resource.getProtocol().equalsIgnoreCase("file"))
			{
				final String fileComponent = resource.getFile(); // Get the raw file component from the URL
				final String filePath = URLDecoder.decode(fileComponent, "UTF8"); // Decode any URL encoded values (e.g. spaces)

				final File file = new File(filePath);

				return PropertyFile.readOnly(file);
			}
			else
			{
				log.trace("{openResource} Falling back to opening resource as stream: PropertyFile filename will remain unknown");
				InputStream is = classloader.getResourceAsStream(fileName);

				PropertyFile props = new PropertyFile();
				props.load(is);

				return props;
			}
		}
		catch (IOException e)
		{
			throw new IllegalArgumentException("Error finding/loading property file: " + fileName + ". Error: " + e.getMessage(),
			                                   e);
		}
	}


	/**
	 * Find all property files by the name <code>name</code> by searching the classloader that owns PropertyFile
	 *
	 * @param name
	 * @return
	 */
	public static PropertyFile[] findAll(final String name)
	{
		return findAll(PropertyFile.class.getClassLoader(), name);
	}


	/**
	 * Find all property files by the name <code>name</code> by searching the specified classloader
	 *
	 * @param loader
	 * @param name
	 * @return
	 */
	public static PropertyFile[] findAll(ClassLoader loader, final String name)
	{
		try
		{
			final Enumeration<URL> urls = loader.getResources(name);

			List<PropertyFile> files = new ArrayList<PropertyFile>();

			for (URL url : ListUtility.iterate(urls))
			{
				InputStream is = null;
				try
				{
					is = url.openStream();
					final PropertyFile file = new PropertyFile(is);

					files.add(file);
				}
				catch (IOException e)
				{
					throw new RuntimeException("Error loading properties from " +
					                           url +
					                           " for name: " +
					                           name +
					                           ": " +
					                           e.getMessage(), e);
				}
				finally
				{
					IOUtils.closeQuietly(is);
				}
			}

			return files.toArray(new PropertyFile[0]);
		}
		catch (IOException e)
		{
			throw new RuntimeException("Error loading properties for name: " + name + ": " + e.getMessage(), e);
		}
	}

	// ///////////////////////
	// CONSTRUCTORS
	// ///////////////


	public PropertyFile(File fileName) throws IOException
	{
		this(fileName, true);
	}


	public PropertyFile(File fileName, boolean caseSensitive) throws IOException
	{
		setFile(fileName);
		this.caseSensitive = caseSensitive;

		load();
	}


	public PropertyFile()
	{
	}


	public PropertyFile(URL url) throws IOException
	{
		this(url.openStream());
	}


	public PropertyFile(InputStream is, boolean caseSensitive) throws IOException
	{
		this.caseSensitive = caseSensitive;

		load(is);
	}


	public PropertyFile(Reader r) throws IOException
	{
		this(r, true);
	}


	public PropertyFile(Reader r, boolean caseSensitive) throws IOException
	{
		this.caseSensitive = caseSensitive;

		load(r);
	}


	public PropertyFile(InputStream is) throws IOException
	{
		this(is, true);
	}


	public PropertyFile(Properties p)
	{
		this(p, false);
	}


	public PropertyFile(Properties p, boolean caseSensitive)
	{
		this.caseSensitive = caseSensitive;

		load(p);
	}


	public PropertyFile(final Map<String, String> map)
	{
		load(map);
	}


	/**
	 * Converts the name/value pairs stored in this PropertyFile to the Java Properties propertyfile type
	 *
	 * @return a Properties representing the data (but not the comments, etc) in this file
	 */
	public Properties toProperties()
	{
		Properties p = new Properties();

		for (NameValuePair nvp : vars.values())
		{
			p.put(nvp.name, nvp.value);
		}

		return p;
	}


	/**
	 * Converts the name/value pairs stored in this PropertyFile to a Map of key/value pairs
	 *
	 * @return a Map representing the data (but not the comments, etc) in this file
	 */
	public Map<String, String> toMap()
	{
		Map<String, String> map = new HashMap<>(vars.size());

		for (NameValuePair nvp : vars.values())
			map.put(nvp.name, nvp.value);

		return map;
	}


	public boolean getCaseSensitive()
	{
		return caseSensitive;
	}


	public File getFile()
	{
		return f;
	}


	public void setFile(File file)
	{
		this.f = file;
	}


	public void setForceNameValueDelimiterWhitespace(boolean value)
	{
		this.forceNameValueDelimiterWhitespace = value;
	}


	public boolean getForceNameValueDelimiterWhitespace()
	{
		return this.forceNameValueDelimiterWhitespace;
	}

	// //////////////////////////////
	// READING AND WRITING
	// //////////////////////


	public void load() throws IOException
	{
		assert (f != null);

		load(f);
	}


	public void load(Map<String, String> map)
	{
		for (Map.Entry<String, String> entry : map.entrySet())
			set(entry.getKey(), entry.getValue());
	}


	public void load(Properties p)
	{
		entries.clear();
		vars.clear();

		// Properties already contains decoded strings
		for (String name : p.stringPropertyNames())
		{
			NameValuePair nvp = new NameValuePair(name, p.getProperty(name));
			entries.add(nvp);
			vars.put(caseSensitive ? name : name.toLowerCase(), nvp);
		}

		hook_loaded();
	}


	public void load(File file) throws IOException
	{
		assert (file != null);

		load(new FileReader(file));
	}


	public void load(InputStream is) throws IOException
	{
		load(new InputStreamReader(is));
	}


	public void save() throws IOException
	{
		save(f, null);
	}


	public void save(File f, String comment) throws IOException
	{
		assert (f != null);

		FileWriter fw = new FileWriter(f);
		try
		{
			save(comment, fw);
		}
		finally
		{
			fw.close();
		}
	}


	public void save(OutputStream os) throws IOException
	{
		save(null, new OutputStreamWriter(os));
	}


	public void save(String comment) throws IOException
	{
		save(f, comment);
	}


	public void save(String comment, OutputStream os) throws IOException
	{
		save(comment, new OutputStreamWriter(os));
	}

	// ///////////////////////////////
	// MANIPULATION
	// ////////////////////////////


	/**
	 * Make this PropertyFile object read-only
	 */
	public void makeReadOnly()
	{
		this.readOnly = true;
	}


	public boolean containsKey(final String name)
	{
		return _contains(name);
	}


	public String get(final String name)
	{
		return get(name, null);
	}


	public String get(final String name, final String defaultValue)
	{
		return _get(name, defaultValue);
	}


	public int get(final String name, final int defaultValue)
	{
		final String val = get(name, null);

		if (val != null)
			return Integer.parseInt(val.trim());
		else
			return defaultValue;
	}


	public long get(final String name, final long defaultValue)
	{
		final String val = get(name, null);

		if (val != null)
			return Long.parseLong(val.trim());
		else
			return defaultValue;
	}


	public boolean get(final String name, final boolean defaultValue)
	{
		return Boolean.parseBoolean(get(name, Boolean.toString(defaultValue)).trim());
	}


	public boolean getBoolean(final String name, final boolean defaultValue)
	{
		return parseBoolean(get(name, Boolean.toString(defaultValue)).trim());
	}


	private static boolean parseBoolean(final String value)
	{
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on"))
			return true;
		else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("off"))
			return false;
		else
			throw new IllegalArgumentException("Cannot parse to a boolean value: " + value);
	}


	public URI getURI(final String name, final String defaultValue)
	{
		String uri = get(name, null);

		if (uri == null && defaultValue == null)
			return null;
		else if (uri == null)
			uri = defaultValue;

		try
		{
			return new URI(uri.trim());
		}
		catch (URISyntaxException e)
		{
			throw new IllegalArgumentException("Illegal URI: " + uri, e);
		}
	}


	public String set(final String name, final String value)
	{
		return _set(name, value);
	}


	public String set(final String name, final long value)
	{
		return set(name, Long.toString(value));
	}


	public String set(final String name, final int value)
	{
		return set(name, Integer.toString(value));
	}


	public void remove(final String name)
	{
		_rm(name);
	}


	public void clear()
	{
		_clear();
	}

	// //////////////////////
	// ITERATORS
	// ////////////////


	public Set<String> keySet()
	{
		return vars.keySet();
	}


	public Iterator<String> keyIterator()
	{
		return vars.keySet().iterator();
	}


	public int getLineCount()
	{
		return entries.size();
	}


	public int getVariableCount()
	{
		return vars.size();
	}

	// ////////////////////////
	// CALLBACKS
	// ///////////////

	protected boolean dirty = false;


	protected void hook_changed(final String name, final String oldValue, final String newValue)
	{
		dirty = true;
	}


	protected void hook_added(final String name, final String newValue)
	{
		dirty = true;
	}


	protected void hook_removed(final String name)
	{
		dirty = true;
	}


	protected void hook_cleared()
	{
		dirty = true;
	}


	protected void hook_loaded()
	{
		dirty = false;
	}


	protected void hook_saved()
	{
		dirty = false;
	}

	// ////////////////////////////////////////
	// LOADING AND SAVING PRIMITIVES
	// /////////////////////////////////


	public void load(final Reader r) throws IOException
	{
		try (BufferedReader in = new BufferedReader(r))
		{
			entries.clear();
			vars.clear();

			String line = in.readLine();
			while (line != null)
			{
				// Parse the line

				if (line.length() == 0)
				{
					entries.add(new BlankLine());
				}
				else
				{
					char c = line.charAt(0);

					if (c == COMMENT_CHAR)
					{
						if (!line.startsWith(COMMENT_INST))
						{ // Don't load instance comments
							entries.add(new Comment(line.substring(1)));
						}
					}
					else if (c == ' ')
					{
						// If it starts with a space let's call it a blank line
						entries.add(new BlankLine());
					}
					else
					{ // Name=Value pair
						// TODO - handle escaped characters (notably, \= and \n)
						final int equalsIndex = line.indexOf('=');

						if (equalsIndex != -1)
						{
							String name = line.substring(0, equalsIndex).trim();
							String value = line.substring(equalsIndex + 1);

							NameValuePair nvp = new NameValuePair(name, value);
							if (vars.containsKey(nvp.name))
							{
								log.warn("{load} duplicate entry '{}': overwriting previous value", nvp.name);
							}

							entries.add(nvp);
							vars.put(caseSensitive ? nvp.name : nvp.name.toLowerCase(), nvp);
						}
						else
						{
							log.error("Malformed line in property file: {}", line);
						}
					}
				}

				line = in.readLine();
			}

			hook_loaded();
		}
	}


	public void save(final String comment, final Writer w) throws IOException
	{
		// Write all properties to the file
		try
		{
			if (comment != null)
			{
				w.append(COMMENT_INST);
				w.append(comment);
				w.append(NEWLINE);

				w.append(COMMENT_INST);
				w.append("at ");
				w.append(Calendar.getInstance().getTime().toString());
				w.append(NEWLINE);
			}

			int sz = entries.size();
			for (int i = 0; i < sz; i++)
			{
				entries.get(i).append(w, this);
			}

			hook_saved();
		}
		finally
		{
			if (w != null)
				w.close();
		}
	}

	// ///////////////////////////////////////////////
	// INTERNAL MANIPULATION PRIMITIVES
	// ////////////////////////////////////


	protected void _clear()
	{
		if (readOnly)
			throw new UnsupportedOperationException("Cannot modify a read-only collection");

		entries.clear();
		vars.clear();

		hook_cleared();
	}


	protected String _set(String name, final String value)
	{
		if (readOnly)
			throw new UnsupportedOperationException("Cannot modify a read-only collection");

		assert (name != null);
		name = name.trim();

		NameValuePair nvp = vars.get(caseSensitive ? name : name.toLowerCase());

		if (nvp != null)
		{
			String oldValue = nvp.value;
			nvp.value = value;

			hook_changed(name, oldValue, value);

			return oldValue;
		}
		else
		{ // nvp == null
			// Add a new nvp
			nvp = new NameValuePair(name, value);
			entries.add(nvp);
			vars.put(caseSensitive ? name : name.toLowerCase(), nvp);

			hook_added(name, value);

			return null; // no previous value
		}
	}


	protected String _get(String name, final String defaultValue)
	{
		assert (name != null);
		name = name.trim();

		return _get_core(name, defaultValue);
	}


	protected String _get_core(final String name, final String defaultValue)
	{
		NameValuePair nvp = vars.get(caseSensitive ? name : name.toLowerCase());

		if (nvp == null)
		{
			return defaultValue;
		}
		else
		{
			if (forceNameValueDelimiterWhitespace && nvp.value.length() > 0 && nvp.value.charAt(0) == ' ')
				return nvp.value.substring(1);
			else
				return nvp.value;
		}
	}


	protected boolean _contains(String name)
	{
		assert (name != null);
		name = name.trim();

		return vars.containsKey(caseSensitive ? name : name.toLowerCase());
	}


	protected void _rm(String name)
	{
		assert (name != null);
		name = name.trim();

		NameValuePair nvp = vars.get(caseSensitive ? name : name.toLowerCase());

		vars.remove(caseSensitive ? name : name.toLowerCase());
		entries.remove(nvp);

		hook_removed(name);
	}


	/**
	 * Creates a read-only version of a property file<br />
	 * Fails if the file does not exist.
	 *
	 * @param filename the filename to load
	 * @return
	 * @throws IOException if the file cannot be loaded
	 */
	public static PropertyFile readOnly(final File filename) throws IOException
	{
		final PropertyFile props = new PropertyFile(filename);

		props.readOnly = true;

		return props;
	}


	////////////////////////////////////
	// Property File entry types

	/// /////////////////////////////////


	protected abstract class Entry
	{
		public abstract void append(Writer w, PropertyFile p) throws IOException;
	}

	protected class BlankLine extends Entry
	{
		@Override
		public void append(Writer w, PropertyFile p) throws IOException
		{
			w.append(NEWLINE);
		}
	}

	protected class Comment extends Entry
	{
		public String data;


		public Comment(String data)
		{
			this.data = data;
		}


		@Override
		public void append(Writer w, PropertyFile p) throws IOException
		{
			w.append(COMMENT_CHAR);
			w.append(data);
			w.append(NEWLINE);
		}
	}

	protected class NameValuePair extends Entry
	{
		private boolean unusualName;
		public String name;
		public String value;


		private void checkName()
		{
			if (name.contains("="))
			{
				unusualName = true;
			}
			else
			{
				unusualName = false;
			}
		}


		public NameValuePair(String name, String value)
		{
			this.name = name;
			this.value = value;

			checkName();
		}


		@Override
		public void append(Writer w, PropertyFile p) throws IOException
		{
			if (!unusualName)
			{
				w.append(name);
			}
			else
			{
				// TODO - escape the name
				log.warn("[NameValuePair] {append} name needs escapingbut this implementation doesn't escape");
				w.append(name);
			}

			if (forceNameValueDelimiterWhitespace && !name.endsWith(" "))
				w.append(" ");
			w.append("=");
			if (forceNameValueDelimiterWhitespace && !value.startsWith(" "))
				w.append(" ");
			w.append(value);
			w.append(NEWLINE);
		}
	}
}
