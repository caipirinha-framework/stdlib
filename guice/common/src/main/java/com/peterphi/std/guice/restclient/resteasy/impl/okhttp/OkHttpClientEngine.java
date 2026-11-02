package com.peterphi.std.guice.restclient.resteasy.impl.okhttp;

/*
 * Copyright (C) 2015 Thomas Broyer (t.broyer@ltgt.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http.HttpMethod;
import okio.Buffer;
import okio.BufferedSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;
import org.jboss.resteasy.client.jaxrs.internal.ClientResponse;
import org.jboss.resteasy.client.jaxrs.internal.FinalizedClientResponse;
import org.jboss.resteasy.util.CaseInsensitiveMap;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ClientHttpEngine} based on OkHttp.
 *
 * <p>Usage:
 *
 * <pre><code>
 * new ResteasyClientBuilder()
 *     .httpEngine(new OkHttpClientEngine(okHttpClient))
 *     .build()
 * </code></pre>
 *
 * @author Thomas Broyer <t.broyer@ltgt.net>
 */
public class OkHttpClientEngine implements ClientHttpEngine
{
	private static final Logger log = LoggerFactory.getLogger(OkHttpClientEngine.class);

	final OkHttpClient client;

	private SSLContext sslContext;


	public OkHttpClientEngine(OkHttpClient client)
	{
		this.client = client;
	}


	@Override
	public SSLContext getSslContext()
	{
		return sslContext;
	}


	public void setSslContext(SSLContext sslContext)
	{
		this.sslContext = sslContext;
	}


	@Override
	public HostnameVerifier getHostnameVerifier()
	{
		return client.hostnameVerifier();
	}


	@Override
	public jakarta.ws.rs.core.Response invoke(final Invocation invocation)
	{
		final long start = System.currentTimeMillis();

		try
		{
			final ClientInvocation request = (ClientInvocation) invocation;
			Request req = createRequest(request);
			Response response;
			try
			{
				response = client.newCall(req).execute();
			}
			catch (IOException e)
			{
				throw new ProcessingException("Unable to invoke request", e);
			}
			return createResponse(request, response);
		}
		finally
		{
			if (log.isTraceEnabled())
			{
				log.trace("OkHttp Request completed in: {} ms", System.currentTimeMillis() - start);
			}
		}
	}


	private Request createRequest(ClientInvocation request)
	{
		Request.Builder builder = new Request.Builder()
				.url(request.getUri().toString());


		final var body = createRequestBody(request);


		if (body == null && HttpMethod.requiresRequestBody(request.getMethod()))
			builder.method(request.getMethod(), RequestBody.create(new byte[0], null));
		else
			builder.method(request.getMethod(), body);

		for (Map.Entry<String, List<String>> header : request.getHeaders().asMap().entrySet())
		{
			String headerName = header.getKey();
			for (String headerValue : header.getValue())
			{
				builder.addHeader(headerName, headerValue);
			}
		}
		return builder.build();
	}


	private RequestBody createRequestBody(final ClientInvocation request)
	{
		if (request.getEntity() == null)
		{
			return null;
		}

		// NOTE: this will invoke WriterInterceptors which can possibly change the request,
		// so it must be done first, before reading any header.
		final Buffer buffer = new Buffer();
		try
		{
			request.writeRequestBody(buffer.outputStream());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		jakarta.ws.rs.core.MediaType mediaType = request.getHeaders().getMediaType();
		final MediaType contentType = (mediaType == null) ? null : MediaType.parse(mediaType.toString());

		return new RequestBody()
		{
			@Override
			public long contentLength()
			{
				return buffer.size();
			}


			@Override
			public MediaType contentType()
			{
				return contentType;
			}


			@Override
			public void writeTo(BufferedSink sink)
			{
				buffer.copyTo(sink.getBuffer(), 0, buffer.size());
			}
		};
	}


	private ClientResponse createResponse(ClientInvocation request, final Response response)
	{
		ClientResponse clientResponse = new FinalizedClientResponse(request.getClientConfiguration(), request.getTracingLogger())
		{
			private InputStream stream;


			@Override
			protected InputStream getInputStream()
			{
				if (stream == null)
				{
					try
					{
						stream = new ByteArrayInputStream(response.body().bytes());
					}
					catch (IOException t)
					{
						throw new RuntimeException("IOException while reading HTTP response body!", t);
					}
				}

				return stream;
			}


			@Override
			protected void setInputStream(InputStream is)
			{
				stream = is;
			}


			@Override
			public void releaseConnection() throws IOException
			{
				// Stream might have been entirely replaced, so we need to close it independently from response.body()
				Throwable primaryEx = null;
				try
				{
					if (stream != null)
					{
						stream.close();
					}
				}
				catch (Throwable t)
				{
					primaryEx = t;
					throw t;
				}
				finally
				{
					if (primaryEx != null)
					{
						try
						{
							response.body().close();
						}
						catch (Throwable suppressed)
						{
							primaryEx.addSuppressed(suppressed);
						}
					}
					else
					{
						response.body().close();
					}
				}
			}
		};

		clientResponse.setStatus(response.code());
		clientResponse.setHeaders(transformHeaders(response.headers()));

		return clientResponse;
	}


	private MultivaluedMap<String, String> transformHeaders(Headers headers)
	{
		MultivaluedMap<String, String> ret = new CaseInsensitiveMap<>();
		for (int i = 0, l = headers.size(); i < l; i++)
		{
			ret.add(headers.name(i), headers.value(i));
		}
		return ret;
	}


	@Override
	public void close()
	{
		// no-op
	}
}
