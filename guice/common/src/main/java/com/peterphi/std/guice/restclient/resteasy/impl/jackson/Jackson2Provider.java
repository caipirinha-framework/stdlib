package com.peterphi.std.guice.restclient.resteasy.impl.jackson;


import com.fasterxml.jackson.jakarta.rs.cfg.Annotations;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.google.inject.Inject;
import com.peterphi.std.guice.common.jackson.JacksonFactory;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.core.messagebody.AsyncBufferedMessageBodyWriter;


@Provider
@Consumes({"application/json", "application/*+json", "text/json"})
@Produces({"application/json", "application/*+json", "text/json"})
public class Jackson2Provider extends JacksonJsonProvider implements AsyncBufferedMessageBodyWriter<Object>
{
	@Inject
	public Jackson2Provider(JacksonFactory factory)
	{
		super(factory.getObjectMapper(), new Annotations[]{Annotations.JACKSON});
	}
}
