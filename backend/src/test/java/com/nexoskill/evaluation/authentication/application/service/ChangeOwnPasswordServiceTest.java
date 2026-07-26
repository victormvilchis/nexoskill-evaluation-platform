package com.nexoskill.evaluation.authentication.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.model.ChangePasswordCommand;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.application.port.out.PasswordHistoryPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.application.service.PasswordPolicy;
import com.nexoskill.evaluation.users.domain.model.RoleGrant;
import com.nexoskill.evaluation.users.domain.model.UserAccess;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserAccount;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import com.nexoskill.evaluation.users.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChangeOwnPasswordServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");

	@Test
	void completesMandatoryPasswordChangeAndRevokesEveryPreviousSession() {
		UserRepository users = mock(UserRepository.class);
		PasswordHasher hasher = mock(PasswordHasher.class);
		PasswordHistoryPort history = mock(PasswordHistoryPort.class);
		UserSessionPort sessions = mock(UserSessionPort.class);
		AuditLogPort audit = mock(AuditLogPort.class);
		AppProperties properties = new AppProperties();
		UserAccount user = temporaryUser();

		when(users.findById(10L)).thenReturn(java.util.Optional.of(user));
		when(hasher.matches("Temp#12345", "old-hash")).thenReturn(true);
		when(hasher.matches("Final#67890", "old-hash")).thenReturn(false);
		when(history.recentHashes(10L, properties.getSecurity().getPasswordHistorySize())).thenReturn(List.of());
		when(hasher.encode("Final#67890")).thenReturn("new-hash");
		when(users.save(user)).thenReturn(user);
		when(sessions.revokeActiveSessions(10L, NOW)).thenReturn(1);

		ChangeOwnPasswordService service = new ChangeOwnPasswordService(users, hasher, new PasswordPolicy(), history,
				sessions, audit, properties, Clock.fixed(NOW, ZoneOffset.UTC));
		service.change(new ChangePasswordCommand(10L, "Temp#12345", "Final#67890", "Final#67890", "restricted-session",
				"127.0.0.1", "JUnit"));

		assertThat(user.isPasswordChangeRequired()).isFalse();
		assertThat(user.getPasswordHash()).isEqualTo("new-hash");
		assertThat(user.getTemporaryPasswordExpiresAt()).isNull();
		verify(sessions).revokeActiveSessions(10L, NOW);
	}

	private UserAccount temporaryUser() {
		return new UserAccount(10L, "user-public", "manager@nexoskill.local", "manager@nexoskill.local", "old-hash",
				"Gestor", "Prueba", "Gestor Prueba", UserStatus.ACTIVE, 0, null, null, true, null,
				NOW.plusSeconds(3600), Set.of(new RoleGrant("MANAGER", Set.of("PASSWORD_CHANGE"))),
				new UserAccess(NOW.minusSeconds(60), null, UserAccessStatus.ACTIVE));
	}
}
