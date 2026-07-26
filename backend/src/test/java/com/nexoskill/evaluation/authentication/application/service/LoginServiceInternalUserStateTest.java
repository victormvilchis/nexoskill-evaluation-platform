package com.nexoskill.evaluation.authentication.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.model.LoginCommand;
import com.nexoskill.evaluation.authentication.application.port.out.LoginAttemptPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.authentication.application.port.out.SessionTokenGenerator;
import com.nexoskill.evaluation.authentication.application.port.out.TokenHasher;
import com.nexoskill.evaluation.authentication.domain.AuthenticationException;
import com.nexoskill.evaluation.authentication.domain.repository.AuthSessionRepository;
import com.nexoskill.evaluation.organizations.infrastructure.persistence.UserOrganizationMembershipRepository;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.domain.model.RoleGrant;
import com.nexoskill.evaluation.users.domain.model.UserAccess;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoginServiceInternalUserStateTest {
	private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");

	@Test
	void returnsInactiveMessageOnlyAfterThePasswordWasValidated() {
		Fixture fixture = fixture(UserStatus.INACTIVE, true);

		AuthenticationException exception = assertThrows(AuthenticationException.class,
				() -> fixture.service.login(command()));

		assertThat(exception.getCode()).isEqualTo("ACCOUNT_INACTIVE");
		assertThat(exception.getMessage()).isEqualTo("Tu cuenta se encuentra inactiva. Contacta a un administrador.");
	}

	@Test
	void doesNotRevealInactiveStateWhenThePasswordIsWrong() {
		Fixture fixture = fixture(UserStatus.INACTIVE, false);

		AuthenticationException exception = assertThrows(AuthenticationException.class,
				() -> fixture.service.login(command()));

		assertThat(exception.getCode()).isEqualTo("AUTHENTICATION_FAILED");
	}

	@Test
	void returnsSuspendedMessageForAValidPassword() {
		Fixture fixture = fixture(UserStatus.SUSPENDED, true);

		AuthenticationException exception = assertThrows(AuthenticationException.class,
				() -> fixture.service.login(command()));

		assertThat(exception.getCode()).isEqualTo("ACCOUNT_SUSPENDED");
		assertThat(exception.getMessage()).isEqualTo("Tu acceso se encuentra suspendido. Contacta a un administrador.");
	}

	private Fixture fixture(UserStatus status, boolean passwordMatches) {
		UserRepository users = mock(UserRepository.class);
		PasswordHasher passwords = mock(PasswordHasher.class);
		UserAccount account = new UserAccount(10L, "user-public", "internal@nexoskill.local",
				"internal@nexoskill.local", "encoded", "Usuario", "Interno", "Usuario Interno", status, 0, null, null,
				false, NOW.minusSeconds(100), null, Set.of(new RoleGrant("ADMINISTRATOR", Set.of("PASSWORD_CHANGE"))),
				new UserAccess(NOW.minusSeconds(60), null, UserAccessStatus.ACTIVE));
		when(users.findByNormalizedEmail("INTERNAL@NEXOSKILL.LOCAL")).thenReturn(Optional.of(account));
		when(passwords.matches("Password#1", "encoded")).thenReturn(passwordMatches);

		LoginService service = new LoginService(users, mock(AuthSessionRepository.class),
				mock(UserOrganizationMembershipRepository.class), passwords, mock(SessionTokenGenerator.class),
				mock(TokenHasher.class), mock(LoginAttemptPort.class), mock(AuditLogPort.class), new AppProperties(),
				Clock.fixed(NOW, ZoneOffset.UTC));
		return new Fixture(service);
	}

	private LoginCommand command() {
		return new LoginCommand("internal@nexoskill.local", "Password#1", "127.0.0.1", "JUnit");
	}

	private record Fixture(LoginService service) {
	}
}
