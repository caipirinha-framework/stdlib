package com.peterphi.std.types;

import java.security.SecureRandom;
import java.util.Random;

/**
 * A simple, untyped Id field; this is one step above having a String id<br />
 * It is recommended that users extend Id themselves rather than using this type (as a SimpleId adds no discernable information
 * about what the id is for)
 */
public final class SimpleId
{
	// 0-9a-z excluding vowels
	private final static String digits = "0123456789bcdfghjklmnpqrstvwxyz";

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();


	private SimpleId()
	{
	}
	/**
	 * Generates a random sequence of alphanumeric characters of length <code>length</code> using a shared cryptographically
	 * strong random number generator (<code>SecureRandom</code>)
	 *
	 * @param length
	 * 		the length of the string to generate (must be > 0)
	 *
	 * @return a new random alphanumeric String of length <code>length</code>
	 */
	public static String alphanumeric(int length)
	{
		return alphanumeric(SECURE_RANDOM, length);
	}


	/**
	 * Generates a random sequence of alphanumeric characters, prefixed by <code>prefix</code> such that the total string is of
	 * length <code>totalLength</code> using a shared cryptographically strong random number
	 * generator (<code>SecureRandom</code>)
	 *
	 * @param totalLength
	 * 		the length of the string to return (must be > the prefix length)
	 *
	 * @return a String of length <code>length</code>
	 */
	public static String alphanumeric(final String prefix, final int totalLength)
	{
		if (prefix.length() >= totalLength)
			throw new IllegalArgumentException("Cannot generate id of length " +
			                                   totalLength +
			                                   ", prefix " +
			                                   prefix +
			                                   " takes up that length!");

		return prefix + alphanumeric(totalLength - prefix.length());
	}


	/**
	 * Generates a random sequence of alphanumeric characters of length <code>length</code> using the provided random number
	 * generator
	 *
	 * @param random
	 * 		the random number generator to use (must not be null)
	 * @param length
	 * 		the length of the string to generate (must be > 0)
	 *
	 * @return a new random alphanumeric String of length <code>length</code>
	 */
	public static String alphanumeric(final Random random, final int length)
	{
		final char[] buffer = new char[length];

		for (int i = 0; i < length; i++)
		{
			final int rand = random.nextInt(digits.length());
			buffer[i] = digits.charAt(rand);
		}

		return new String(buffer);
	}
}
