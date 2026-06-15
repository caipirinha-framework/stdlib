package com.peterphi.std.io;

import com.peterphi.std.system.exec.Exec;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Files;

/**
 * <p>
 * Title: File Helper
 * </p>
 * <p>
 * Description: File-related helper functions
 * </p>
 * <p>
 * Copyright: Copyright (c) 2006-2009
 * </p>
 * <p>
 * <p/>
 * </p>
 *
 * @version $Revision$
 */
public class FileHelper
{
	private static final transient Logger log = LoggerFactory.getLogger(FileHelper.class);


	private FileHelper()
	{
	} // Prevent instantiation


	/**
	 * Creates a temporary file name
	 *
	 * @return File
	 */
	public static File createTempFile(final String prefix, final String suffix)
	{
		try
		{
			final File tempFile = File.createTempFile(prefix, suffix);
			if (tempFile.exists())
			{
				if (!tempFile.delete())
					throw new RuntimeException("Could not delete new temp file: " + tempFile);
			}

			return tempFile;
		}
		catch (IOException e)
		{
			log.error("[FileHelper] {createTempFile} Error creating temp file: {}", e.getMessage(), e);
			return null;
		}
	}


	/**
	 * Ensures that a file is "safe" (this is done by whitelisting a small number of characters)<br />
	 * This method is mainly designed for producing a filename which bears some resemblance to a user-supplied String while
	 * removing the risk of being tricked
	 *
	 * @param filename
	 *
	 * @return
	 */
	public static String enforceSafeFilename(final String filename)
	{
		final char safeChar = '.';

		char[] chars = filename.toCharArray();

		for (int i = 0; i < chars.length; i++)
		{
			if (!isSafeFilenameCharacter(chars[i]))
				chars[i] = safeChar;
		}

		return String.valueOf(chars);
	}


	/**
	 * Determines if a character is a valid "safe filename" character (which we restrict to: alpha, digits, "-", "_" and ".")
	 *
	 * @param c
	 *
	 * @return
	 */
	private static boolean isSafeFilenameCharacter(char c)
	{
		if (Character.isLetterOrDigit(c))
		{
			return true;
		}
		else if (c == '-' || c == '_' || c == '.')
		{
			return true;
		}
		else
		{
			return false;
		}
	}


	public static boolean isAbsoluteFile(File f)
	{
		try
		{
			return f.isAbsolute();
		}
		catch (Throwable t)
		{
			return false;
		}
	}


	public static boolean isAbsoluteFile(String filename)
	{
		return isAbsoluteFile(new File(filename));
	}


	public static boolean isAncestor(File root, File f)
	{
		while (f != null && !f.equals(root))
			f = f.getParentFile();

		return f != null;
	}


	public static boolean couldWrite(final File f)
	{
		if (f == null)
			throw new IllegalArgumentException("Must specify a non-null file!");

		// If the file exists check if we can write to it
		// If the file doesn't exist, try to create it. If we can't then the caller won't be able to write to it if they try
		// If the file doesn't exist and the parent directory doesn't exist
		if (!f.exists())
		{
			try
			{
				if (f.getParentFile().exists())
				{
					try
					{
						FileHelper.write(f, "");

						// should always exist - test in case there was a silently dropped error
						return f.exists();
					}
					finally
					{
						if (!f.delete())
							log.warn("Could not delete file once written: {}", f);
					}
				}
				else
				{
					log.info("{couldWrite} Could not determine if the file is writable because the parent directory did not exist");

					return false;
				}
			}
			catch (IOException e)
			{
				log.warn("{couldWrite} Error while testing: {}", e.getMessage(), e);
				return false;
			}
		}
		else
		{
			return f.canWrite();
		}
	}


	public static void copy(File src, File dest) throws IOException
	{
		if (!src.exists())
			throw new FileNotFoundException("Cannot copy from non-existant source " +
			                                src.getAbsolutePath() +
			                                " to " +
			                                dest.getAbsolutePath());

		if (log.isInfoEnabled())
			log.info("[FileHelper] {copy} Copying {} to {}", src, dest);

		if (src.isDirectory())
		{
			FileUtils.copyDirectory(src, dest);
		}
		else
		{
			if (dest.exists())
			{
				delete(dest);
			}
			FileUtils.copyFile(src, dest);

			if (log.isTraceEnabled())
			{
				if (src.length() != dest.length())
				{
					log.trace("[FileHelper] {copy} src and dest have different sizes at the end of the copy process!");
				}
				else
				{
					log.trace("[FileHelper] {copy} Success: src and dest are identically sized");
				}
			}
		}
	}


	public static void move(File src, File dest) throws IOException
	{
		if (!src.exists())
			throw new FileNotFoundException("Cannot copy from non-existant source " +
			                                src.getAbsolutePath() +
			                                " to " +
			                                dest.getAbsolutePath());

		if (log.isInfoEnabled())
			log.info("[FileHelper] {move} Moving {} to {}", src, dest);

		if (!src.renameTo(dest))
		{ // Try a fast move (works if the files are on the same partition)
			if (src.isDirectory())
			{
				FileUtils.copyDirectory(src, dest);
				FileUtils.deleteDirectory(src);
			}
			else
			{
				FileUtils.copyFile(src, dest);
				src.delete();
			}
		}
	}


	public static boolean trySymlink(final File from, final File to)
	{
		try
		{
			symlink(from, to);

			return true;
		}
		catch (IOException e)
		{
			log.error("[FileHelper] {trySymlink} Failure: {}", e.getMessage(), e);

			return false;
		}
	}


	public static void symlink(final File from, final File to) throws IOException
	{
		try
		{
			if (!from.exists())
			{
				throw new FileNotFoundException("Symbolic link source does not exist: " + from);
			}

			// Delete the destination if necessary
			if (to.exists())
				if (!to.delete())
					throw new IOException("Could not delete file: " + to);

			// Launch the link process
			final int returnCode = Exec.utilityAs(null, "ln", "-s", from.toString(), to.toString()).waitForExit();

			if (returnCode != 0)
			{
				throw new IOException("ln command returned nonzero return code: " + returnCode);
			}
			else if (!to.exists())
			{
				throw new IOException("Symbolic link does not exist (but ln claimed successful execution)");
			}
			else if (!to.getCanonicalFile().equals(from.getCanonicalFile()))
			{
				throw new IOException("Link points to the wrong place (to.getCanonicalFile() != from.getCanonicalFile()");
			}
		}
		catch (IOException e)
		{
			log.error("[FileHelper] {symlink} Failure: {}", e.getMessage(), e);
			throw e;
		}
	}


	/**
	 * Safely moves a file from one place to another, ensuring the filesystem is left in a consistent state
	 *
	 * @param src
	 * 		File The source file
	 * @param dest
	 * 		File The destination file
	 *
	 * @return boolean True if the file has been completely moved to the new location, false if it is still in the original
	 * location
	 *
	 * @throws java.lang.SecurityException
	 * 		MAY BE THROWN if permission is denied to src or dest
	 * @deprecated use commons file utils FileUtils.moveDirectoryToDirectory instead
	 */
	@Deprecated
	public static boolean safeMove(File src, File dest) throws SecurityException
	{
		assert (src.exists());

		final boolean createDestIfNotExist = true;

		try
		{
			if (src.isFile())
				FileUtils.moveFile(src, dest);
			else
				FileUtils.moveDirectoryToDirectory(src, dest, createDestIfNotExist);

			return true;
		}
		catch (IOException e)
		{
			log.error("{safeMove} Error during move operation: {}", e.getMessage(), e);
			return false;
		}
	}


	/**
	 * Deletes a local file or directory from the filesystem
	 *
	 * @param f
	 * 		File The file/directory to delete
	 *
	 * @return boolean True if the deletion was a success, otherwise false
	 */
	public static boolean delete(File f) throws IOException
	{
		assert (f.exists());

		if (f.isDirectory())
		{
			FileUtils.deleteDirectory(f);
			return true;
		}
		else
		{
			return f.delete();
		}
	}


	/**
	 * Determines if 2 files or directories are equivalent by looking inside them
	 *
	 * @param one
	 * 		File The first file/directory
	 * @param two
	 * 		File The second file/directory
	 * @param checkName
	 * 		boolean Whether names should be identical also
	 *
	 * @return boolean True if the files/directories are equivalent, otherwise false
	 *
	 * @throws IOException
	 * 		On an unhandleable error or a non-file, non-directory input
	 */
	public static boolean smartEquals(File one, File two, boolean checkName) throws IOException
	{
		if (checkName)
		{
			if (!one.getName().equals(two.getName()))
			{
				return false;
			}
		}

		if (one.isDirectory() == two.isDirectory())
		{
			if (one.isDirectory())
			{
				File[] filesOne = one.listFiles();
				File[] filesTwo = two.listFiles();

				if (filesOne.length == filesTwo.length)
				{
					if (filesOne.length > 0)
					{
						for (int i = 0; i < filesOne.length; i++)
						{
							if (!smartEquals(filesOne[i], filesTwo[i], checkName))
							{
								return false;
							}
						}
						return true; // all subfiles are equal
					}
					else
					{
						return true;
					}
				}
				else
				{
					return false;
				}
			} // Otherwise, the File objects are Files
			else if (one.isFile() && two.isFile())
			{
				if (one.length() == two.length())
				{
					return FileUtils.contentEquals(one, two);
				}
				else
				{
					return false;
				}
			}
			else
			{
				throw new IOException("I don't know how to handle a non-file non-directory File: one=" + one + " two=" + two);
			}
		} // One is a directory and the other is not
		else
		{
			return false;
		}
	}


	/**
	 * Reads a PID (Process Id) file
	 *
	 * @param f
	 * 		File The process Id file (must exist!)
	 *
	 * @return String The process Id represented by the file (or -1 if the file doesn't exist)
	 *
	 * @throws IOException
	 * 		On filesystem-level errors
	 */
	public static long readPID(File f) throws IOException
	{
		return readPID(f, false);
	}


	/**
	 * Reads a PID (Process Id) file
	 *
	 * @param f
	 * 		File The process Id file (must exist!)
	 * @param carefulProcessing
	 * 		boolean If true, non-numeric chars are stripped from the PID before it is parsed
	 *
	 * @return String The process Id represented by the file (or -1 if the file doesn't exist)
	 *
	 * @throws IOException
	 * 		On filesystem-level errors
	 */
	public static long readPID(File f, boolean carefulProcessing) throws IOException
	{
		if (f.exists())
		{
			String pidString = cat(f);

			if (carefulProcessing)
				pidString = pidString.replaceAll("[^0-9]", ""); // Strip out anything that's not a number
			else
				pidString = pidString.replace("\n", ""); // Just remove newlines

			if (pidString.length() > 0)
				return Long.parseLong(pidString);
			else
				return -1;
		}
		else
		{
			return -1;
		}
	}


	public static void writePID(File f, long pid) throws IOException
	{
		writePID(f, Long.toString(pid));
	}


	public static void writePID(File f, String pid) throws IOException
	{
		assert (pid != null && pid.length() != 0) : "Must supply a valid PID!";
		write(f, pid);
	}


	public static void write(File f, String contents) throws IOException
	{
		write(f, new StringReader(contents));
	}


	public static void write(File f, StringBuilder sb) throws IOException
	{
		write(f, sb.toString());
	}


	public static void write(File f, Reader fr) throws IOException
	{
		assert (f != null) : "Must supply a file to write to!";
		assert (fr != null) : "Must supply a Reader to read from!";

		FileWriter fw = new FileWriter(f);

		try
		{
			char[] buffer = new char[4096];
			int size = 0;
			while ((size = fr.read(buffer)) != -1)
			{
				fw.write(buffer, 0, size);
			}
		}
		finally
		{
			fw.close();
			fr.close();
		}
	}


	public static String cat(String filename) throws IOException
	{
		return cat(new File(filename));
	}


	public static String cat(InputStream is) throws IOException
	{
		return cat(new InputStreamReader(is), 1024);
	}


	public static String cat(final URL u) throws IOException
	{
		return cat(u.openStream());
	}


	public static String cat(final Reader reader) throws IOException
	{
		return cat(reader, 1024);
	}


	public static String cat(final File f) throws IOException
	{
		FileInputStream fis = new FileInputStream(f);
		try
		{
			return cat(fis);
		}
		finally
		{
			fis.close();
		}
	}


	public static String cat(final Reader reader, final int sizeEstimate) throws IOException
	{
		assert (reader != null) : "Must provide a reader to read from!";

		try
		{
			StringBuilder sb = new StringBuilder(sizeEstimate);

			char[] buffer = new char[4096];
			int read = 0;

			while ((read = reader.read(buffer)) != -1)
			{
				sb.append(buffer, 0, read);
			}

			return sb.toString();
		}
		finally
		{
			reader.close();
		}
	}


	/**
	 * Creates/updates a file with the modify date set to now
	 *
	 * @param f
	 */
	public static void touch(File f) throws IOException
	{
		if (f.createNewFile())
		{
			// The modify time is already set
		}
		else
		{
			boolean success = f.setLastModified(System.currentTimeMillis());

			if (!success)
				log.warn("[FileHelper] {touch} Failed to update modify time on {}", f);
		}
	}


	/**
	 * Create a directory and any necessary parent directories, throwing a {@link RuntimeException} on failure
	 *
	 * @param f
	 * 		the directory to create
	 *
	 * @throws RuntimeException
	 * 		wrapping the inner IOException if the filesystem cannot create a directory
	 */
	public static void mkdirs(File f)
	{
		try
		{
			Files.createDirectories(f.toPath());
		}
		catch (IOException e)
		{
			throw new RuntimeException("Could not create directory: " + e.getMessage(), e);
		}
	}
}
