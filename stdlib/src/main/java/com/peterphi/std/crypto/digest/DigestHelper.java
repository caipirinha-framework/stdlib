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
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * Where possible implementations should use DigestUtils from commons-codec
 */
public class DigestHelper
{
	public static final int ENCODE_BASE64 = 1;
	public static final int ENCODE_HEX = 2;

	public static final String SHA1 = "SHA1";
	public static final String MD5 = "MD5";


	/**
	 * Performs HMAC-SHA1 on the UTF-8 byte representation of strings
	 *
	 * @param key
	 * @param plaintext
	 *
	 * @return
	 */
	public static String sha1hmac(String key, String plaintext)
	{
		return sha1hmac(key, plaintext, ENCODE_HEX);
	}


	/**
	 * Performs HMAC-SHA1 on the UTF-8 byte representation of strings, returning the hexidecimal hash as a result
	 *
	 * @param key
	 * @param plaintext
	 *
	 * @return
	 */
	public static String sha1hmac(String key, String plaintext, int encoding)
	{
		byte[] signature = sha1hmac(key.getBytes(), plaintext.getBytes());

		return encode(signature, encoding);
	}


	/**
	 * @param key
	 * @param text
	 *
	 * @return
	 *
	 * @throws IllegalArgumentException
	 */
	public static byte[] sha1hmac(byte[] key, byte[] text) throws IllegalArgumentException
	{
		try
		{
			SecretKey sk = new SecretKeySpec(key, "HMACSHA1");
			Mac m = Mac.getInstance(sk.getAlgorithm());
			m.init(sk);
			return m.doFinal(text);
		}
		catch (InvalidKeyException e)
		{
			throw new IllegalArgumentException(e);
		}
		catch (NoSuchAlgorithmException e)
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
		return sha1(plaintext, ENCODE_HEX);
	}


	public static String sha1(String plaintext, int encoding) throws IOException, NoSuchAlgorithmException
	{
		ByteArrayInputStream is = new ByteArrayInputStream(plaintext.getBytes());
		try
		{
			return sha1(is, encoding);
		}
		finally
		{
			is.close();
		}
	}


	public static String sha1(File testFile) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		return sha1(testFile, ENCODE_HEX);
	}


	public static String sha1(File testFile, int encoding) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		return digest(testFile, SHA1, encoding);
	}


	public static String sha1(InputStream is, int encoding) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		return digest(is, SHA1, encoding);
	}


	public static String digest(File testFile, String algorithm, int encoding) throws IOException, NoSuchAlgorithmException
	{
		FileInputStream fis = new FileInputStream(testFile);

		try
		{
			return digest(fis, algorithm, encoding);
		}
		finally
		{
			fis.close();
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

		return encode(digest, encoding);
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


	public static String md5(File testFile) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		return md5(testFile, ENCODE_HEX);
	}


	/**
	 * Compute the hexadecimal MD5 of a byte array
	 *
	 * @param plaintext
	 *
	 * @return
	 */
	public static String md5(final byte[] plaintext)
	{
		return digest(plaintext, MD5, ENCODE_HEX);
	}


	/**
	 * Compute the digest of a byte array
	 *
	 * @param plaintext
	 * @param algorithm
	 * @param encoding
	 *
	 * @return
	 *
	 * @throws IllegalArgumentException
	 * 		if the requested algorithm is not available
	 */
	public static String digest(final byte[] plaintext, final String algorithm, final int encoding)
	{
		try
		{
			return encode(MessageDigest.getInstance(algorithm).digest(plaintext), encoding);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalArgumentException("No such digest algorithm: " + algorithm, e);
		}
	}


	public static String md5(File testFile, int encoding) throws FileNotFoundException, IOException, NoSuchAlgorithmException
	{
		return digest(testFile, MD5, encoding);
	}


	private static String encode(final byte[] in, final int method)
	{
		switch (method)
		{
			case ENCODE_HEX:
				return HexHelper.toHex(in);
			case ENCODE_BASE64:
				return Base64.getEncoder().encodeToString(in);
			default:
				throw new IllegalArgumentException("Unsupported encoding method!");
		}
	}
}
