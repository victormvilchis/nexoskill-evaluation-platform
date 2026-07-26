package com.nexoskill.evaluation.users.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nexoskill.evaluation.audit.application.port.AuditLogPort;
import com.nexoskill.evaluation.shared.domain.BusinessException;
import com.nexoskill.evaluation.users.application.model.AdminUserSummary;
import com.nexoskill.evaluation.users.application.model.UpdateUserRoleCommand;
import com.nexoskill.evaluation.users.application.port.out.UserManagementPort;
import com.nexoskill.evaluation.users.application.port.out.UserSessionPort;
import com.nexoskill.evaluation.users.domain.model.UserAccessStatus;
import com.nexoskill.evaluation.users.domain.model.UserStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UpdateUserRoleServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-24T20:00:00Z");

	@Test
	void shouldPreventDemotingTheLastEffectiveAdministrator() {
		UserManagementPort userManagementPort = mock(UserManagementPort.class);
		UserSessionPort userSessionPort = mock(UserSessionPort.class);
		AuditLogPort auditLogPort = mock(AuditLogPort.class);
		UpdateUserRoleService service = new UpdateUserRoleService(userManagementPort, userSessionPort, auditLogPort,
				Clock.fixed(NOW, ZoneOffset.UTC));
		AdminUserSummary summary = new AdminUserSummary("admin-public-id", "admin@nexoskill.local", "Administrador",
				"NexoSkill", "Administrador NexoSkill", UserStatus.ACTIVE, Set.of("ADMINISTRATOR"), null, null,
				UserAccessStatus.ACTIVE, NOW.minusSeconds(60), null, null, NOW.minusSeconds(3600),
				NOW.minusSeconds(300), "Sistema", "Creación de usuario");
		when(userManagementPort.getByPublicId("admin-public-id"))
				.thenReturn(new UserManagementPort.ManagedUser(10L, summary));
		when(userManagementPort.countEffectiveAdministrators(NOW)).thenReturn(1L);

		assertThatThrownBy(
				() -> service.update(new UpdateUserRoleCommand("admin-public-id", "USER", 20L, "127.0.0.1", "JUnit")))
				.isInstanceOf(BusinessException.class)
				.hasMessage("No puedes quitar el rol al último administrador activo de la plataforma.");

		verifyNoInteractions(userSessionPort, auditLogPort);
	}
}
