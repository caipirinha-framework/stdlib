package com.peterphi.usermanager.guice.authentication;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.peterphi.std.guice.common.auth.annotations.AuthConstraint;
import com.peterphi.std.guice.common.serviceprops.annotations.Reconfigurable;
import com.peterphi.usermanager.db.entity.UserEntity;
import com.peterphi.usermanager.guice.UMConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ImpersonationService
{
	private static final Logger log = LoggerFactory.getLogger(ImpersonationService.class);

	@Inject
	Provider<UserLogin> userProvider;
	@Inject
	UserAuthenticationService authenticationService;

	@Inject(optional = true)
	@Named(UMConfig.IMPERSONATION_PERMITTED)
	@Reconfigurable
	public boolean enabled = true;

	@AuthConstraint(id = "impersonation", role = UserLogin.ROLE_ADMIN, comment = "only admins can impersonate other users")
	public void impersonate(final int userId)
	{
		if (!enabled)
			throw new IllegalArgumentException(
					"Impersonation has been disabled on this installation. Please reconfigure and restart before continuing.");

		final UserLogin currentUser = userProvider.get();
		final UserEntity newUser = authenticationService.getById(userId);

		log.info("Admin user {} ({}) is changing their session to impersonate user {} ({})",
		         currentUser.getId(),
		         currentUser.getEmail(),
		         newUser.getId(),
		         newUser.getEmail());

		currentUser.reload(newUser);
	}
}
