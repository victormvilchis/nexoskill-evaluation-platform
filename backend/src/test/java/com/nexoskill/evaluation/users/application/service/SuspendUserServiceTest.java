package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UserStatusCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SuspendUserServiceTest {
	private static final Instant NOW = Instant.parse("2026-07-24T20:00:00Z");

	@Test
	void shouldPreventAdministratorFromSuspendingOwnAccount() {
		UserManagementPort users = mock(UserManagementPort.class);
		UserSessionPort sessions = mock(UserSessionPort.class);
		InternalUserStatusHistoryService history = mock(InternalUserStatusHistoryService.class);
		AuditLogPort audit = mock(AuditLogPort.class);
		SuspendUserService service = new SuspendUserService(users, sessions, new InternalUserTransitionPolicy(),
				history, audit, Clock.fixed(NOW, ZoneOffset.UTC));

		when(users.getByPublicId("public-admin"))
				.thenReturn(new UserManagementPort.ManagedUser(10L, summary("public-admin")));

		assertThatThrownBy(
				() -> service.suspend(new UserStatusCommand("public-admin", "Seguridad", 10L, "127.0.0.1", "JUnit")))
				.isInstanceOf(BusinessException.class).hasMessage("No puedes suspender tu propia cuenta.");

		verifyNoInteractions(sessions, history, audit);
	}

	@Test
	void shouldPreventSuspendingTheLastEffectiveAdministrator() {
		UserManagementPort users = mock(UserManagementPort.class);
		UserSessionPort sessions = mock(UserSessionPort.class);
		InternalUserStatusHistoryService history = mock(InternalUserStatusHistoryService.class);
		AuditLogPort audit = mock(AuditLogPort.class);
		SuspendUserService service = new SuspendUserService(users, sessions, new InternalUserTransitionPolicy(),
				history, audit, Clock.fixed(NOW, ZoneOffset.UTC));

		when(users.getByPublicId("other-admin"))
				.thenReturn(new UserManagementPort.ManagedUser(20L, summary("other-admin")));
		when(users.countEffectiveAdministrators(NOW)).thenReturn(1L);

		assertThatThrownBy(
				() -> service.suspend(new UserStatusCommand("other-admin", "Seguridad", 10L, "127.0.0.1", "JUnit")))
				.isInstanceOf(BusinessException.class)
				.hasMessage("No puedes suspender al último administrador activo de la plataforma.");

		verifyNoInteractions(sessions, history, audit);
	}

	private AdminUserSummary summary(String id) {
		return new AdminUserSummary(id, id + "@nexoskill.local", "Administrador", "NexoSkill",
				"Administrador NexoSkill", UserStatus.ACTIVE, Set.of("ADMINISTRATOR"), null, null,
				UserAccessStatus.ACTIVE, NOW.minusSeconds(60), null, null, NOW.minusSeconds(3600),
				NOW.minusSeconds(300), "Sistema", "Creación de usuario");
	}
}
