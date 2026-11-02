package com.peterphi.usermanager.rest.iface.oauth2server;

import com.peterphi.std.annotation.Doc;
import com.peterphi.std.guice.restclient.annotations.NoClientBreaker;
import com.peterphi.usermanager.rest.type.UserManagerUser;
import org.jboss.resteasy.util.HttpHeaderNames;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@NoClientBreaker
@Path("/oauth2")
@Doc("Implements the OAuth2 flow")
public interface UserManagerOAuthService
{
	String GRANT_TYPE_AUTHORIZATION_CODE="authorization_code";
	String GRANT_TYPE_REFRESH_TOKEN="refresh_token";
	String GRANT_TYPE_TOKEN_EXCHANGE="urn:ietf:params:oauth:grant-type:token-exchange";
	String GRANT_TYPE_PASSWORD = "password";
	String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";


	@GET
	@Path("/authorize")
	Response getAuth(@QueryParam("response_type") @Doc("The response type - N.B. only code supported") String responseType,
	                 @QueryParam("client_id") @Doc("Client identifier") final String clientId,
	                 @QueryParam("redirect_uri") @Doc("client endpoint to return to") String redirectUri,
	                 @QueryParam("state") @Doc("optional state to return to client at end of flow") String state,
	                 @QueryParam("scope") @Doc("(optional) User data the client is requesting") String scope);


	@POST
	@Path("/authorize")
	Response userMadeAuthDecision(@FormParam("response_type") @Doc("Original response_type") String responseType,
	                              @FormParam("client_id") @Doc("Original client_id") final String clientId,
	                              @FormParam("redirect_uri") @Doc("Original redirect_uri") String redirectUri,
	                              @FormParam("state") @Doc("Original state") String state,
	                              @FormParam("scope") @Doc("Original scope") String scope,
	                              @FormParam("token") @Doc("For CSRF prevention") String token,
	                              @FormParam("decision") @Doc("decision (Allow/Deny)") String decision);

	/**
	 * Exchange an access code, a refresh token or a username/password for a Token to be POSTed back<br /> N.B. currently this
	 * impl is not fully compliant with RFC6749 because it cannot accept <code>clientId</code> and <code>secret</code> using BASIC
	 * Auth.<br />
	 *
	 * One approach here might be to add logic into the BASIC auth processor to treat as anonymous any BASIC auth request whose username is in the client ID format
	 *
	 * @param grantType
	 * @param code
	 * @param redirectUri
	 * @param clientId
	 * @param secret
	 * @param refreshToken
	 * @param username
	 * @param password
	 * @return
	 */
	@POST
	@Doc(value = "Exchange an access code (or a refresh token, or a username+password) for a Token to be provided to the standard OpenID Connect JSON /userinfo resource or non-standard structured /token-to-user-info method.", href = "https://tools.ietf.org/html/rfc6749#section-3.2")
	@Path("/token")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces({MediaType.APPLICATION_JSON})
	String getToken(@FormParam("grant_type")
	                @Doc("One of: authorization_code,refresh_token,urn:ietf:params:oauth:grant-type:token-exchange,password,client_credentials")
			                String grantType,
	                @FormParam("code")
	                @Doc("The authorization code from the auth callback (where grant_type is authorization_code)") String code,
	                @FormParam("redirect_uri") @Doc("") String redirectUri,
	                @FormParam("client_id") String clientId,
	                @FormParam("client_secret") String secret,
	                @FormParam("refresh_token") @Doc("The refresh token (where grant_type is refresh_token)") String refreshToken,
	                @FormParam("username") @Doc("The username (where grant_type is password)") String username,
	                @FormParam("password") @Doc("The password (where grant_type is password)") String password,
	                @FormParam("subject_token")
	                @Doc("The subject_token (where grant_type is urn:ietf:params:oauth:grant-type:token-exchange)")
			                String subjectToken,
	                @HeaderParam(HttpHeaderNames.AUTHORIZATION)
	                @Doc(value = "If providing client id and secret as BASIC auth rather than form params", href = "https://tools.ietf.org/html/rfc6749#section-2.3.1")
	                final String basicAuthHeader);


	/**
	 * User Manager extension: given user info, return user record
	 *
	 * @param token
	 * @param clientId if specified, checks that the token is associated with this client (may be required in the future)
	 * @return
	 */
	@POST
	@Doc("Non-standard userinfo implementation that returns structured user info as part of a well-defined XML schema")
	@Path("/token-to-user-info")
	@Produces(MediaType.APPLICATION_XML)
	UserManagerUser get(@FormParam("token") String token,
	                    @FormParam("client_id")
	                    @Doc("Optional, if provided, server will ensure that it only returns if the token is associated with the given client id")
			                    String clientId);


	@GET
	@Path("/userinfo")
	@Doc(value = "OpenID Connect Compatible UserInfo endpoint", href = "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo")
	@Produces(MediaType.APPLICATION_JSON)
	Response getOIDCUserInfo(@HeaderParam(HttpHeaderNames.AUTHORIZATION)
	                         @Doc("Expected header is 'Authorization: Bearer [token from /token resource]'; if omitted then will return 'WWW-Authenticate: Bearer'")
			                         String bearerTokenHeader);

	@POST
	@Path("/userinfo")
	@Doc(value = "OpenID Connect Compatible UserInfo endpoint", href = "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo")
	@Produces(MediaType.APPLICATION_JSON)
	Response getOIDCUserInfoPost(@HeaderParam(HttpHeaderNames.AUTHORIZATION)
	                             @Doc("Expected header is 'Authorization: Bearer [token from /token resource]'; if omitted then will return 'WWW-Authenticate: Bearer'")
			                             String bearerTokenHeader);

	@POST
	@Path("/token-to-delegated-token")
	@Doc("Non-standard method to exchange a Refresh Token for a short-lived Delegated Access Token granting the same access privileges as that user, and the access rights of the service. The client_id MUST be permitted to use the provided refresh_token")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.TEXT_PLAIN)
	String createDelegatedAccessToken(@FormParam("client_id") String clientId,
	                                  @FormParam("client_secret") String secret,
	                                  @FormParam("validity_period")
	                                  @Doc("The number of milliseconds the token should remain valid for") long validityPeriod,
	                                  @FormParam("refresh_token") @Doc("The refresh token") String refreshToken,
	                                  @HeaderParam(HttpHeaderNames.AUTHORIZATION)
	                                  @Doc(value = "If providing client id and secret as BASIC auth rather than form params", href = "https://tools.ietf.org/html/rfc6749#section-2.3.1")
	                                  final String basicAuthHeader);
}
