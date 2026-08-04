package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.authentication.application.port.out.PasswordHasher;
import com.nexoskill.evaluation.shared.infrastructure.config.AppProperties;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.CreateUserCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateUserServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-26T18:00:00Z");

	@Test
	void shouldAlwaysCreateInternalUsersAsActive() {
		UserManagementPort users = mock(UserManagementPort.class);
		PasswordHasher hasher = mock(PasswordHasher.class);
		SecureTemporaryPasswordGenerator generator = mock(SecureTemporaryPasswordGenerator.class);
		InternalUserStatusHistoryService history = mock(InternalUserStatusHistoryService.class);
		AuditLogPort audit = mock(AuditLogPort.class);
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

		when(generator.generate()).thenReturn("SafePass1!");
		when(hasher.encode("SafePass1!")).thenReturn("encoded");
		when(users.create(any())).thenReturn(summary());
		when(users.getByPublicId("user-public")).thenReturn(new UserManagementPort.ManagedUser(10L, summary()));

		CreateUserService service = new CreateUserService(users, hasher, new PasswordPolicy(), generator,
				new InternalRolePolicy(), history, audit, new AppProperties(), clock);

		service.create(new CreateUserCommand("manager@example.com", "María", "López", null, "MANAGER", "org-public",
				NOW, null, 1L, "127.0.0.1", "browser"));

		ArgumentCaptor<UserManagementPort.NewUserData> captor = ArgumentCaptor
				.forClass(UserManagementPort.NewUserData.class);
		verify(users).create(captor.capture());
		assertThat(captor.getValue().initialStatus()).isEqualTo(UserStatus.ACTIVE);
		verify(history).record(10L, 1L, null, UserStatus.ACTIVE, "Creación de usuario", NOW);
	}

	private static AdminUserSummary summary() {
		return new AdminUserSummary("user-public", "manager@example.com", "María", "López", "María López",
				UserStatus.ACTIVE, Set.of("MANAGER"), "org-public", "Acme", UserAccessStatus.ACTIVE, NOW, null, null,
				NOW, NOW, "Administrador", "Creación de usuario");
	}
}
