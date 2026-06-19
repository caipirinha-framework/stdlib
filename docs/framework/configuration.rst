Framework Configuration
=======================

This documentation is written with the assumption that you'll be deploying webapps to a multi-domain tomcat system, see https://github.com/caipirinha-framework/tomcat-control-script/wiki for more detail

Configuration File Layout
-------------------------

It is assumed that your configuration files are laid out as follows:

 - /opt/tomcat/appname/lib/environment.properties - config specific to the "appname" domain; usually the endpoint
 - /opt/tomcat/shared/lib/environment.properties - master config shared by all applications under /opt/tomcat
 - /opt/tomcat/shared/lib/services/(webapp).properties


Example appname/lib/environment.properties
------------------------------------------

We generally put a reverse proxy in front of the tomcat domains, flattening their multi-port layout and making it easier to dynamically deploy services with no user impact (or to apply more sophisticated rules to incoming client requests), as well as to have a single central point for logging and metrics to do with HTTP requests.
As such, the environment.properties is typically very similar for each domain:

.. code-block:: java
	
	local.container.endpoint=https://example.com
	tomcat.domain=appname

Example shared/lib/environment.properties
-----------------------------------------

This configuration is shared across all applications; it is loaded after "appname/lib/environment.properties".

.. code-block:: java
	
	mode=DEVELOPMENT
	
	restutils.show-serviceprops=true
	restutils.allow-reconfigure=true
	restutils.show-bound-values=true
	
	metrics.label=domain="${tomcat.domain}"
	
	service.user-manager.endpoint=https://example.com/user-manager

	service.some-service.endpoint=https://example.com/some-service
	service.some-service.delegation=true
	service.some-service.bearer=${service.oauth2.own_api_key}


Example shared/lib/services/user-manager.properties
-----------------------------------------------

This configuration is specific to one service, deployed as `(webapp).war` (or if parallel deployment is enabled, `(webapp)##1.war` etc.)

.. code-block:: java
	
	hibernate.properties=hibernate/user-manager.properties
	framework.webauth.enabled=true

	# Don't allow users to create accounts for themselves
	authentication.allowAnonymousRegistration=false

	# Enable Active Directory as an auth backend
	# This will be checked after authentication is tested against locally registered users
	authentication-backend=ldap

	ldap.endpoint=ldap://active-directory-server:389
	ldap.domain=somedomain.dom
	ldap.filter=(&(objectClass=user)(sAMAccountName=%s))
	ldap.search-base=dc=somedomain,dc=dom
	ldap.group-regex.find=(?i)^cn=([^,]+),.*$
	ldap.group-regex.replace=$1

	# Allow LDAP roles to be linked to special internal User Manager roles
	ldap.group.user-manager-admin=UserManager-UserManagerAdmin
	ldap.group.admin=UserManager-Admin
	ldap.group.framework-admin=UserManager-FrameworkAdmin
	ldap.group.framework-info=UserManager-FrameworkInfo

	# Update LDAP user information periodically in the background
	ldap.background-role-updates=true
	ldap.user-manager-account.username=some-username
	ldap.user-manager-account.password=some-password

	# If true, logins created using LDAP credentials will be allowed to use the Session Reconnect Key system to stay logged in longer without reauthenticating
	# N.B. if enabled, ldap.background-role-updates should be enabled so that we can capture group changes from LDAP
	auth.ldap.allow-session-reconnect=true


Example shared/lib/hibernate/(webapp).properties
------------------------------------------------

We keep hibernate configuration files in a separate folder, and they generally simply nominate which driver to use and the name of the JNDI resource to connect to:

.. code-block:: java
	
	hibernate.dialect=org.hibernate.dialect.SQLServer2008Dialect
	hibernate.connection.datasource=java:comp/env/jdbc/core-database
	hibernate.default_schema=user_manager

	hibernate.show_sql=false
	hibernate.current_session_context_class=thread
	hibernate.jdbc.use_streams_for_binary=false

	liquibase.action=UPDATE

