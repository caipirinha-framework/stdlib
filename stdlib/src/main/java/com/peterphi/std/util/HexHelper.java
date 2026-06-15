package com.peterphi.std.util;

import java.security.SecureRandom;
import java.util.Random;
import java.util.StringTokenizer;

/**
 * A helper class which handles encoding and decoding hexidecimal
 */
public class HexHelper
{
	private HexHelper()
	{
	}


	private static final char[] hex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	/**
	 * Decodes a hexidecimal string into a series of bytes
	 *
	 * @param value
	 *
	 * @return
	 */
	public static final byte[] fromHex(final String value)
	{
		if (value.length() == 0)
			return new byte[0];
		else if (value.indexOf(':') != -1)
			return fromHex(':', value);
		else if (value.length() % 2 != 0)
			throw new IllegalArgumentException("Invalid hex specified: uneven number of digits passed for byte[] conversion");

		final byte[] buffer = new byte[value.length() / 2];

		// i tracks input position, j tracks output position
		int j = 0;
		for (int i = 0; i < buffer.length; i++)
		{
			buffer[i] = (byte) Integer.parseInt(value.substring(j, j + 2), 16);
			j += 2;
		}

		return buffer;
	}

	/**
	 * Decodes a hexidecimal string (optionally with <code>separator</code> characters separating each two-digit pair)<br />
	 * Multiple runs of the separator will be ignored (eg. AA::BB will result in <code>[0xAA,0xBB]</code>, not
	 * <code>[0xAA,0x00,0xBB]</code>)<br />
	 *
	 * @param separator
	 * 		the separator character
	 * @param value
	 *
	 * @return
	 */
	public static final byte[] fromHex(final char separator, final String value)
	{
		if (value.length() == 0)
			return new byte[0];

		final String sepString = new String(new char[]{separator});

		final StringTokenizer t = new StringTokenizer(value, sepString, false);

		final byte[] buffer = new byte[t.countTokens()];

		int i = 0;
		while (t.hasMoreTokens())
		{
			final String hex = t.nextToken();

			if (hex.length() == 2)
				buffer[i++] = (byte) Integer.parseInt(hex, 16);
			else
				throw new IllegalArgumentException("Hex section of length " +
				                                   hex.length() +
				                                   " encountered inside hex string: " +
				                                   value +
				                                   " with separator " +
				                                   separator);
		}

		return buffer;
	}

	/**
	 * Generates a number of random bytes which can then be manipulated and/or converted to hex<br />
	 * Uses a shared cryptographically strong random number generator (java.security.SecureRandom)
	 *
	 * @param bytes
	 *
	 * @return
	 */
	public static final byte[] generateBytes(final int bytes)
	{
		return generateBytes(SECURE_RANDOM, bytes);
	}

	/**
	 * Generates a number of random bytes which can then be manipulated and/or converted to hex<br />
	 *
	 * @param RANDOM
	 * 		the random number generator to use
	 * @param bytes
	 *
	 * @return
	 */
	public static final byte[] generateBytes(final Random rand, final int bytes)
	{
		if (bytes < 1)
			throw new IllegalArgumentException("bytes must be >= 1");

		final byte[] buffer = new byte[bytes];
		rand.nextBytes(buffer);
		return buffer;
	}

	/**
	 * Generates a hexidecimal String of length <code>characters</code>
	 *
	 * @param characters
	 * 		the number of characters in the resulting String
	 *
	 * @return
	 */
	public static final String generateHex(final int characters)
	{
		return generateHex(SECURE_RANDOM, characters);
	}

	/**
	 * Generates a hexidecimal String of length <code>characters</code>
	 *
	 * @param random
	 * 		the random number generator to use
	 * @param characters
	 * 		the number of characters in the resulting String
	 *
	 * @return
	 */
	public static final String generateHex(final Random random, final int characters)
	{
		if (characters < 1)
			throw new IllegalArgumentException("characters must be >= 1");

		final char[] str = new char[characters];

		for (int i = 0; i < characters; i++)
		{
			str[i] = hex[random.nextInt(16)];
		}

		return new String(str);
	}

	/**
	 * Encodes a series of bytes into a hexidecimal string (potentially with leading zeroes) with no separators between each
	 * source byte
	 *
	 * @param bin
	 *
	 * @return
	 */
	public static final String toHex(final byte... bin)
	{
		if (bin == null || bin.length == 0)
			return "";

		final char[] buffer = new char[bin.length * 2];

		// i tracks input position, j tracks output position
		for (int i = 0, j = 0; i < bin.length; i++)
		{
			final byte b = bin[i];

			buffer[j++] = hex[(b >> 4) & 0x0F];
			buffer[j++] = hex[b & 0x0F];
		}

		return new String(buffer);
	}

	/**
	 * Encodes a series of bytes into a hexidecimal string with each source byte (represented in the output as a 2 digit
	 * hexidecimal pair) separated by <code>separator</code><br />
	 *
	 * @param separator
	 * 		The character to insert between each byte (for example, <code>':'</code>)
	 * @param bin
	 * 		the series of bytes to encode
	 *
	 * @return a hexidecimal string with each source byte (represented in the output as a 2 digit hexidecimal pair) separated by
	 * <code>separator</code>
	 */
	public static final String toHex(final char separator, final byte... bin)
	{
		if (bin == null || bin.length == 0)
			return "";

		char[] buffer = new char[(bin.length * 3) - 1];
		int end = bin.length - 1;
		int base = 0; // Store the index of buffer we're inserting into
		for (int i = 0; i < bin.length; i++)
		{
			byte b = bin[i];

			buffer[base++] = hex[(b >> 4) & 0x0F];
			buffer[base++] = hex[b & 0x0F];
			if (i != end)
				buffer[base++] = separator;
		}

		return new String(buffer);
	}
}
