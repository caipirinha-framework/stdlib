package com.peterphi.std.crypto.digest;

import com.peterphi.std.util.HexHelper;
import org.apache.commons.io.IOUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * Where possible implementations should use DigestUtils from commons-codec
 */
public class DigestHelper
{
	public static final String SHA1 = "SHA1";
	public static final String MD5 = "MD5";


	/**
	 * Performs HMAC-SHA1 on the UTF-8 byte representation of strings, returning the hexidecimal hash as a result
	 *
	 * @param key
	 * @param plaintext
	 * @return
	 */
	public static String sha1hmac(String key, String plaintext)
	{
		byte[] signature = sha1hmac(key.getBytes(), plaintext.getBytes());

		return encode(signature);
	}


	/**
	 * @param key
	 * @param text
	 * @return
	 * @throws IllegalArgumentException
	 */
	public static byte[] sha1hmac(byte[] key, byte[] text) throws IllegalArgumentException
	{
		try
		{
			final SecretKey sk = new SecretKeySpec(key, "HMACSHA1");
			final Mac m = Mac.getInstance(sk.getAlgorithm());

			m.init(sk);
			return m.doFinal(text);
		}
		catch (InvalidKeyException | NoSuchAlgorithmException e)
		{
			throw new IllegalArgumentException(e);
		}
	}


	public static byte[] sha1(byte[] plaintext) throws NoSuchAlgorithmException
	{
		MessageDigest md = MessageDigest.getInstance("SHA1");

		return md.digest(plaintext);
	}


	public static String sha1(String plaintext) throws IOException, NoSuchAlgorithmException
	{
		try (ByteArrayInputStream is = new ByteArrayInputStream(plaintext.getBytes()))
		{
			return sha1(is);
		}
	}


	public static String sha1(File testFile) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		try (FileInputStream fis = new FileInputStream(testFile))
		{
			return encode(digest(fis, SHA1));
		}
	}


	public static String sha1(InputStream is) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		return encode(digest(is, SHA1));
	}


	public static String digest(File testFile, String algorithm, int encoding) throws IOException, NoSuchAlgorithmException
	{
		try (FileInputStream fis = new FileInputStream(testFile))
		{
			return digest(fis, algorithm, encoding);
		}
	}


	public static byte[] digest(final InputStream is, final String algorithm) throws IOException, NoSuchAlgorithmException
	{
		MessageDigest md = MessageDigest.getInstance(algorithm);

		byte[] buffer = new byte[4096];
		int readSize = 0;
		while (readSize >= 0)
		{
			readSize = is.read(buffer);

			if (readSize >= 0)
			{
				md.update(buffer, 0, readSize);
			}
		}

		// Finish the hash then convert it to a hex string
		return md.digest();
	}


	public static String digest(final InputStream is,
	                            final String algorithm,
	                            final int encoding) throws IOException, NoSuchAlgorithmException
	{
		byte[] digest = digest(is, algorithm);

		return encode(digest);
	}


	public static long crc32(File testFile) throws FileNotFoundException, IOException
	{
		CheckedInputStream inputStream = new CheckedInputStream(new FileInputStream(testFile), new CRC32());
		try
		{
			// Read 4k at a time and discard the incoming data
			byte[] buffer = new byte[4096];
			while (inputStream.read(buffer) >= 0)
			{
				// Ignore input
			}

			return inputStream.getChecksum().getValue();
		}
		finally
		{
			IOUtils.closeQuietly(inputStream);
		}
	}


	public static long crc32(InputStream is) throws IOException
	{
		CRC32 crc = new CRC32();

		byte[] buffer = new byte[4096];
		int read = 0;
		while ((read = is.read(buffer)) >= 0)
		{
			crc.update(buffer, 0, read);
		}

		return crc.getValue();
	}


	/**
	 * Compute the hexadecimal MD5 of a byte array
	 *
	 * @param plaintext
	 * @return
	 */
	public static String md5(final byte[] plaintext)
	{
		return digest(plaintext, MD5);
	}


	/**
	 * Compute the digest of a byte array
	 *
	 * @param plaintext
	 * @param algorithm
	 * @return
	 * @throws IllegalArgumentException if the requested algorithm is not available
	 */
	public static String digest(final byte[] plaintext, final String algorithm)
	{
		try
		{
			return encode(MessageDigest.getInstance(algorithm).digest(plaintext));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalArgumentException("No such digest algorithm: " + algorithm, e);
		}
	}


	public static String md5(File testFile) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		try (FileInputStream fis = new FileInputStream(testFile))
		{
			return encode(digest(fis, MD5));
		}
	}


	private static String encode(final byte[] in)
	{
		return HexHelper.toHex(in);
	}
}
