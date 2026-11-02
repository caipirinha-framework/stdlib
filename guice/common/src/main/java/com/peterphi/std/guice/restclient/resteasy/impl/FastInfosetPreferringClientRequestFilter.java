package com.peterphi.std.guice.restclient.resteasy.impl;

import org.apache.commons.lang.StringUtils;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

/**
 * A self-registering client filter that (if the fast infoset plugin is available) always prefers fastinfoset responses over
 * requests application/xml
 */
public class FastInfosetPreferringClientRequestFilter implements ClientRequestFilter
{
	private static boolean REGISTERED = false;


	public static void register()
	{
		if (!REGISTERED)
		{
			if (FastInfosetPreferringClientRequestFilter.class.getResource(
					"/META-INF/maven/org.jboss.resteasy/resteasy-fastinfoset-provider/pom.properties") != null)
			{
				ResteasyProviderFactory.getInstance().registerProviderInstance(new FastInfosetPreferringClientRequestFilter());
			}

			REGISTERED = true;
		}
	}


	@Override
	public void filter(final ClientRequestContext ctx) throws IOException
	{
		if (StringUtils.equals(ctx.getHeaderString("Accept"), "application/xml"))
		{
			ctx.getHeaders().putSingle("Accept", "application/fastinfoset, application/xml");
		}
	}
}
