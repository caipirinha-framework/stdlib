package com.peterphi.std.guice.web.rest.auth.userprovider;

import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.consumer.InvalidJwtSignatureException;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JWTTest
{
	@Test
	public void encodeJWT() throws Exception
	{
		final String expected = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ4IjoieSJ9.J7n6L4WPK24Eyz5r9RZSYX0soR1o20_rIvNjcZBVh7s";
		final String actual = createJWT("secret", "{\"x\":\"y\"}");

		assertEquals(expected, actual);
	}


	@Test
	public void verifyJWT() throws Exception
	{
		final String expected = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ4IjoieSJ9.J7n6L4WPK24Eyz5r9RZSYX0soR1o20_rIvNjcZBVh7s";
		// Now verify that the JWT is valid
		JWTVerifier verifier = new JWTVerifier("secret", null, null);

		verifier.verify(expected);
	}


	/**
	 * Should throw RuntimeException wrapping {@link InvalidJwtSignatureException}
	 *
	 * @throws Exception
	 */
	@Test
	public void verifyInvalidJWTSignatureFails() throws InvalidJwtSignatureException
	{
		final String expected = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ4IjoieSJ9.J7n6L4WPK24Eyz5r9RZSYX0soR1o20_rIvNjcZBVh7r";
		// Now verify that the JWT is valid
		JWTVerifier verifier = new JWTVerifier("secret", null, null);

		assertThrows(RuntimeException.class, () -> verifier.verify(expected));
	}


	private static String createJWT(final String secret, final String payload) throws JoseException
	{
		String token;
		JsonWebSignature sig = new JsonWebSignature();
		sig.setKey(new HmacKey(secret.getBytes(StandardCharsets.UTF_8)));
		sig.setDoKeyValidation(false);
		sig.setPayload(payload);
		sig.setAlgorithmHeaderValue(AlgorithmIdentifiers.HMAC_SHA256);
		sig.setHeader(HeaderParameterNames.TYPE, "JWT");

		token = sig.getCompactSerialization();
		return token;
	}
}
