package com.peterphi.std.guice.web.rest.auth.oauth2.rest.api;

import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.restclient.annotations.NoClientBreaker;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@NoClientBreaker
@Path("/oauth2/client")
public interface OAuth2ClientCallbackRestService
{
	/**
	 * The request is missing a required parameter, includes an invalid parameter value, includes a parameter more than once, or
	 * is otherwise malformed.
	 */
	String ERROR_INVALID_REQUEST = "invalid_request";
	/**
	 * The client is not authorized to request an authorization code using this method.
	 */
	String ERROR_UNAUTHORIZED_CLIENT = "unauthorized_client";
	/**
	 * The resource owner or authorization server denied the request.
	 */
	String ERROR_ACCESS_DENIED = "access_denied";
	/**
	 * The authorization server does not support obtaining an authorization code using this method.
	 */
	String ERROR_UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type";
	/**
	 * The requested scope is invalid, unknown, or malformed.
	 */
	String ERROR_INVALID_SCOPE = "invalid_scope";
	/**
	 * The authorization server encountered an unexpected condition that prevented it from fulfilling the request. (This error
	 * code is needed because a 500 Internal Server Error HTTP status code cannot be returned to the client via an HTTP
	 * redirect.)
	 */
	String ERROR_SERVER_ERROR = "server_error";
	/**
	 * The authorization server is currently unable to handle the request due to a temporary overloading or maintenance of the
	 * server.  (This error code is needed because a 503 Service Unavailable HTTP status code cannot be returned to the client via
	 * an HTTP redirect.)
	 */
	String ERROR_TEMPORARILY_UNAVAILABLE = "temporarily_unavailable";

	@GET
	@Path("/cb")
	@Doc("OAuth2 callback with authorisation code")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	Response callback(@QueryParam("code") String code,
	                  @QueryParam("state") String returnTo,
	                  @QueryParam("error") String error,
	                  @QueryParam("error_description") String errorDescription,
	                  @QueryParam("error_uri") String errorUri);
}

